package com.lorevault.api.ai;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify the new chunking configuration with 800-character chunks and 200-character overlap.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "lorevault.content.chunking.target-size=800",
    "lorevault.content.chunking.overlap-percentage=25",
    "lorevault.content.chunking.min-chunk-size=400", 
    "lorevault.content.chunking.max-chunk-size=1200",
    "lorevault.content.chunking.decision-threshold=1600"
})
@DisplayName("TextChunkingService Configuration")
class TextChunkingServiceConfigurationTest {

    @Autowired
    private TextChunkingService textChunkingService;

    @Test
    @DisplayName("should use 800-character target chunks with 200-character overlap")
    void shouldUse800CharTargetChunksWithOverlap() {
        // Create text that will trigger multi-chunk subdivision (> 1600 chars)
        StringBuilder sb = new StringBuilder();
        String sentence = "This is a test sentence that will be used for chunking. ";
        
        // Build text of approximately 2500 characters to ensure multiple chunks
        while (sb.length() < 2500) {
            sb.append(sentence);
        }
        String testText = sb.toString();
        
        var chunks = textChunkingService.extractChunks(testText);
        
        // Should create multiple chunks since text > 1600 chars (decision threshold)
        assertThat(chunks.size()).isGreaterThan(1);
        
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            int chunkStart = chunk.getStartCharInChapter();
            int chunkEnd = chunk.getEndCharInChapter();
            int chunkLength = chunkEnd - chunkStart;
            
            // Chunks should be in the 400-1200 character range (min-max)
            assertThat(chunkLength)
                .as("Chunk %d length should be within configured bounds", i + 1)
                .isBetween(400, 1200);
                
            // For chunks that aren't the last one, verify overlap exists
            if (i < chunks.size() - 1) {
                var nextChunk = chunks.get(i + 1);
                int overlapAmount = chunkEnd - nextChunk.getStartCharInChapter();
                
                // Should have overlap (next chunk starts before current chunk ends)
                assertThat(overlapAmount)
                    .as("Chunks %d and %d should have overlap", i + 1, i + 2)
                    .isGreaterThan(0);
                    
                // Overlap should be approximately 25% of target size (200 chars)
                // Allow some variance due to sentence boundary alignment
                assertThat(overlapAmount)
                    .as("Overlap between chunks %d and %d should be around 200 characters", i + 1, i + 2)
                    .isBetween(100, 300); // Allow variance for sentence boundaries
            }
        }
        
        System.out.println("✅ Chunking test passed:");
        System.out.println("   Text length: " + testText.length() + " chars");
        System.out.println("   Number of chunks: " + chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            int chunkStart = chunk.getStartCharInChapter();
            int chunkEnd = chunk.getEndCharInChapter();
            int chunkLength = chunkEnd - chunkStart;
            System.out.println("   Chunk " + (i + 1) + ": " + chunkLength + " chars (pos " + 
                             chunkStart + "-" + chunkEnd + ")");
        }
    }

    @Test
    @DisplayName("should create single chunk for text under decision threshold")
    void shouldCreateSingleChunkForSmallText() {
        String smallText = "This is a short text that should result in a single chunk.";
        
        var chunks = textChunkingService.extractChunks(smallText);
        
        // Should create only one chunk since text < 1600 chars
        assertThat(chunks).hasSize(1);
        
        var chunk = chunks.get(0);
        assertThat(chunk.getStartCharInChapter()).isEqualTo(0);
        assertThat(chunk.getEndCharInChapter()).isEqualTo(smallText.length());
        assertThat(chunk.getText()).isEqualTo(smallText);
    }
}
