package com.lorevault.api.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * DTOs for semantic search endpoints.
 */
public class SemanticSearchDtos {

    /**
     * Request DTO for semantic search.
     */
    @Data
    public static class SemanticSearchRequest {
        
        @NotBlank(message = "Query cannot be blank")
        @Size(min = 1, max = 1000, message = "Query must be between 1 and 1000 characters")
        private String query;
        
        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 50, message = "topK cannot exceed 50") 
        private Integer topK = 5;
        
        @Min(value = 0, message = "Threshold cannot be negative")
        @Max(value = 1, message = "Threshold cannot exceed 1.0")
        private Double threshold;
        
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private SemanticSearchFilters filters;

        @Valid
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private SpoilerVisibility visibility;
    }

    /**
     * Filters for constraining semantic search scope.
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SemanticSearchFilters {
        @Size(max = 100, message = "Universe name too long")
        private String universe;
        
        @Size(max = 100, message = "Series name too long")
        private String series;
        
        @Min(value = 1, message = "Book number must be positive")
        private Integer bookNumber;
        
        @Min(value = 1, message = "Chapter number must be positive")
        private Integer chapterNumber;
    }

    /**
     * Response DTO for semantic search results.
     */
    @Data
    public static class SemanticSearchResponse {
        private List<SearchResultDto> results;
        private SearchMetadata metadata;

        public static SemanticSearchResponse of(List<SearchResultDto> results, SearchMetadata metadata) {
            SemanticSearchResponse response = new SemanticSearchResponse();
            response.results = results;
            response.metadata = metadata;
            return response;
        }
    }

    /**
     * Individual search result, including scene-level entity context when available.
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SearchResultDto {
        private UUID chunkId;
        private double score;
        private String snippet;
        private UUID chapterId;
        private Integer bookNumber;
        private Integer chapterNumber;
        private UUID sceneId;
        private String sceneSummary;
        private List<String> individualsPresent;
        private List<String> locationsPresent;

        public static SearchResultDto of(UUID chunkId, double score, String snippet,
                                         UUID chapterId, Integer bookNumber, Integer chapterNumber) {
            return of(chunkId, score, snippet, chapterId, bookNumber, chapterNumber,
                      null, null, List.of(), List.of());
        }

        public static SearchResultDto of(UUID chunkId, double score, String snippet,
                                         UUID chapterId, Integer bookNumber, Integer chapterNumber,
                                         UUID sceneId, String sceneSummary,
                                         List<String> individualsPresent, List<String> locationsPresent) {
            SearchResultDto dto = new SearchResultDto();
            dto.chunkId = chunkId;
            dto.score = score;
            dto.snippet = snippet;
            dto.chapterId = chapterId;
            dto.bookNumber = bookNumber;
            dto.chapterNumber = chapterNumber;
            dto.sceneId = sceneId;
            dto.sceneSummary = sceneSummary;
            dto.individualsPresent = (individualsPresent == null || individualsPresent.isEmpty()) ? null : individualsPresent;
            dto.locationsPresent  = (locationsPresent  == null || locationsPresent.isEmpty())  ? null : locationsPresent;
            return dto;
        }
    }

    /**
     * Metadata about the search operation.
     */
    @Data
    public static class SearchMetadata {
        private String query;
        private int totalResults;
        private int returnedResults;
        private long processingTimeMs;

        public static SearchMetadata of(String query, int totalResults, int returnedResults, long processingTimeMs) {
            SearchMetadata metadata = new SearchMetadata();
            metadata.query = query;
            metadata.totalResults = totalResults;
            metadata.returnedResults = returnedResults;
            metadata.processingTimeMs = processingTimeMs;
            return metadata;
        }
    }
}
