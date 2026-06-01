package com.lorevault.api.common;

/**
 * Canonical exception message sanitizer for log safety.
 * <p>
 * Strips control characters, truncates to 200 characters,
 * and handles null messages gracefully.
 */
public final class ExceptionSanitizer {

    private static final int MAX_LENGTH = 200;

    private ExceptionSanitizer() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a log-safe message from an exception.
     * Strips control characters, truncates to {@value #MAX_LENGTH} characters.
     *
     * @param throwable the exception (may be null)
     * @return sanitized message, or "null" if throwable is null
     */
    public static String sanitize(Throwable throwable) {
        if (throwable == null) {
            return "null";
        }
        String message = throwable.getMessage();
        if (message == null || message.isEmpty()) {
            return throwable.getClass().getSimpleName();
        }
        String sanitized = message.replaceAll("[\\p{Cntrl}]", "");
        if (sanitized.length() > MAX_LENGTH) {
            sanitized = sanitized.substring(0, MAX_LENGTH) + "...";
        }
        return sanitized;
    }
}
