package com.lorevault.api.search.application;

import com.lorevault.api.content.entities.Chapter;
import com.lorevault.api.content.entities.Chunk;
import com.lorevault.api.library.domain.PublicationCoordinates;
import com.lorevault.api.search.application.CoreSearchRecords.CoreAskRequest;
import com.lorevault.api.search.application.CoreSearchRecords.CoreAskResponse;
import com.lorevault.api.search.application.CoreSearchRecords.CoreCitation;
import com.lorevault.api.search.application.CoreSearchRecords.CoreAskMetadata;
import com.lorevault.api.search.application.CoreSearchRecords.CoreAskFilters;
import com.lorevault.api.search.application.CoreSearchRecords.CoreSemanticSearchRequest;
import com.lorevault.api.search.application.CoreSearchRecords.CoreSemanticSearchResponse;
import com.lorevault.api.search.application.CoreSearchRecords.CoreSearchResult;
import com.lorevault.api.search.application.CoreSearchRecords.CoreSemanticSearchFilters;
import com.lorevault.api.search.domain.QuestionIntent;
import com.lorevault.api.search.domain.EntityLookupException;
import com.lorevault.api.search.domain.SemanticSearchException;
import com.lorevault.api.search.infrastructure.CypherTemplateRegistry;
import com.lorevault.api.content.entities.ChapterGraphRepository;
import com.lorevault.api.content.entities.ChunkGraphRepository;
import com.lorevault.api.ai.infrastructure.PromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * Service for RAG (Retrieve-Augment-Generate) question answering.
 *
 * <p>Routes each question through a two-lane strategy:
 * <ul>
 *   <li><b>ENTITY_LOOKUP</b> — parameterized Cypher templates via {@link CypherTemplateRegistry}.
 *       Used for "Who is X?", "Describe Y", "Where is Z?" questions.</li>
 *   <li><b>NARRATIVE_QA</b> — vector-seeded graph expansion via {@link SemanticSearchService}.
 *       Used for all other questions. Entity context (individuals, locations) is injected
 *       into the LLM context string from scene-level graph expansion.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagService {

    private final SemanticSearchService semanticSearchService;
    private final PromptRepository promptRepository;
    private final ChunkGraphRepository chunkRepo;
    private final ChapterGraphRepository chapterRepo;
    private final QuestionIntentClassifier intentClassifier;
    private final CypherTemplateRegistry templateRegistry;

    @Qualifier("nlpBig")
    private final ChatClient chatClient;

    @Value("${lorevault.ai.models.nlp-big.model:llama-3.1-70b-versatile}")
    private String modelId;

    @Value("${lorevault.search.hybrid.enabled:false}")
    private boolean hybridEnabled;

    @Value("${lorevault.search.hybrid.rag-only:true}")
    private boolean hybridRagOnly;

    @Value("${lorevault.search.hybrid.branch-n:20}")
    private int hybridBranchN;

    @Value("${lorevault.search.hybrid.rrf-k:60}")
    private int hybridRrfK;

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Answer a question using the routed RAG strategy.
     *
     * @param request Question and search parameters
     * @return Answer with source citations and metadata
     */
    public CoreAskResponse ask(CoreAskRequest request) {
        log.debug("RAG request: question='{}', topK={}", request.question(), request.topK());

        long startTime = System.currentTimeMillis();

        QuestionIntent intent = intentClassifier.classify(request.question());
        log.debug("Classified question intent: {}", intent);

        return switch (intent) {
            case ENTITY_LOOKUP -> handleEntityLookup(request, startTime);
            case NARRATIVE_QA, AMBIGUOUS -> handleNarrativeQa(request, startTime);
        };
    }

    /**
     * Vector-only RAG baseline (no graph-template lane, no hybrid fusion).
     */
    public CoreAskResponse askRagBaseline(CoreAskRequest request) {
        log.debug("RAG baseline request: question='{}', topK={}", request.question(), request.topK());
        return handleNarrativeQaVectorOnly(request, System.currentTimeMillis());
    }

    /**
     * Graph-aware routed QA path (entity template lane + narrative fallback).
     */
    public CoreAskResponse askGraphAware(CoreAskRequest request) {
        log.debug("Graph-aware RAG request: question='{}', topK={}", request.question(), request.topK());
        return ask(request);
    }

    /**
     * Explicit hybrid endpoint entrypoint for A/B comparison.
     * Always runs the hybrid narrative pipeline regardless of global hybrid toggle.
     */
    public CoreAskResponse askHybrid(CoreAskRequest request) {
        log.debug("Hybrid RAG request: question='{}', topK={}", request.question(), request.topK());
        return handleNarrativeQaHybrid(request, System.currentTimeMillis());
    }

    // -------------------------------------------------------------------------
    // Entity lookup lane (Pattern 1 — Cypher templates)
    // -------------------------------------------------------------------------

    /**
     * Handle entity-lookup questions by extracting the subject name from the question,
     * running the appropriate Cypher template, and synthesizing a prose answer via LLM.
     *
     * <p>Falls back to the narrative QA lane if no template results are found.
     */
    private CoreAskResponse handleEntityLookup(CoreAskRequest request, long startTime) {
        String question = request.question();
        String subject = extractSubjectName(question);

        if (subject == null || subject.isBlank()) {
            log.debug("Could not extract subject from entity-lookup question; falling back to narrative QA");
            return handleNarrativeQa(request, startTime);
        }

        String normalizedName = subject.toLowerCase().trim();
        log.debug("Entity lookup: subject='{}', normalizedName='{}'", subject, normalizedName);

        // Try individual lookup first, then location lookup
        List<CypherTemplateRegistry.EntityLookupResult> results =
                executeEntityLookupTemplate("individual-lookup",
                        Map.of("normalizedName", normalizedName),
                        request.visibility());

        if (results.isEmpty()) {
            results = executeEntityLookupTemplate("location-lookup",
                    Map.of("normalizedName", normalizedName),
                    request.visibility());
        }

        if (results.isEmpty()) {
            log.debug("No entity template results for '{}'; falling back to narrative QA", normalizedName);
            return handleNarrativeQa(request, startTime);
        }

        String context = buildEntityContext(results);
        String answer = generateAnswer(question, context);

        long processingTime = System.currentTimeMillis() - startTime;
        CoreAskMetadata metadata = new CoreAskMetadata(
                question, results.size(), results.size(), processingTime,
                modelId != null ? modelId : "unknown-model");

        return new CoreAskResponse(answer, List.of(), metadata);
    }

    /**
     * Extract the subject name from a question like "Who is Vin?" → "Vin".
     * Strips leading intent phrases and trailing punctuation.
     */
    private String extractSubjectName(String question) {
        if (question == null) return null;

        String q = question.trim();

        // Strip leading intent phrases (order matters — longer first)
        String[] prefixes = {
                "tell me about ", "describe ", "who was ", "who is ",
                "what was ", "what is ", "where was ", "where is "
        };
        String lower = q.toLowerCase();
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                q = q.substring(prefix.length()).trim();
                break;
            }
        }

        // Strip trailing punctuation
        q = q.replaceAll("[?!.,;:]+$", "").trim();
        return q.isEmpty() ? null : q;
    }

    private String buildEntityContext(List<CypherTemplateRegistry.EntityLookupResult> results) {
        StringBuilder sb = new StringBuilder();
        for (CypherTemplateRegistry.EntityLookupResult r : results) {
            if (r.displayName() != null) {
                sb.append("Name: ").append(r.displayName()).append("\n");
            }
            if (r.mentionCount() != null) {
                sb.append("Mention count: ").append(r.mentionCount()).append("\n");
            }
            if (r.firstSeenChapterId() != null) {
                sb.append("First seen chapter ID: ").append(r.firstSeenChapterId()).append("\n");
            }
            if (r.sceneSummary() != null) {
                sb.append("Scene: ").append(r.sceneSummary()).append("\n");
            }
            if (r.bookNumber() != null && r.chapterNumber() != null) {
                sb.append(String.format("Location: Book %d, Chapter %d%n",
                        r.bookNumber(), r.chapterNumber()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Narrative QA lane (Pattern 2 — vector-seeded graph expansion)
    // -------------------------------------------------------------------------

    private CoreAskResponse handleNarrativeQa(CoreAskRequest request, long startTime) {
        if (isHybridEnabledForRag()) {
            return handleNarrativeQaHybrid(request, startTime);
        }

        return handleNarrativeQaVectorOnly(request, startTime);
    }

    private CoreAskResponse handleNarrativeQaVectorOnly(CoreAskRequest request, long startTime) {

        // Step 1: Retrieve relevant chunks via semantic search
        CoreSemanticSearchRequest searchRequest = buildSearchRequest(request);
        CoreSemanticSearchResponse searchResponse;
        try {
            searchResponse = semanticSearchService.search(searchRequest);
        } catch (SemanticSearchException e) {
            log.warn("Narrative QA vector search backend failure: {}", e.getMessage());
            return buildSearchFailureResponse(request, startTime);
        }

        List<CoreSearchResult> searchResults = searchResponse.results();
        log.debug("Retrieved {} chunks for RAG", searchResults.size());

        // Step 2: Filter by threshold if specified
        List<CoreSearchResult> filteredResults = filterByThreshold(searchResults, request.threshold());
        log.debug("Using {} chunks after threshold filtering", filteredResults.size());

        // Step 3: Handle no evidence case
        if (filteredResults.isEmpty()) {
            return buildNoEvidenceResponse(request, searchResults.size(), startTime);
        }

        // Step 4: Generate answer using LLM
        String context = buildContextFromEvidence(filteredResults);
        String answer = generateAnswer(request.question(), context);

        // Step 5: Build citations from filtered results
        List<CoreCitation> citations = filteredResults.stream()
                .map(this::buildCitation)
                .toList();

        long processingTime = System.currentTimeMillis() - startTime;

        CoreAskMetadata metadata = new CoreAskMetadata(
                request.question(),
                searchResults.size(),
                filteredResults.size(),
                processingTime,
                modelId != null ? modelId : "unknown-model");

        log.debug("RAG completed in {}ms: answer length={}, citations={}",
                processingTime, answer.length(), citations.size());

        return new CoreAskResponse(answer, citations, metadata);
    }

    private CoreAskResponse handleNarrativeQaHybrid(CoreAskRequest request, long startTime) {
        CoreSemanticSearchRequest vectorSearchRequest = buildHybridVectorSearchRequest(request);

        record BranchOutcome(List<CoreSearchResult> results, boolean failed) {}

        CompletableFuture<BranchOutcome> vectorFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return new BranchOutcome(semanticSearchService.search(vectorSearchRequest).results(), false);
            } catch (Exception e) {
                log.warn("Hybrid vector branch failed: {}", e.getMessage());
                return new BranchOutcome(List.of(), true);
            }
        });

        CompletableFuture<BranchOutcome> graphFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return new BranchOutcome(runGraphBranch(request), false);
            } catch (Exception e) {
                log.warn("Hybrid graph branch failed: {}", e.getMessage());
                return new BranchOutcome(List.of(), true);
            }
        });

        BranchOutcome vectorOutcome = vectorFuture.join();
        BranchOutcome graphOutcome = graphFuture.join();
        boolean anyBranchFailed = vectorOutcome.failed() || graphOutcome.failed();

        if (vectorOutcome.failed() && graphOutcome.failed()) {
            return buildSearchFailureResponse(request, startTime);
        }

        List<CoreSearchResult> vectorResults = vectorOutcome.results();
        List<CoreSearchResult> graphResults = graphOutcome.results();

        List<CoreSearchResult> fusedResults = fuseByRrf(vectorResults, graphResults, request.topK());

        // Hybrid fused scores are rank-based and not compatible with cosine thresholds.
        List<CoreSearchResult> filteredResults = filterByThreshold(fusedResults, null);

        if (filteredResults.isEmpty()) {
            if (anyBranchFailed) {
                return buildSearchFailureResponse(request, startTime);
            }
            return buildNoEvidenceResponse(request, fusedResults.size(), startTime);
        }

        String context = buildContextFromEvidence(filteredResults);
        String answer = generateAnswer(request.question(), context);

        List<CoreCitation> citations = filteredResults.stream()
                .map(this::buildCitation)
                .toList();

        long processingTime = System.currentTimeMillis() - startTime;
        CoreAskMetadata metadata = new CoreAskMetadata(
                request.question(),
                fusedResults.size(),
                filteredResults.size(),
                processingTime,
                modelId != null ? modelId : "unknown-model");

        return new CoreAskResponse(answer, citations, metadata);
    }

    private CoreAskResponse buildSearchFailureResponse(CoreAskRequest request, long startTime) {
        String failureAnswer = "Search backend failures prevented retrieval for this question.";

        long processingTime = System.currentTimeMillis() - startTime;
        CoreAskMetadata metadata = new CoreAskMetadata(
                request.question(),
                0,
                0,
                processingTime,
                modelId != null ? modelId : "unknown-model");

        return new CoreAskResponse(failureAnswer, List.of(), metadata);
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private String generateAnswer(String question, String context) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(question, context);

        log.debug("Calling LLM for answer generation: model={}, context_length={}",
                modelId, context.length());

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

        if (answer == null || answer.trim().isEmpty()) {
            throw new RuntimeException("LLM returned empty response for question: " + question);
        }

        log.debug("LLM response length: {} chars", answer.length());
        return answer.trim();
    }

    private CoreSemanticSearchRequest buildSearchRequest(CoreAskRequest request) {
        CoreSemanticSearchFilters finalFilters = null;
        if (request.filters() != null) {
            finalFilters = validateAndConvertFilters(request.filters());
        }

        return new CoreSemanticSearchRequest(
            request.question(),
            request.topK(),
            null, // Handle threshold filtering in RAG service
            finalFilters,
            request.visibility()
        );
    }

    /**
     * Validates filter hierarchy and returns properly structured filters.
     * Enforces hierarchy: universe -> series -> book -> chapter
     */
    private CoreSemanticSearchFilters validateAndConvertFilters(CoreAskFilters askFilters) {
        String universe = askFilters.universe();
        String series = askFilters.series();
        Integer bookNumber = askFilters.bookNumber();
        Integer chapterNumber = askFilters.chapterNumber();

        if (chapterNumber != null && bookNumber == null) {
            log.warn("Chapter filter {} ignored: book number must be specified", chapterNumber);
            chapterNumber = null;
        }

        if (bookNumber != null && series == null) {
            log.warn("Book filter {} ignored: series must be specified", bookNumber);
            bookNumber = null;
            chapterNumber = null;
        }

        if (series != null && universe == null) {
            log.warn("Series filter '{}' ignored: universe must be specified", series);
            series = null;
            bookNumber = null;
            chapterNumber = null;
        }

        if (universe == null && series == null && bookNumber == null && chapterNumber == null) {
            return null;
        }

        CoreSemanticSearchFilters searchFilters = new CoreSemanticSearchFilters(
            universe,
            series,
            bookNumber,
            chapterNumber
        );

        log.debug("Applied validated filters: universe='{}', series='{}', book={}, chapter={}",
                universe, series, bookNumber, chapterNumber);

        return searchFilters;
    }

    private List<CoreSearchResult> filterByThreshold(List<CoreSearchResult> results, Double threshold) {
        if (threshold == null) {
            return results;
        }
        return results.stream()
                .filter(result -> result.score() >= threshold)
                .toList();
    }

    private CoreAskResponse buildNoEvidenceResponse(CoreAskRequest request, int chunksRetrieved, long startTime) {
        String noEvidenceAnswer = "No evidence found in the knowledge base to answer this question.";

        long processingTime = System.currentTimeMillis() - startTime;
        CoreAskMetadata metadata = new CoreAskMetadata(
                request.question(),
                chunksRetrieved,
                0,
                processingTime,
                modelId != null ? modelId : "unknown-model");

        return new CoreAskResponse(noEvidenceAnswer, List.of(), metadata);
    }

    private String buildContextFromEvidence(List<CoreSearchResult> evidence) {
        StringBuilder context = new StringBuilder();

        IntStream.range(0, evidence.size())
                .forEach(i -> {
                    CoreSearchResult result = evidence.get(i);

                    Optional<Chunk> chunkOpt = chunkRepo.findById(result.chunkId());
                    String chunkText = chunkOpt.map(Chunk::getText).orElse(result.snippet());

                    context.append(String.format("[%d] %s", i + 1, chunkText));

                    String locationTag = buildLocationTag(result);
                    if (!locationTag.isEmpty()) {
                        context.append(" ").append(locationTag);
                    }

                    context.append("\n\n");
                });

        return context.toString();
    }

    /**
     * Builds the parenthetical annotation that follows a chunk in the RAG context string.
     * Example output: "(Book 2, Chapter 5 — featuring: Vin, Kelsier — at: Luthadel)"
     * Parts are omitted when the corresponding data is absent.
     */
    private String buildLocationTag(CoreSearchResult result) {
        List<String> parts = new ArrayList<>();

        if (result.bookNumber() != null && result.chapterNumber() != null) {
            parts.add(String.format("Book %d, Chapter %d", result.bookNumber(), result.chapterNumber()));
        }

        List<String> individuals = result.individualsPresent();
        if (individuals != null && !individuals.isEmpty()) {
            parts.add("featuring: " + String.join(", ", individuals));
        }

        List<String> locations = result.locationsPresent();
        if (locations != null && !locations.isEmpty()) {
            parts.add("at: " + String.join(", ", locations));
        }

        if (parts.isEmpty()) {
            return "";
        }
        return "(" + String.join(" \u2014 ", parts) + ")";
    }

    private String buildSystemPrompt() {
        return promptRepository.get("rag-answer-generation").render();
    }

    private String buildUserPrompt(String question, String context) {
        return String.format("""
                Question: %s
                
                Context from the story:
                %s
                
                Please answer the question based on the provided context.
                """, question, context);
    }

    private CoreCitation buildCitation(CoreSearchResult searchResult) {
        if (searchResult.chapterId() == null) {
            PublicationCoordinates fallbackCoords = new PublicationCoordinates();
            fallbackCoords.setBookNumber(searchResult.bookNumber());
            fallbackCoords.setChapterNumber(searchResult.chapterNumber());

            return new CoreCitation(
                    searchResult.chunkId(),
                    searchResult.score(),
                    searchResult.snippet(),
                    fallbackCoords);
        }

        Optional<Chapter> chapterOpt = chapterRepo.findById(searchResult.chapterId());

        if (chapterOpt.isPresent()) {
            Chapter chapter = chapterOpt.get();
            PublicationCoordinates coords = new PublicationCoordinates();
            coords.setUniverse(chapter.getUniverse());
            coords.setSeries(chapter.getSeries());
            coords.setBookTitle(chapter.getBookTitle());
            coords.setBookNumber(chapter.getBookNumber());
            coords.setChapterNumber(chapter.getChapterNumber());
            coords.setChapterTitle(chapter.getChapterTitle());

            if (coords.getUniverse() == null || coords.getBookTitle() == null ||
                    coords.getChapterTitle() == null || coords.getBookNumber() == null || coords.getChapterNumber() == null) {
                log.warn("Citation coordinates missing fields for chapter {}: universe={}, series={}, bookTitle={}, chapterTitle={}, bookNumber={}, chapterNumber={}",
                        searchResult.chapterId(), coords.getUniverse(), coords.getSeries(), coords.getBookTitle(),
                        coords.getChapterTitle(), coords.getBookNumber(), coords.getChapterNumber());
            }

            return new CoreCitation(
                    searchResult.chunkId(),
                    searchResult.score(),
                    searchResult.snippet(),
                    coords);
        } else {
            PublicationCoordinates fallbackCoords = new PublicationCoordinates();
            fallbackCoords.setBookNumber(searchResult.bookNumber());
            fallbackCoords.setChapterNumber(searchResult.chapterNumber());

            return new CoreCitation(
                    searchResult.chunkId(),
                    searchResult.score(),
                    searchResult.snippet(),
                    fallbackCoords);
        }
    }

    private boolean isHybridEnabledForRag() {
        return hybridEnabled && hybridRagOnly;
    }

    private CoreSemanticSearchRequest buildHybridVectorSearchRequest(CoreAskRequest request) {
        CoreSemanticSearchRequest searchRequest = buildSearchRequest(request);
        int branchTopN = Math.max(hybridBranchN, request.topK() != null ? request.topK() : 5);
        return new CoreSemanticSearchRequest(
            searchRequest.query(),
            branchTopN,
            searchRequest.threshold(),
            searchRequest.filters(),
            searchRequest.visibility()
        );
    }

    private List<CoreSearchResult> runGraphBranch(CoreAskRequest request) {
        String subject = extractSubjectName(request.question());
        if (subject == null || subject.isBlank()) {
            return List.of();
        }

        List<CypherTemplateRegistry.EntityLookupResult> entityScenes = executeEntityLookupTemplate(
                "individual-scenes",
                Map.of("normalizedName", subject.toLowerCase().trim()),
                request.visibility());

        if (entityScenes.isEmpty()) {
            return List.of();
        }

        List<CoreSearchResult> graphCandidates = new ArrayList<>();
        for (CypherTemplateRegistry.EntityLookupResult sceneResult : entityScenes) {
            if (sceneResult.sceneId() == null) {
                continue;
            }

            try {
                UUID sceneId = UUID.fromString(sceneResult.sceneId());
                List<Chunk> sceneChunks = chunkRepo.findBySceneId(sceneId);
                for (Chunk chunk : sceneChunks) {
                    if (chunk.getId() == null) {
                        continue;
                    }

                    graphCandidates.add(new CoreSearchResult(
                            chunk.getId(),
                            1.0,
                            chunk.getText(),
                            null,
                            sceneResult.bookNumber(),
                            sceneResult.chapterNumber(),
                            sceneId,
                            sceneResult.sceneSummary(),
                            sceneResult.displayName() == null ? List.of() : List.of(sceneResult.displayName()),
                            List.of()));
                }
            } catch (IllegalArgumentException ignored) {
                log.debug("Skipping graph candidate with invalid sceneId: {}", sceneResult.sceneId());
            }
        }

        return graphCandidates.stream().limit(Math.max(hybridBranchN, 1)).toList();
    }

    private List<CypherTemplateRegistry.EntityLookupResult> executeEntityLookupTemplate(String templateId,
                                                                                        Map<String, Object> params,
                                                                                        com.lorevault.api.search.domain.SpoilerVisibility visibility) {
        try {
            return templateRegistry.execute(templateId, params, visibility);
        } catch (EntityLookupException e) {
            log.warn("Entity lookup template '{}' failed: {}", templateId, e.getMessage());
            throw e;
        }
    }

    private List<CoreSearchResult> fuseByRrf(List<CoreSearchResult> vectorResults,
                                            List<CoreSearchResult> graphResults,
                                            Integer finalTopK) {
        record Aggregate(
                CoreSearchResult dto,
                double fusedScore,
                int branchCount,
                int bestRank,
                int vectorRank
        ) {}

        Map<UUID, CoreSearchResult> mergedByChunk = new java.util.HashMap<>();
        Map<UUID, Double> fusedScores = new java.util.HashMap<>();
        Map<UUID, Integer> branchCounts = new java.util.HashMap<>();
        Map<UUID, Integer> bestRanks = new java.util.HashMap<>();
        Map<UUID, Integer> vectorRanks = new java.util.HashMap<>();

        applyRrfBranch(vectorResults, mergedByChunk, fusedScores, branchCounts, bestRanks, vectorRanks, true);
        applyRrfBranch(graphResults, mergedByChunk, fusedScores, branchCounts, bestRanks, vectorRanks, false);

        List<Aggregate> aggregates = fusedScores.entrySet().stream()
                .map(entry -> {
                    UUID chunkId = entry.getKey();
                    return new Aggregate(
                            mergedByChunk.get(chunkId),
                            entry.getValue(),
                            branchCounts.getOrDefault(chunkId, 0),
                            bestRanks.getOrDefault(chunkId, Integer.MAX_VALUE),
                            vectorRanks.getOrDefault(chunkId, Integer.MAX_VALUE));
                })
                .toList();

        int maxResults = Math.max(finalTopK != null ? finalTopK : 5, 1);

        Comparator<Aggregate> rankingComparator = Comparator
                .comparingDouble(Aggregate::fusedScore).reversed()
                .thenComparing(Comparator.comparingInt(Aggregate::branchCount).reversed())
                .thenComparingInt(Aggregate::bestRank)
                .thenComparingInt(Aggregate::vectorRank)
                .thenComparing((Aggregate aggregate) -> aggregate.dto().chunkId().toString());

        return aggregates.stream()
                .sorted(rankingComparator)
                .map(aggregate -> new CoreSearchResult(
                        aggregate.dto().chunkId(),
                        aggregate.fusedScore(),
                        aggregate.dto().snippet(),
                        aggregate.dto().chapterId(),
                        aggregate.dto().bookNumber(),
                        aggregate.dto().chapterNumber(),
                        aggregate.dto().sceneId(),
                        aggregate.dto().sceneSummary(),
                        aggregate.dto().individualsPresent(),
                        aggregate.dto().locationsPresent()))
                .limit(maxResults)
                .toList();
    }

    private void applyRrfBranch(List<CoreSearchResult> branchResults,
                                Map<UUID, CoreSearchResult> mergedByChunk,
                                Map<UUID, Double> fusedScores,
                                Map<UUID, Integer> branchCounts,
                                Map<UUID, Integer> bestRanks,
                                Map<UUID, Integer> vectorRanks,
                                boolean vectorBranch) {
        for (int i = 0; i < branchResults.size(); i++) {
            CoreSearchResult candidate = branchResults.get(i);
            UUID chunkId = candidate.chunkId();
            if (chunkId == null) {
                continue;
            }

            int rank = i + 1;
            double contribution = 1.0 / (Math.max(hybridRrfK, 1) + rank);

            mergedByChunk.merge(chunkId, candidate, this::mergeCandidate);
            fusedScores.merge(chunkId, contribution, Double::sum);
            branchCounts.merge(chunkId, 1, Integer::sum);
            bestRanks.merge(chunkId, rank, Math::min);
            if (vectorBranch) {
                vectorRanks.merge(chunkId, rank, Math::min);
            }
        }
    }

    private CoreSearchResult mergeCandidate(CoreSearchResult left, CoreSearchResult right) {
        String snippet = left.snippet();
        if ((snippet == null || snippet.isBlank()) && right.snippet() != null) {
            snippet = right.snippet();
        }

        UUID chapterId = left.chapterId() != null ? left.chapterId() : right.chapterId();
        Integer bookNumber = left.bookNumber() != null ? left.bookNumber() : right.bookNumber();
        Integer chapterNumber = left.chapterNumber() != null ? left.chapterNumber() : right.chapterNumber();
        UUID sceneId = left.sceneId() != null ? left.sceneId() : right.sceneId();
        String sceneSummary = left.sceneSummary() != null ? left.sceneSummary() : right.sceneSummary();

        List<String> individuals = mergeList(left.individualsPresent(), right.individualsPresent());
        List<String> locations = mergeList(left.locationsPresent(), right.locationsPresent());

        return new CoreSearchResult(
                left.chunkId(),
                left.score(),
                snippet,
                chapterId,
                bookNumber,
                chapterNumber,
                sceneId,
                sceneSummary,
                individuals,
                locations);
    }

    private List<String> mergeList(List<String> first, List<String> second) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        return merged.stream().toList();
    }
}
