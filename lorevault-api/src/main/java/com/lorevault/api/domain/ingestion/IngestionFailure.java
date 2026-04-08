package com.lorevault.api.domain.ingestion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small ingestion-specific failure payload.
 * Kept separate from HTTP error DTOs so pipeline diagnostics can evolve
 * independently from public API error response conventions.
 */
public record IngestionFailure(
        String code,
        String message,
        String exceptionType,
        String stage,
        Map<String, Object> details
) {
    public Map<String, Object> toProperties() {
        Map<String, Object> props = new LinkedHashMap<>();
        putIfPresent(props, "failureCode", code);
        putIfPresent(props, "failureMessage", message);
        putIfPresent(props, "failureExceptionType", exceptionType);
        putIfPresent(props, "failureStage", stage);
        if (details != null) {
            details.forEach((key, value) -> putIfPresent(props, "failureDetail." + key, value));
        }
        return props;
    }

    public static IngestionFailure fromException(String stage, Exception exception) {
        return new IngestionFailure(
                "INGESTION_STAGE_FAILED",
                safeMessage(exception),
                exception != null ? exception.getClass().getSimpleName() : null,
                stage,
                Map.of()
        );
    }

    public static Builder builder(String code, String message) {
        return new Builder(code, message);
    }

    private static String safeMessage(Exception exception) {
        if (exception == null) {
            return "Unknown ingestion failure";
        }
        return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private static void putIfPresent(Map<String, Object> props, String key, Object value) {
        if (key != null && value != null) {
            props.put(key, value);
        }
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

        public IngestionFailure build() {
            return new IngestionFailure(code, message, exceptionType, stage, Map.copyOf(details));
        }
    }
}
