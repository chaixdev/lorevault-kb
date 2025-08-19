package com.lorevault.api.service.ask;

import com.lorevault.api.dto.ask.AskDtos.AskRequest;
import com.lorevault.api.dto.ask.AskDtos.AskResponse;
import com.lorevault.api.dto.ask.AskDtos.CitationDto;
import com.lorevault.api.dto.ask.AskDtos.AskMetadata;
import com.lorevault.api.dto.ask.AskDtos.AskFilters;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchRequest;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchResponse;
import com.lorevault.api.dto.search.SemanticSearchDtos.SearchResultDto;
import com.lorevault.api.dto.search.SemanticSearchDtos.SemanticSearchFilters;
import com.lorevault.api.service.search.SemanticSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Service for RAG (Retrieve-Augment-Generate) question answering.
 * Retrieves relevant chunks via semantic search and synthesizes answers using LLM.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final SemanticSearchService semanticSearchService;
    
    @Qualifier("nlpBig")
    private final ChatClient chatClient;

    @Value("${lorevault.ai.models.nlp-big.model:llama-3.1-70b-versatile}")
    private String modelId;

    /**
     * Answer a question using RAG (Retrieve-Augment-Generate) approach.
     * 
     * @param request Question and search parameters
     * @return Answer with source citations and metadata
     */
    public AskResponse ask(AskRequest request) {
        log.debug("RAG request: question='{}', topK={}", request.getQuestion(), request.getTopK());
        
        long startTime = System.currentTimeMillis();

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
        String answer = generateAnswer(request.getQuestion(), filteredResults);
        
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
            modelId != null ? modelId : "unknown-model"
        );

        log.debug("RAG completed in {}ms: answer length={}, citations={}", 
                 processingTime, answer.length(), citations.size());

        return AskResponse.of(answer, citations, metadata);
    }

    private SemanticSearchRequest buildSearchRequest(AskRequest request) {
        SemanticSearchRequest searchRequest = new SemanticSearchRequest();
        searchRequest.setQuery(request.getQuestion());
        searchRequest.setTopK(request.getTopK());
        searchRequest.setThreshold(null); // Handle threshold filtering in RAG service
        
        // Convert and validate filters if present
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
     * 
     * @param askFilters The filters from the ask request
     * @return Valid semantic search filters or null if all filters are invalid
     */
    private SemanticSearchFilters validateAndConvertFilters(AskFilters askFilters) {
        String universe = askFilters.getUniverse();
        String series = askFilters.getSeries();
        Integer bookNumber = askFilters.getBookNumber();
        Integer chapterNumber = askFilters.getChapterNumber();
        
        // Validate hierarchy constraints
        if (chapterNumber != null && bookNumber == null) {
            log.warn("Chapter filter {} ignored: book number must be specified", chapterNumber);
            chapterNumber = null;
        }
        
        if (bookNumber != null && series == null) {
            log.warn("Book filter {} ignored: series must be specified", bookNumber);
            bookNumber = null;
            chapterNumber = null; // Also clear chapter if book is invalid
        }
        
        if (series != null && universe == null) {
            log.warn("Series filter '{}' ignored: universe must be specified", series);
            series = null;
            bookNumber = null; // Also clear dependent filters
            chapterNumber = null;
        }
        
        // Return null if no valid filters remain
        if (universe == null && series == null && bookNumber == null && chapterNumber == null) {
            return null;
        }
        
        // Build validated filters
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
            0, // chunksUsed
            processingTime,
            modelId != null ? modelId : "unknown-model"
        );
        
        return AskResponse.of(noEvidenceAnswer, List.of(), metadata);
    }

    private String generateAnswer(String question, List<SearchResultDto> evidence) {
        // Build context from evidence chunks
        String context = buildContextFromEvidence(evidence);
        
        // Build prompts
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(question, context);
        
        log.debug("Calling LLM for answer generation: model={}, context_length={}", 
                 modelId, context.length());

        // Call LLM
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

    private String buildContextFromEvidence(List<SearchResultDto> evidence) {
        StringBuilder context = new StringBuilder();
        
        IntStream.range(0, evidence.size())
            .forEach(i -> {
                SearchResultDto result = evidence.get(i);
                context.append(String.format("[%d] %s", i + 1, result.getSnippet()));
                
                // Add chapter context if available
                if (result.getBookNumber() != null && result.getChapterNumber() != null) {
                    context.append(String.format(" (Book %d, Chapter %d)", 
                                   result.getBookNumber(), result.getChapterNumber()));
                }
                
                context.append("\n\n");
            });
        
        return context.toString();
    }

    private String buildSystemPrompt() {
        return """
            You are a knowledgeable assistant helping users understand fictional stories and characters.
            
            Your task is to answer the question using ONLY the provided context from the story.
            
            Guidelines:
            - Base your answer strictly on the provided evidence
            - If the evidence is insufficient, say so explicitly
            - Be concise but informative
            - Do not add information from outside the provided context
            - Maintain the narrative voice and tone appropriate to the source material
            """;
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
        return CitationDto.of(
            searchResult.getChunkId(),
            searchResult.getScore(),
            searchResult.getSnippet(),
            searchResult.getChapterId(),
            searchResult.getBookNumber(),
            searchResult.getChapterNumber()
        );
    }
}
