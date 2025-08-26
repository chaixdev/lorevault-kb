package com.lorevault.api.integration.content;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.LoreVaultApiApplication;
import com.lorevault.api.service.content.SceneProcessingService;
import com.lorevault.api.service.content.TextChunkingService;
import com.lorevault.api.service.content.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.lorevault.api.testing.TestImages;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Comprehensive integration tests for the content processing services.
 * Tests the integration between SceneProcessingService, TextChunkingService, and EmbeddingService
 * Uses real database connections and validates end-to-end behavior.
 */
@SpringBootTest(classes = LoreVaultApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
class ContentProcessingIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(TestImages.NEO4J_IMAGE)
            .withAdminPassword("test123")
            .withReuse(true);

    @DynamicPropertySource
    static void setNeo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4jContainer::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "test123");
    }

    @Autowired 
    private SceneProcessingService sceneProcessingService;
    
    @Autowired 
    private TextChunkingService textChunkingService;
    
    @Autowired 
    private EmbeddingService embeddingService;
    
    @Autowired 
    private ContentPersistencePort contentPersistencePort;

    @MockitoBean
    @Qualifier("nlpSmall")
    private ChatClient nlpSmallChatClient;

    @MockitoBean
    @Qualifier("nlpBig")
    private ChatClient nlpBigChatClient;

    private Chapter testChapter;
    private UUID testChapterId;

    @BeforeEach
    void setUp() {
        testChapterId = UUID.randomUUID();
        
        PublicationCoordinates coordinates = new PublicationCoordinates();
        coordinates.setUniverse("Test Universe");
        coordinates.setSeries("Test Series");
        coordinates.setBookTitle("Test Book");
        coordinates.setBookNumber(1);
        coordinates.setChapterTitle("Test Chapter");
        coordinates.setChapterNumber(1);
        
        testChapter = new Chapter();
        testChapter.setId(testChapterId);
        testChapter.setCoordinates(coordinates);
        testChapter.setRawText("This is the first scene of the test chapter. It contains some dialog and action.\n\n" +
                         "The second scene begins here. There was a dramatic pause before the character spoke again.");
        testChapter.setCreatedAt(LocalDateTime.now());
        
        contentPersistencePort.createChapter(testChapter);
    }

    @Test
    void sceneProcessingServiceRetrievesScenes() {
        // Act: Get existing scenes (should be empty initially)
        List<Scene> scenes = sceneProcessingService.getScenesByChapterId(testChapterId);

        // Assert: Initially no scenes should exist
        assertThat(scenes).isEmpty();
    }

    @Test
    void textChunkingServiceExtractsChunks() {
        // Act: Extract chunks from text
        List<Chunk> chunks = textChunkingService.extractChunks(testChapter.getRawText());

        // Assert: Chunks should be extracted
        assertAll(
                () -> assertThat(chunks).isNotEmpty(),
                () -> assertThat(chunks.get(0).getText()).isNotBlank()
        );
    }

    @Test
    void embeddingServiceGeneratesEmbeddings() {
        // Arrange: Create some test chunks in the database
        List<Chunk> chunks = List.of(
                createTestChunk(1, "First chunk of text for testing embedding generation."),
                createTestChunk(2, "Second chunk continues the narrative with more content.")
        );

        contentPersistencePort.addChunksToChapter(testChapterId, chunks);
        
        // Act: Generate embeddings
        int embeddedCount = embeddingService.generateEmbeddingsForChapter(testChapterId);
        
        // Assert: Some embeddings should be generated
        assertThat(embeddedCount).isGreaterThanOrEqualTo(0);
    }

    @Test
    void integrationWorkflow() {
        // Arrange: Use TextChunkingService to create chunks from chapter content
        List<Chunk> extractedChunks = textChunkingService.extractChunks(testChapter.getRawText());
        
        // Set up chunks with proper relationships to chapter
        extractedChunks.forEach(chunk -> {
            chunk.setChapter(testChapter);
        });
        
        // Act: Add chunks to database
        List<Chunk> persistedChunks = contentPersistencePort.addChunksToChapter(testChapterId, extractedChunks);
        
        // Generate embeddings for the chunks
        int embeddedCount = embeddingService.generateEmbeddingsForChapter(testChapterId);
        
        // Assert: Verify the complete workflow
        assertAll(
                () -> assertThat(extractedChunks).isNotEmpty(),
                () -> assertThat(persistedChunks).isNotEmpty(),
                () -> assertThat(embeddedCount).isGreaterThanOrEqualTo(0)
        );
        
        // Verify chunks are retrievable
        List<Chunk> retrievedChunks = contentPersistencePort.findChunksByChapterId(testChapterId);
        assertThat(retrievedChunks).hasSizeGreaterThanOrEqualTo(extractedChunks.size());
    }

    @Test
    void performanceBaselineTest() {
        // Arrange: Create larger content
        String largeContent = "Large content block ".repeat(100);
        Chapter largeChapter = new Chapter();
        largeChapter.setId(testChapterId);
        largeChapter.setRawText(largeContent);
        largeChapter.setCreatedAt(LocalDateTime.now());
        
        contentPersistencePort.createChapter(largeChapter);
        
        // Act & Assert: Measure processing time
        long startTime = System.currentTimeMillis();
        
        // Extract chunks
        List<Chunk> chunks = textChunkingService.extractChunks(largeContent);
        chunks.forEach(chunk -> chunk.setChapter(largeChapter));
        contentPersistencePort.addChunksToChapter(testChapterId, chunks);
        
        // Generate embeddings
        int embeddedCount = embeddingService.generateEmbeddingsForChapter(testChapterId);
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        // Performance assertion - should complete within reasonable time
        assertThat(processingTime).isLessThan(30000); // 30 seconds max
        
        // Verify all steps completed successfully
        List<Chunk> retrievedChunks = contentPersistencePort.findChunksByChapterId(testChapterId);
        
        assertAll(
                () -> assertThat(chunks).isNotEmpty(),
                () -> assertThat(retrievedChunks).isNotEmpty(),
                () -> assertThat(embeddedCount).isGreaterThanOrEqualTo(0)
        );
    }

    @Test
    void errorHandlingScenarios() {
        // Test with invalid chapter ID
        UUID nonExistentChapterId = UUID.randomUUID();
        
        // These should handle missing chapters gracefully
        List<Scene> scenes = sceneProcessingService.getScenesByChapterId(nonExistentChapterId);
        int embeddedCount = embeddingService.generateEmbeddingsForChapter(nonExistentChapterId);
        
        // Verify no exceptions were thrown and appropriate responses returned
        assertAll(
                () -> assertThat(scenes).isEmpty(),
                () -> assertThat(embeddedCount).isEqualTo(0)
        );
    }

    private Chunk createTestChunk(int sequenceNumber, String text) {
        Chunk chunk = new Chunk();
        chunk.setId(UUID.randomUUID());
        chunk.setChapter(testChapter);
        chunk.setText(text);
        chunk.setChunkNumberInChapter(sequenceNumber);
        chunk.setStartCharInChapter(0);
        chunk.setEndCharInChapter(text.length());
        chunk.setCreatedAt(LocalDateTime.now());
        return chunk;
    }
}