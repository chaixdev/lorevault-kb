package com.lorevault.api.service;

import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.service.content.TextChunkingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TextChunkingService implementing specification-compliant chunking behavior.
 * Tests both decision gate logic and sentence-aware sliding window algorithm.
 */
class TextChunkingServiceTest {

    private TextChunkingService textChunkingService;

    @BeforeEach
    void setUp() {
        textChunkingService = new TextChunkingService();
        
        // Set configuration values via reflection to match specification
        ReflectionTestUtils.setField(textChunkingService, "decisionThreshold", 5000);
        ReflectionTestUtils.setField(textChunkingService, "targetChunkSize", 3000);
        ReflectionTestUtils.setField(textChunkingService, "overlapPercentage", 15);
        ReflectionTestUtils.setField(textChunkingService, "minChunkSize", 2000);
        ReflectionTestUtils.setField(textChunkingService, "maxChunkSize", 4000);
    }

    @Test
    void extractChunks_WhenTextIsNull_ShouldReturnEmptyList() {
        // When
        List<Chunk> result = textChunkingService.extractChunks(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void extractChunks_WhenTextIsEmpty_ShouldReturnEmptyList() {
        // When
        List<Chunk> result = textChunkingService.extractChunks("");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void extractChunks_WhenTextIsWhitespace_ShouldReturnEmptyList() {
        // When
        List<Chunk> result = textChunkingService.extractChunks("   \n\t  ");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void extractChunks_WhenTextBelowDecisionThreshold_ShouldCreateSingleChunk() {
        // Given - Text with exactly 4000 characters (below 5000 threshold)
        String shortText = "A".repeat(4000);

        // When
        List<Chunk> result = textChunkingService.extractChunks(shortText);

        // Then - Stage 4A: Single chunk creation
        assertThat(result).hasSize(1);
        
        Chunk chunk = result.get(0);
        assertThat(chunk.getChunkNumberInChapter()).isEqualTo(1);
        assertThat(chunk.getStartCharInChapter()).isZero();
        assertThat(chunk.getEndCharInChapter()).isEqualTo(4000);
    }

    @Test
    void extractChunks_WhenTextAtDecisionThreshold_ShouldCreateSingleChunk() {
        // Given - Text with exactly 5000 characters (at threshold)
        String thresholdText = "A".repeat(5000);

        // When
        List<Chunk> result = textChunkingService.extractChunks(thresholdText);

        // Then - Stage 4A: Single chunk creation
        assertThat(result).hasSize(1);
        
        Chunk chunk = result.get(0);
        assertThat(chunk.getChunkNumberInChapter()).isEqualTo(1);
        assertThat(chunk.getStartCharInChapter()).isZero();
        assertThat(chunk.getEndCharInChapter()).isEqualTo(5000);
    }

    @Test
    void extractChunks_WhenTextAboveDecisionThreshold_ShouldCreateMultipleChunks() {
        // Given - Create text with proper sentences that will be above 5000 chars
        StringBuilder sb = new StringBuilder();
        String sentence = "This is a meaningful sentence that contains enough words to make the text substantial and realistic for testing purposes. ";
        // Each sentence is about 120 characters, so we need about 50 sentences to get ~6000 chars
        for (int i = 0; i < 50; i++) {
            sb.append("Sentence number ").append(i + 1).append(": ").append(sentence);
        }
        String longText = sb.toString();
        
        System.out.println("Test text length: " + longText.length());

        // When
        List<Chunk> result = textChunkingService.extractChunks(longText);

        // Then - Stage 4B: Multi-chunk subdivision
        assertThat(result).hasSizeGreaterThan(1);
        
        System.out.println("Number of chunks created: " + result.size());
        for (int i = 0; i < result.size(); i++) {
            Chunk chunk = result.get(i);
            int chunkLength = chunk.getEndCharInChapter() - chunk.getStartCharInChapter();
            System.out.println("Chunk " + (i+1) + ": " + chunk.getStartCharInChapter() + "-" + 
                             chunk.getEndCharInChapter() + " (length: " + chunkLength + ")");
        }
        
        // Verify chunk numbering is sequential
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).getChunkNumberInChapter()).isEqualTo(i + 1);
        }
        
        // Verify chunks are within size constraints (specification: 2000-4000 chars)
        for (Chunk chunk : result) {
            int chunkLength = chunk.getEndCharInChapter() - chunk.getStartCharInChapter();
            assertThat(chunkLength).isBetween(2000, 4000);
        }
        
        // Verify chunks cover entire text without gaps (account for normalization)
        assertThat(result.get(0).getStartCharInChapter()).isZero();
        // The normalized text might be slightly shorter than original
        int lastChunkEnd = result.get(result.size() - 1).getEndCharInChapter();
        assertThat(lastChunkEnd).isBetween(longText.length() - 5, longText.length());
    }

    @Test
    void extractChunks_WhenTextHasSentences_ShouldRespectSentenceBoundaries() {
        // Given - Text with clear sentence boundaries
        String textWithSentences = "First sentence here. Second sentence follows. " +
                "Third sentence continues the pattern. Fourth sentence adds more content. " +
                "Fifth sentence extends further. ";
        textWithSentences = textWithSentences.repeat(100); // Make it large enough for multiple chunks

        // When
        List<Chunk> result = textChunkingService.extractChunks(textWithSentences);

        // Then
        if (result.size() > 1) {
            // Verify that chunks end at sentence boundaries (don't split mid-sentence)
            for (Chunk chunk : result.subList(0, result.size() - 1)) { // All but last
                String chunkText = textWithSentences.substring(
                    chunk.getStartCharInChapter(), 
                    chunk.getEndCharInChapter()
                );
                
                // Chunk should end with sentence-ending punctuation followed by space
                // (or be at end of text)
                assertThat(chunkText).matches(".*[.!?]\\s*$");
            }
        }
    }

    @Test
    void extractChunks_WhenMultipleChunks_ShouldHaveOverlap() {
        // Given - Long text that will definitely create multiple chunks
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("Sentence ").append(i + 1).append(" contains meaningful content for testing overlap behavior. ");
        }
        String longText = sb.toString();

        // When
        List<Chunk> result = textChunkingService.extractChunks(longText);

        // Then
        if (result.size() > 1) {
            // Verify overlap exists between consecutive chunks
            for (int i = 0; i < result.size() - 1; i++) {
                Chunk current = result.get(i);
                Chunk next = result.get(i + 1);
                
                // Next chunk should start before current chunk ends (overlap)
                assertThat(next.getStartCharInChapter()).isLessThan(current.getEndCharInChapter());
            }
        }
    }

    @Test
    void extractChunks_ShouldNormalizeWhitespace() {
        // Given - Text with various whitespace issues
        String messyText = "Text\r\nwith\r\n\nvarious\t\t\twhitespace   issues\n\n\nthat need   normalization.";
        
        // When
        List<Chunk> result = textChunkingService.extractChunks(messyText);

        // Then
        assertThat(result).hasSize(1); // Should be below threshold
        
        // The chunk coordinates should reflect the normalized text length, not original
        Chunk chunk = result.get(0);
        assertThat(chunk.getEndCharInChapter()).isLessThan(messyText.length());
    }
}
