package com.lorevault.api.web;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Standardized error response format
 */
@Data
@NoArgsConstructor
public class ErrorResponse {
    private ErrorDetails error;
    private LocalDateTime timestamp;
    private String path;

    public ErrorResponse(ErrorDetails error, LocalDateTime timestamp, String path) {
        this.error = error;
        this.timestamp = timestamp;
        this.path = path;
    }

    @Data
    @NoArgsConstructor
    public static class ErrorDetails {
        private String code;
        private String message;
        @Singular
        private Map<String, Object> details;

        public ErrorDetails(String code, String message, Map<String, Object> details) {
            this.code = code;
            this.message = message;
            this.details = details;
        }
    }

    // Builder helper methods
    public static ErrorResponseBuilder builder() {
        return new ErrorResponseBuilder();
    }

    public static class ErrorResponseBuilder {
        private String code;
        private String message;
        private Map<String, Object> details = new HashMap<>();
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
            ErrorDetails errorDetails = new ErrorDetails(this.code, this.message, this.details);

            return new ErrorResponse(errorDetails, this.timestamp, this.path);
        }
    }
}
