package com.lorevault.api.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Minimal request/response DTOs for semantic search (KISS)
 */
public class SemanticSearchDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private String query;
        private Integer topK;
        private Double threshold;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultItem {
        private String chunkId;
        private String chapterId;
        private String content;
        private Double score; // similarity score if available
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private List<ResultItem> results;
    }
}
