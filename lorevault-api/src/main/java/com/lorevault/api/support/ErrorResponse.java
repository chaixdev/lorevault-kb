package com.lorevault.api.support;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized error response format
 */
@Data
@Builder
public class ErrorResponse {
    private ErrorDetails error;
    private LocalDateTime timestamp;
    private String path;

    @Data
    @Builder
    public static class ErrorDetails {
        private String code;
        private String message;
        @Singular
        private Map<String, Object> details;
    }

    // Builder helper methods
    public static ErrorResponseBuilder builder() {
        return new ErrorResponseBuilder();
    }

    public static class ErrorResponseBuilder {
        private String code;
        private String message;
        private Map<String, Object> details = new java.util.HashMap<>();
        private LocalDateTime timestamp;
        private String path;

        public ErrorResponseBuilder code(String code) {
            this.code = code;
            return this;
        }

        public ErrorResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public ErrorResponseBuilder details(String key, Object value) {
            this.details.put(key, value);
            return this;
        }

        public ErrorResponseBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ErrorResponseBuilder path(String path) {
            this.path = path;
            return this;
        }

        public ErrorResponse build() {
            ErrorDetails errorDetails = ErrorDetails.builder()
                    .code(this.code)
                    .message(this.message)
                    .details(this.details)
                    .build();

            return new ErrorResponse(errorDetails, this.timestamp, this.path);
        }
    }
}
