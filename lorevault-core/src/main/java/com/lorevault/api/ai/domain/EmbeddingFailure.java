package com.lorevault.api.ai.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small AI-owned failure payload for embedding-stage failures.
 */
public record EmbeddingFailure(
        String code,
        String message,
        String exceptionType,
        String stage,
        Map<String, Object> details
) {
    public static Builder builder(String code, String message) {
        return new Builder(code, message);
    }

    public static final class Builder {
        private final String code;
        private final String message;
        private String exceptionType;
        private String stage;
        private final Map<String, Object> details = new LinkedHashMap<>();

        private Builder(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public Builder exceptionType(String exceptionType) {
            this.exceptionType = exceptionType;
            return this;
        }

        public Builder stage(String stage) {
            this.stage = stage;
            return this;
        }

        public Builder detail(String key, Object value) {
            if (key != null && value != null) {
                details.put(key, value);
            }
            return this;
        }

        public EmbeddingFailure build() {
            return new EmbeddingFailure(code, message, exceptionType, stage, Map.copyOf(details));
        }
    }
}
