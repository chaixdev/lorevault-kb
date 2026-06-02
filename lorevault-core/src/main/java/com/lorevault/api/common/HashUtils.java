package com.lorevault.api.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for generating content hashes.
 * This replaces the HashService to eliminate unnecessary service layer indirection.
 */
public final class HashUtils {

    private HashUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Generate a SHA-256 hash of the given text
     * 
     * @param text the input text to hash
     * @return the SHA-256 hash as a hexadecimal string
     * @throws RuntimeException if SHA-256 algorithm is not available
     */
    public static String generateSha256Hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}