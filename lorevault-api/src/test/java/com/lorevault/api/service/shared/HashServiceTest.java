package com.lorevault.api.service.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for HashService.
 * Tests SHA-256 hash generation with various input scenarios.
 */
@Tag("unit")
@DisplayName("HashService")
class HashServiceTest {

    private HashService hashService;

    @BeforeEach
    void setUp() {
        hashService = new HashService();
    }

    @Test
    @DisplayName("should generate consistent SHA-256 hash for same input")
    void shouldGenerateConsistentHashForSameInput() {
        // Given
        String input = "Hello, World!";
        
        // When
        String hash1 = hashService.generateSha256Hash(input);
        String hash2 = hashService.generateSha256Hash(input);
        
        // Then
        assertThat(hash1)
            .isNotNull()
            .isNotEmpty()
            .hasSize(64) // SHA-256 produces 64-character hex string
            .isEqualTo(hash2);
    }

    @Test
    @DisplayName("should generate different hashes for different inputs")
    void shouldGenerateDifferentHashesForDifferentInputs() {
        // Given
        String input1 = "Hello, World!";
        String input2 = "Hello, World";
        
        // When
        String hash1 = hashService.generateSha256Hash(input1);
        String hash2 = hashService.generateSha256Hash(input2);
        
        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("should generate known hash for test vector")
    void shouldGenerateKnownHashForTestVector() {
        // Given - well-known test vector
        String input = "abc";
        String expectedHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        
        // When
        String actualHash = hashService.generateSha256Hash(input);
        
        // Then
        assertThat(actualHash).isEqualTo(expectedHash);
    }

    @Test
    @DisplayName("should handle empty string input")
    void shouldHandleEmptyStringInput() {
        // Given
        String input = "";
        String expectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        
        // When
        String actualHash = hashService.generateSha256Hash(input);
        
        // Then
        assertThat(actualHash)
            .isNotNull()
            .hasSize(64)
            .isEqualTo(expectedHash);
    }

    @Test
    @DisplayName("should handle unicode characters correctly")
    void shouldHandleUnicodeCharactersCorrectly() {
        // Given
        String input = "Hello 世界! 🌍";
        
        // When
        String hash1 = hashService.generateSha256Hash(input);
        String hash2 = hashService.generateSha256Hash(input);
        
        // Then
        assertThat(hash1)
            .isNotNull()
            .hasSize(64)
            .isEqualTo(hash2);
    }

    @Test
    @DisplayName("should handle large input text")
    void shouldHandleLargeInputText() {
        // Given
        StringBuilder largeInput = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeInput.append("This is line ").append(i).append(" of the large input text.\n");
        }
        
        // When
        String hash = hashService.generateSha256Hash(largeInput.toString());
        
        // Then
        assertThat(hash)
            .isNotNull()
            .hasSize(64)
            .matches("^[a-f0-9]{64}$"); // Valid hex string
    }

    @Test
    @DisplayName("should handle null input gracefully")
    void shouldHandleNullInputGracefully() {
        // Given
        String input = null;
        
        // When & Then
        assertThatThrownBy(() -> hashService.generateSha256Hash(input))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should generate valid hexadecimal output")
    void shouldGenerateValidHexadecimalOutput() {
        // Given
        String input = "Test input for hex validation";
        
        // When
        String hash = hashService.generateSha256Hash(input);
        
        // Then
        assertThat(hash)
            .hasSize(64)
            .matches("^[a-f0-9]{64}$") // Only lowercase hex characters
            .doesNotContain("G", "H", "I"); // No invalid hex characters
    }

    @Test
    @DisplayName("should be deterministic across multiple calls")
    void shouldBeDeterministicAcrossMultipleCalls() {
        // Given
        String input = "Deterministic test input";
        
        // When - Generate hash multiple times
        String[] hashes = new String[5];
        for (int i = 0; i < hashes.length; i++) {
            hashes[i] = hashService.generateSha256Hash(input);
        }
        
        // Then - All hashes should be identical
        for (int i = 1; i < hashes.length; i++) {
            assertThat(hashes[i]).isEqualTo(hashes[0]);
        }
    }
}