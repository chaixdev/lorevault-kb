package com.lorevault.api.dto.ask;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * DTOs for RAG question answering endpoints.
 */
public class AskDtos {

    /**
     * Request DTO for RAG question answering.
     */
    @Data
    public static class AskRequest {
        
        @NotBlank(message = "Question cannot be blank")
        @Size(min = 1, max = 1000, message = "Question must be between 1 and 1000 characters")
        private String question;
        
        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 20, message = "topK cannot exceed 20") 
        private Integer topK = 5;
        
        @Min(value = 0, message = "Threshold cannot be negative")
        @Max(value = 1, message = "Threshold cannot exceed 1.0")
        private Double threshold;
        
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private AskFilters filters;
    }

    /**
     * Filters for constraining RAG search scope (mirrors semantic search filters).
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AskFilters {
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
     * Response DTO for RAG question answering.
     */
    @Data
    public static class AskResponse {
        private String answer;
        private List<CitationDto> citations;
        private AskMetadata metadata;

        public static AskResponse of(String answer, List<CitationDto> citations, AskMetadata metadata) {
            AskResponse response = new AskResponse();
            response.answer = answer;
            response.citations = citations;
            response.metadata = metadata;
            return response;
        }
    }

    /**
     * Citation with source attribution and publication coordinates.
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CitationDto {
        private UUID chunkId;
        private double score;
        private String snippet;
        // Nested publication coordinates for full citation context
        private com.lorevault.api.dto.shared.PublicationCoordinates coordinates;

        public static CitationDto of(UUID chunkId, double score, String snippet,
                                   com.lorevault.api.dto.shared.PublicationCoordinates coordinates) {
            CitationDto dto = new CitationDto();
            dto.chunkId = chunkId;
            dto.score = score;
            dto.snippet = snippet;
            dto.coordinates = coordinates;
            return dto;
        }
    }

    /**
     * Metadata about the RAG operation.
     */
    @Data
    public static class AskMetadata {
        private String question;
        private int chunksRetrieved;
        private int chunksUsed;
        private long processingTimeMs;
        private String modelId;

        public static AskMetadata of(String question, int chunksRetrieved, int chunksUsed, 
                                   long processingTimeMs, String modelId) {
            AskMetadata metadata = new AskMetadata();
            metadata.question = question;
            metadata.chunksRetrieved = chunksRetrieved;
            metadata.chunksUsed = chunksUsed;
            metadata.processingTimeMs = processingTimeMs;
            metadata.modelId = modelId;
            return metadata;
        }
    }
}
