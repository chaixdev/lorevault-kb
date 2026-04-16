package com.lorevault.api.search;

import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.Chunk;
import com.lorevault.api.search.AskDtos.AskRequest;
import com.lorevault.api.search.AskDtos.AskResponse;
import com.lorevault.api.search.AskDtos.CitationDto;
import com.lorevault.api.search.AskDtos.AskMetadata;
import com.lorevault.api.search.AskDtos.AskFilters;
import com.lorevault.api.search.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.search.SemanticSearchDtos.SemanticSearchResponse;
import com.lorevault.api.search.SemanticSearchDtos.SearchResultDto;
import com.lorevault.api.search.SemanticSearchDtos.SemanticSearchFilters;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.ChunkGraphRepository;
import com.lorevault.api.ai.PromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
@RequiredArgsConstructor
@Slf4j
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

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Answer a question using the routed RAG strategy.
     *
     * @param request Question and search parameters
     * @return Answer with source citations and metadata
     */
    public AskResponse ask(AskRequest request) {
        log.debug("RAG request: question='{}', topK={}", request.getQuestion(), request.getTopK());

        long startTime = System.currentTimeMillis();

        QuestionIntent intent = intentClassifier.classify(request.getQuestion());
        log.debug("Classified question intent: {}", intent);

        return switch (intent) {
            case ENTITY_LOOKUP -> handleEntityLookup(request, startTime);
            case NARRATIVE_QA, AMBIGUOUS -> handleNarrativeQa(request, startTime);
        };
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
    private AskResponse handleEntityLookup(AskRequest request, long startTime) {
        String question = request.getQuestion();
        String subject = extractSubjectName(question);

        if (subject == null || subject.isBlank()) {
            log.debug("Could not extract subject from entity-lookup question; falling back to narrative QA");
            return handleNarrativeQa(request, startTime);
        }

        String normalizedName = subject.toLowerCase().trim();
        log.debug("Entity lookup: subject='{}', normalizedName='{}'", subject, normalizedName);

        // Try individual lookup first, then location lookup
        List<CypherTemplateRegistry.EntityLookupResult> results =
                templateRegistry.execute("individual-lookup",
                        Map.of("normalizedName", normalizedName),
                        request.getVisibility());

        if (results.isEmpty()) {
            results = templateRegistry.execute("location-lookup",
                    Map.of("normalizedName", normalizedName),
                    request.getVisibility());
        }

        if (results.isEmpty()) {
            log.debug("No entity template results for '{}'; falling back to narrative QA", normalizedName);
            return handleNarrativeQa(request, startTime);
        }

        String context = buildEntityContext(results);
        String answer = generateAnswer(question, context);

        long processingTime = System.currentTimeMillis() - startTime;
        AskMetadata metadata = AskMetadata.of(
                question, results.size(), results.size(), processingTime,
                modelId != null ? modelId : "unknown-model");

        return AskResponse.of(answer, List.of(), metadata);
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

    private AskResponse handleNarrativeQa(AskRequest request, long startTime) {
        // Step 1: Retrieve relevant chunks via semantic search
        SemanticSearchRequest searchRequest = buildSearchRequest(request);
        SemanticSearchResponse searchResponse = semanticSearchService.search(searchRequest);

        List<SearchResultDto> searchResults = searchResponse.getResults();
        log.debug("Retrieved {} chunks for RAG", searchResults.size());

        // Step 2: Filter by threshold if specified
        List<SearchResultDto> filteredResults = filterByThreshold(searchResults, request.getThreshold());
        log.debug("Using {} chunks after threshold filtering", filteredResults.size());

        // Step 3: Handle no evidence case
        if (filteredResults.isEmpty()) {
            return buildNoEvidenceResponse(request, searchResults.size(), startTime);
        }

        // Step 4: Generate answer using LLM
        String context = buildContextFromEvidence(filteredResults);
        String answer = generateAnswer(request.getQuestion(), context);

        // Step 5: Build citations from filtered results
        List<CitationDto> citations = filteredResults.stream()
                .map(this::buildCitation)
                .toList();

        long processingTime = System.currentTimeMillis() - startTime;

        AskMetadata metadata = AskMetadata.of(
                request.getQuestion(),
                searchResults.size(),
                filteredResults.size(),
                processingTime,
                modelId != null ? modelId : "unknown-model");

        log.debug("RAG completed in {}ms: answer length={}, citations={}",
                processingTime, answer.length(), citations.size());

        return AskResponse.of(answer, citations, metadata);
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

    private SemanticSearchRequest buildSearchRequest(AskRequest request) {
        SemanticSearchRequest searchRequest = new SemanticSearchRequest();
        searchRequest.setQuery(request.getQuestion());
        searchRequest.setTopK(request.getTopK());
        searchRequest.setThreshold(null); // Handle threshold filtering in RAG service
        searchRequest.setVisibility(request.getVisibility());

        if (request.getFilters() != null) {
            SemanticSearchFilters validatedFilters = validateAndConvertFilters(request.getFilters());
            if (validatedFilters != null) {
                searchRequest.setFilters(validatedFilters);
            }
        }

        return searchRequest;
    }

    /**
     * Validates filter hierarchy and returns properly structured filters.
     * Enforces hierarchy: universe -> series -> book -> chapter
     */
    private SemanticSearchFilters validateAndConvertFilters(AskFilters askFilters) {
        String universe = askFilters.getUniverse();
        String series = askFilters.getSeries();
        Integer bookNumber = askFilters.getBookNumber();
        Integer chapterNumber = askFilters.getChapterNumber();

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

        SemanticSearchFilters searchFilters = new SemanticSearchFilters();
        searchFilters.setUniverse(universe);
        searchFilters.setSeries(series);
        searchFilters.setBookNumber(bookNumber);
        searchFilters.setChapterNumber(chapterNumber);

        log.debug("Applied validated filters: universe='{}', series='{}', book={}, chapter={}",
                universe, series, bookNumber, chapterNumber);

        return searchFilters;
    }

    private List<SearchResultDto> filterByThreshold(List<SearchResultDto> results, Double threshold) {
        if (threshold == null) {
            return results;
        }
        return results.stream()
                .filter(result -> result.getScore() >= threshold)
                .toList();
    }

    private AskResponse buildNoEvidenceResponse(AskRequest request, int chunksRetrieved, long startTime) {
        String noEvidenceAnswer = "No evidence found in the knowledge base to answer this question.";

        long processingTime = System.currentTimeMillis() - startTime;
        AskMetadata metadata = AskMetadata.of(
                request.getQuestion(),
                chunksRetrieved,
                0,
                processingTime,
                modelId != null ? modelId : "unknown-model");

        return AskResponse.of(noEvidenceAnswer, List.of(), metadata);
    }

    private String buildContextFromEvidence(List<SearchResultDto> evidence) {
        StringBuilder context = new StringBuilder();

        IntStream.range(0, evidence.size())
                .forEach(i -> {
                    SearchResultDto result = evidence.get(i);

                    Optional<Chunk> chunkOpt = chunkRepo.findById(result.getChunkId());
                    String chunkText = chunkOpt.map(Chunk::getText).orElse(result.getSnippet());

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
    private String buildLocationTag(SearchResultDto result) {
        List<String> parts = new ArrayList<>();

        if (result.getBookNumber() != null && result.getChapterNumber() != null) {
            parts.add(String.format("Book %d, Chapter %d", result.getBookNumber(), result.getChapterNumber()));
        }

        List<String> individuals = result.getIndividualsPresent();
        if (individuals != null && !individuals.isEmpty()) {
            parts.add("featuring: " + String.join(", ", individuals));
        }

        List<String> locations = result.getLocationsPresent();
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

    private CitationDto buildCitation(SearchResultDto searchResult) {
        Optional<Chapter> chapterOpt = chapterRepo.findById(searchResult.getChapterId());

        if (chapterOpt.isPresent()) {
            Chapter chapter = chapterOpt.get();
            com.lorevault.api.support.PublicationCoordinates coords = new com.lorevault.api.support.PublicationCoordinates();
            coords.setUniverse(chapter.getUniverse());
            coords.setSeries(chapter.getSeries());
            coords.setBookTitle(chapter.getBookTitle());
            coords.setBookNumber(chapter.getBookNumber());
            coords.setChapterNumber(chapter.getChapterNumber());
            coords.setChapterTitle(chapter.getChapterTitle());

            if (coords.getUniverse() == null || coords.getBookTitle() == null ||
                    coords.getChapterTitle() == null || coords.getBookNumber() == null || coords.getChapterNumber() == null) {
                log.warn("Citation coordinates missing fields for chapter {}: universe={}, series={}, bookTitle={}, chapterTitle={}, bookNumber={}, chapterNumber={}",
                        searchResult.getChapterId(), coords.getUniverse(), coords.getSeries(), coords.getBookTitle(),
                        coords.getChapterTitle(), coords.getBookNumber(), coords.getChapterNumber());
            }

            return CitationDto.of(
                    searchResult.getChunkId(),
                    searchResult.getScore(),
                    searchResult.getSnippet(),
                    coords);
        } else {
            com.lorevault.api.support.PublicationCoordinates fallbackCoords = new com.lorevault.api.support.PublicationCoordinates();
            fallbackCoords.setBookNumber(searchResult.getBookNumber());
            fallbackCoords.setChapterNumber(searchResult.getChapterNumber());

            return CitationDto.of(
                    searchResult.getChunkId(),
                    searchResult.getScore(),
                    searchResult.getSnippet(),
                    fallbackCoords);
        }
    }
}
