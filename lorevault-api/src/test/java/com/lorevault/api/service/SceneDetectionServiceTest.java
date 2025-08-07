package com.lorevault.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SceneDetectionService focusing on coordinate localization logic.
 * Tests the Stage 2 implementation (snippet pattern matching to character coordinates).
 * 
 * This test focuses on the core pattern matching logic without external dependencies.
 */
class SceneDetectionServiceTest {

    private SceneDetectionService sceneDetectionService;
    private String sampleChapterText;
    
    @BeforeEach
    void setUp() {
        // Sample chapter text with clear scene boundaries
        sampleChapterText = """
            The morning sun filtered through the curtains as Sarah woke up in her bedroom. 
            She stretched and yawned, looking at the clock on her nightstand. It was already 8 AM.
            
            An hour later, Sarah walked into the bustling coffee shop downtown. The aroma of 
            freshly ground beans filled the air as she waited in line. The barista smiled 
            warmly as she approached the counter.
            
            That evening, Sarah sat in her car outside the old library. The rain drummed 
            against the windshield as she gathered her courage. This was where it all began, 
            twenty years ago.
            """;
            
        // Create a SceneDetectionService instance with null dependencies for isolated testing
        sceneDetectionService = new SceneDetectionService(null, null, new ObjectMapper(), null);
    }
    
    @Test
    void findSnippetPosition_ShouldFindStartSnippetAtBeginning() {
        try {
            var method = SceneDetectionService.class.getDeclaredMethod("findSnippetPosition", 
                String.class, String.class, boolean.class);
            method.setAccessible(true);
            
            // Test start snippet at the very beginning
            long startPos = (Long) method.invoke(sceneDetectionService, 
                sampleChapterText, "The morning sun", true);
            assertThat(startPos).isEqualTo(0L);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to test start snippet position", e);
        }
    }
    
    @Test
    void findSnippetPosition_ShouldFindEndSnippetWithLength() {
        try {
            var method = SceneDetectionService.class.getDeclaredMethod("findSnippetPosition", 
                String.class, String.class, boolean.class);
            method.setAccessible(true);
            
            // Test end snippet - should return position + length
            String endSnippet = "8 AM.";
            long endPos = (Long) method.invoke(sceneDetectionService, 
                sampleChapterText, endSnippet, false);
            
            // Should be greater than 0 and include the snippet length
            assertThat(endPos).isGreaterThan(endSnippet.length());
            
            // Verify we can extract text up to this position
            String extracted = sampleChapterText.substring(0, (int) endPos);
            assertThat(extracted).endsWith(endSnippet);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to test end snippet position", e);
        }
    }
    
    @Test
    void findSnippetPosition_ShouldFindMiddleSnippets() {
        try {
            var method = SceneDetectionService.class.getDeclaredMethod("findSnippetPosition", 
                String.class, String.class, boolean.class);
            method.setAccessible(true);
            
            // Test finding a snippet in the middle of the text
            String middleSnippet = "coffee shop downtown";
            long pos = (Long) method.invoke(sceneDetectionService, 
                sampleChapterText, middleSnippet, true);
            
            assertThat(pos).isGreaterThan(0L);
            
            // Verify the snippet is actually at that position
            String extracted = sampleChapterText.substring((int) pos, (int) pos + middleSnippet.length());
            assertThat(extracted).isEqualTo(middleSnippet);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to test middle snippet position", e);
        }
    }
    
    @Test
    void findSnippetPosition_ShouldHandleNonExistentSnippet() {
        try {
            var method = SceneDetectionService.class.getDeclaredMethod("findSnippetPosition", 
                String.class, String.class, boolean.class);
            method.setAccessible(true);
            
            // Test non-existent snippet
            long notFound = (Long) method.invoke(sceneDetectionService, 
                sampleChapterText, "This text does not exist anywhere", true);
            assertThat(notFound).isEqualTo(-1L);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to test non-existent snippet", e);
        }
    }
    
    @Test
    void findSnippetPosition_ShouldHandleNullAndEmptySnippets() {
        try {
            var method = SceneDetectionService.class.getDeclaredMethod("findSnippetPosition", 
                String.class, String.class, boolean.class);
            method.setAccessible(true);
            
            // Test null snippet
            long nullResult = (Long) method.invoke(sceneDetectionService, 
                sampleChapterText, null, true);
            assertThat(nullResult).isEqualTo(-1L);
            
            // Test empty snippet
            long emptyResult = (Long) method.invoke(sceneDetectionService, 
                sampleChapterText, "", true);
            assertThat(emptyResult).isEqualTo(-1L);
            
            // Test whitespace-only snippet
            long whitespaceResult = (Long) method.invoke(sceneDetectionService, 
                sampleChapterText, "   ", true);
            assertThat(whitespaceResult).isEqualTo(-1L);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to test edge cases", e);
        }
    }
    
    @Test
    void findSnippetPosition_ShouldDistinguishStartVsEndSnippets() {
        try {
            var method = SceneDetectionService.class.getDeclaredMethod("findSnippetPosition", 
                String.class, String.class, boolean.class);
            method.setAccessible(true);
            
            // Use a snippet that appears multiple times - first and last occurrence should differ
            String repeatedSnippet = "Sarah";
            
            long firstOccurrence = (Long) method.invoke(sceneDetectionService, 
                sampleChapterText, repeatedSnippet, true);
            long lastOccurrence = (Long) method.invoke(sceneDetectionService, 
                sampleChapterText, repeatedSnippet, false);
            
            // For start snippets, we get the position
            // For end snippets, we get position + length 
            assertThat(firstOccurrence).isGreaterThanOrEqualTo(0L);
            assertThat(lastOccurrence).isGreaterThan(firstOccurrence);
            
            // Verify the first occurrence
            String firstExtracted = sampleChapterText.substring((int) firstOccurrence, (int) firstOccurrence + repeatedSnippet.length());
            assertThat(firstExtracted).isEqualTo(repeatedSnippet);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to test start vs end snippet logic", e);
        }
    }
    
    @Test
    void sceneWithCoordinates_ShouldHaveCorrectStructure() {
        // Test that we can create SceneWithCoordinates objects (public record)
        var scene = new SceneDetectionService.SceneWithCoordinates(
            0, 100L, 200L, "Test scene"
        );
        
        assertThat(scene.sceneIndex()).isEqualTo(0);
        assertThat(scene.startCharacterOffset()).isEqualTo(100L);
        assertThat(scene.endCharacterOffset()).isEqualTo(200L);
        assertThat(scene.contextSummary()).isEqualTo("Test scene");
    }
}
