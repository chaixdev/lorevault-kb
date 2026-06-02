package com.lorevault.api.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HashUtils.
 */
@DisplayName("HashUtils")
class HashUtilsTest {

    @Test
    @DisplayName("should generate consistent SHA-256 hashes for same input")
    void generateSha256Hash_SameInput_ShouldProduceConsistentHash() {
        String input = "test content";
        
        String hash1 = HashUtils.generateSha256Hash(input);
        String hash2 = HashUtils.generateSha256Hash(input);
        
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 produces 64-character hex string
    }

    @Test
    @DisplayName("should generate different hashes for different inputs")
    void generateSha256Hash_DifferentInputs_ShouldProduceDifferentHashes() {
        String input1 = "first content";
        String input2 = "second content";
        
        String hash1 = HashUtils.generateSha256Hash(input1);
        String hash2 = HashUtils.generateSha256Hash(input2);
        
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("should generate known hash for specific input")
    void generateSha256Hash_KnownInput_ShouldProduceExpectedHash() {
        String input = "hello world";
        String expectedHash = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
        
        String actualHash = HashUtils.generateSha256Hash(input);
        
        assertEquals(expectedHash, actualHash);
    }

    @Test
    @DisplayName("should handle empty string")
    void generateSha256Hash_EmptyString_ShouldReturnValidHash() {
        String input = "";
        
        String hash = HashUtils.generateSha256Hash(input);
        
        assertNotNull(hash);
        assertEquals(64, hash.length());
        // SHA-256 of empty string
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }
}