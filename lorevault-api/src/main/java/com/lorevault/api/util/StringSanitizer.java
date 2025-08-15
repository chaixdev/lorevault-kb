package com.lorevault.api.util;

public class StringSanitizer {
        public static  String toSnakeCase(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // Lowercase
        String lower = input.toLowerCase();

        // Replace non-alphanumeric characters (except spaces) with nothing
        String cleaned = lower.replaceAll("[^a-z0-9\\s]", "");

        // Replace one or more spaces with underscores
        return cleaned.trim().replaceAll("\\s+", "_");
    }
}
