package com.lorevault.api.search.semantic;

import com.lorevault.api.search.extraction.ExtractionResult;
import com.lorevault.api.search.extraction.QueryEntityExtractor;
import com.lorevault.api.search.semantic.Neo4jSemanticSearch.SearchFilters;
import com.lorevault.api.search.semantic.Neo4jSemanticSearch.SearchResult;
import com.lorevault.api.search.model.CoreSearchRecords.CoreSemanticSearchRequest;
import com.lorevault.api.search.model.CoreSearchRecords.CoreSemanticSearchResponse;
import com.lorevault.api.search.model.CoreSearchRecords.CoreSearchResult;
import com.lorevault.api.search.model.CoreSearchRecords.CoreSearchMetadata;
import com.lorevault.api.search.model.CoreSearchRecords.CoreSemanticSearchFilters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Service for performing semantic search across chunk content.
 * Orchestrates query embedding generation, vector similarity search,
 * and entity-aware re-ranking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    private final EmbeddingModel embeddingModel;
    private final Neo4jSemanticSearch semanticSearch;
    private final QueryEntityExtractor entityExtractor;

    /**
     * Score bonus added per matched entity name found in a chunk's scene metadata.
     * Default 0.05 keeps re-ranking subtle; raise to 0.15+ for stronger entity preference.
     * Configurable via {@code lorevault.search.entity-boost-per-match}.
     */
    @Value("${lorevault.search.entity-boost-per-match:0.05}")
    private double entityBoostPerMatch;

    /**
     * Perform semantic search for the given query.
     *
     * @param request Search request with query and optional filters
     * @return Search results with metadata, re-ranked by entity overlap when applicable
     */
    public CoreSemanticSearchResponse search(CoreSemanticSearchRequest request) {
        log.debug("Performing semantic search for query: '{}' with topK: {}",
                 request.query(), request.topK());

        long startTime = System.currentTimeMillis();

        // Extract entity candidates from the query (runs before embedding — no extra latency on
        // the embedding call, and lets us log extracted entities for observability)
        ExtractionResult entities = entityExtractor.extract(request.query());
        if (!entities.isEmpty()) {
            log.debug("Entity extraction: known={}, discovered={}",
                    entities.knownEntities(), entities.unknownNounPhrases());
        }

        // Generate query embedding
        float[] queryEmbeddingRaw = embeddingModel.embed(request.query());
        double[] queryEmbedding = toDoubleArray(queryEmbeddingRaw);
        log.debug("Generated embedding vector of dimension: {}", queryEmbedding.length);

        // Convert filters
        SearchFilters searchFilters = convertFilters(request.filters());

        // Perform vector search
        List<SearchResult> searchResults = semanticSearch.search(
            queryEmbedding,
            request.topK(),
            searchFilters,
            request.visibility()
        );

        // Convert results
        List<CoreSearchResult> resultDtos = searchResults.stream()
            .map(this::convertSearchResult)
            .toList();

        // Re-rank by entity overlap (no-op when no entities extracted)
        List<CoreSearchResult> rankedDtos = rerankByEntityOverlap(resultDtos, entities);

        long processingTime = System.currentTimeMillis() - startTime;

        CoreSearchMetadata metadata = new CoreSearchMetadata(
            request.query(),
            searchResults.size(),
            rankedDtos.size(),
            processingTime
        );

        log.debug("Semantic search completed in {}ms, found {} results",
                 processingTime, searchResults.size());

        return new CoreSemanticSearchResponse(rankedDtos, metadata);
    }

    /**
     * Check if semantic search is currently available.
     *
     * @return true if search can be performed, false otherwise
     */
    public boolean isAvailable() {
        return semanticSearch.isAvailable();
    }

    // --- entity re-ranking ---

    /**
     * Re-ranks results by boosting scores for chunks whose scene metadata mentions
     * entities extracted from the query.
     *
     * <p>The boost is additive: {@code boostedScore = score + entityBoostPerMatch * matchCount}.
     * Results are then sorted descending by boosted score. The original vector similarity
     * scores remain available (they are not overwritten in the DTO).</p>
     *
     * @param results   DTOs from vector search, already in descending score order
     * @param entities  extracted entity candidates from the query
     * @return re-ordered list (may be identical to input if no entities or no overlap)
     */
    private List<CoreSearchResult> rerankByEntityOverlap(
            List<CoreSearchResult> results,
            ExtractionResult entities) {

        if (results.isEmpty() || entities.isEmpty()) {
            return results;
        }

        Set<String> candidates = entities.allCandidates(); // case-insensitive TreeSet

        // Compute boosted score for each result
        record Boosted(CoreSearchResult dto, double boostedScore) {}

        List<Boosted> boosted = results.stream()
                .map(dto -> {
                    long matchCount = countEntityMatches(dto, candidates);
                    double boost = matchCount * entityBoostPerMatch;
                    return new Boosted(dto, dto.score() + boost);
                })
                .toList();

        boolean anyBoosted = boosted.stream().anyMatch(b -> b.boostedScore() > b.dto().score());
        if (anyBoosted) {
            log.debug("Entity re-ranking applied: candidates={}, boost/match={}",
                    candidates, entityBoostPerMatch);
        }

        return boosted.stream()
                .sorted(Comparator.comparingDouble(Boosted::boostedScore).reversed())
                .map(Boosted::dto)
                .toList();
    }

    /**
     * Counts how many of the given entity candidates appear in a result's
     * {@code individualsPresent} or {@code locationsPresent} lists.
     */
    private long countEntityMatches(CoreSearchResult dto, Set<String> candidates) {
        long count = 0;

        List<String> individuals = dto.individualsPresent();
        if (individuals != null) {
            count += individuals.stream().filter(candidates::contains).count();
        }

        List<String> locations = dto.locationsPresent();
        if (locations != null) {
            count += locations.stream().filter(candidates::contains).count();
        }

        return count;
    }

    // --- helpers ---

    private SearchFilters convertFilters(CoreSemanticSearchFilters filters) {
        if (filters == null) {
            return SearchFilters.empty();
        }

        return new SearchFilters(
            filters.universe(),
            filters.series(),
            filters.bookNumber(),
            filters.chapterNumber()
        );
    }

    private CoreSearchResult convertSearchResult(SearchResult result) {
        return new CoreSearchResult(
            result.chunkId(),
            result.score(),
            result.snippet(),
            result.chapterId(),
            result.bookNumber(),
            result.chapterNumber(),
            result.sceneId(),
            result.sceneSummary(),
            result.individualsPresent(),
            result.locationsPresent()
        );
    }

    private double[] toDoubleArray(float[] vector) {
        if (vector == null) {
            return new double[0];
        }
        double[] out = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            out[i] = vector[i];
        }
        return out;
    }
}
