package com.lorevault.api.common.error;

/**
 * Shared exception message sanitization utilities.
 *
 * <p>Previously part of the deleted {@code PipelineStageSupport} class.
 * Consolidates the previously duplicated {@code safeMessage} pattern used in 7+ classes.
 */
public final class ExceptionSanitizer {

    private ExceptionSanitizer() {
        // utility class — no instantiation
    }

    /**
     * Returns a safe message from an exception, falling back to the class simple name
     * when the message is {@code null}.
     *
     * <p>Use this for log messages and failure records where the message must never
     * be {@code null}. For user-facing error messages that need CR/LF stripping and
     * length truncation, use {@link #sanitizeMessage(Exception)} instead.
     */
    public static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }

    /**
     * Strips CR/LF from an exception message and truncates to 200 characters.
     *
     * <p>This is the canonical sanitizer for user-facing error messages.
     */
    public static String sanitizeMessage(Exception e) {
        if (e == null) {
            return "unknown error";
        }
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        // Strip CR, LF and other ASCII control characters (< 0x20, except space)
        String sanitized = message.replaceAll("[\\r\\n\\t\\x00-\\x1F\\x7F]", " ").strip();
        if (sanitized.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return sanitized.length() > 200 ? sanitized.substring(0, 200) + "…" : sanitized;
    }
}
