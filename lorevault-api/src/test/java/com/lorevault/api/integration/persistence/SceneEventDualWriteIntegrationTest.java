package com.lorevault.api.integration.persistence;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for LV-083-1: Dual-write Scenes → Events on ingestion.
 * Verifies that scenes are persisted with :Event:Scene labels and proper relationships.
 */
@SpringBootTest
@Testcontainers
class SceneEventDualWriteIntegrationTest {

    @Container
    @SuppressWarnings("resource") // Testcontainers manages lifecycle
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.20")
            .withAdminPassword("testpassword");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "testpassword");
    }

    @Autowired
    private ContentPersistencePort contentPersistencePort;

    @Autowired
    private Neo4jClient neo4jClient;

    @AfterEach
    void cleanUp() {
        // Clear all nodes to ensure test isolation
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    @Test
    void addScenesToChapter_createsEventSceneDualLabelNodes() {
        // Given: A chapter with publication coordinates
        Chapter chapter = createTestChapter();
        Chapter savedChapter = contentPersistencePort.createChapter(chapter);

        // And: Two scenes with different offsets and context
        Scene scene1 = createTestScene(1, 0L, 150L, "Opening scene with character introduction");
        Scene scene2 = createTestScene(2, 150L, 300L, "Action sequence in the forest");

        // When: Adding scenes to the chapter via persistence port
        List<Scene> savedScenes = contentPersistencePort.addScenesToChapter(savedChapter.getId(), List.of(scene1, scene2));

        // Then: Scenes are persisted successfully
        assertThat(savedScenes).hasSize(2);
        assertThat(savedScenes.get(0).getId()).isNotNull();
        assertThat(savedScenes.get(1).getId()).isNotNull();

        // And: Scenes have dual labels :Scene:Event in the graph
        String dualLabelQuery = """
                MATCH (s:Scene:Event)
                RETURN count(s) as sceneEventCount
                """;
        Long sceneEventCount = neo4jClient.query(dualLabelQuery)
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("sceneEventCount").asLong())
                .one()
                .orElse(0L);
        assertThat(sceneEventCount).isEqualTo(2L);

        // And: Chapter HAS_SCENE relationships point to :Event:Scene nodes
        String chapterSceneQuery = """
                MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene:Event)
                RETURN count(s) as linkedScenes
                """;
        Long linkedScenes = neo4jClient.query(chapterSceneQuery)
                .bind(savedChapter.getId().toString()).to("chapterId")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("linkedScenes").asLong())
                .one()
                .orElse(0L);
        assertThat(linkedScenes).isEqualTo(2L);

        // And: Scene nodes have chapterId property set for timeline queries
        String chapterIdQuery = """
                MATCH (s:Scene:Event)
                WHERE s.chapterId IS NOT NULL
                RETURN count(s) as scenesWithChapterId
                """;
        Long scenesWithChapterId = neo4jClient.query(chapterIdQuery)
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("scenesWithChapterId").asLong())
                .one()
                .orElse(0L);
        assertThat(scenesWithChapterId).isEqualTo(2L);
    }

    @Test
    void addChunksToScene_linksChunksToEventSceneNodes() {
        // Given: A chapter with one scene
        Chapter chapter = createTestChapter();
        Chapter savedChapter = contentPersistencePort.createChapter(chapter);
        
        Scene scene = createTestScene(1, 0L, 200L, "Scene with multiple chunks");
        List<Scene> savedScenes = contentPersistencePort.addScenesToChapter(savedChapter.getId(), List.of(scene));
        UUID sceneId = savedScenes.get(0).getId();

        // And: Two chunks to add to the scene
        Chunk chunk1 = createTestChunk(1, 0, 100, "First chunk content");
        Chunk chunk2 = createTestChunk(2, 100, 200, "Second chunk content");

        // When: Adding chunks to the scene
        List<Chunk> savedChunks = contentPersistencePort.addChunksToScene(sceneId, List.of(chunk1, chunk2));

        // Then: Chunks are persisted successfully
        assertThat(savedChunks).hasSize(2);

        // And: HAS_CHUNK relationships link from :Event:Scene to chunks
        String sceneChunkQuery = """
                MATCH (s:Scene:Event {id: $sceneId})-[:HAS_CHUNK]->(ch:Chunk)
                RETURN count(ch) as linkedChunks
                """;
        Long linkedChunks = neo4jClient.query(sceneChunkQuery)
                .bind(sceneId.toString()).to("sceneId")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("linkedChunks").asLong())
                .one()
                .orElse(0L);
        assertThat(linkedChunks).isEqualTo(2L);

        // And: Chunks can be found via Chapter → Scene → Chunk traversal
        String traversalQuery = """
                MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene:Event)-[:HAS_CHUNK]->(ch:Chunk)
                RETURN count(ch) as traversalChunks
                """;
        Long traversalChunks = neo4jClient.query(traversalQuery)
                .bind(savedChapter.getId().toString()).to("chapterId")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("traversalChunks").asLong())
                .one()
                .orElse(0L);
        assertThat(traversalChunks).isEqualTo(2L);
    }

    @Test
    void findScenesByChapterId_returnsEventSceneNodes() {
        // Given: A chapter with scenes persisted as :Event:Scene
        Chapter chapter = createTestChapter();
        Chapter savedChapter = contentPersistencePort.createChapter(chapter);
        
        Scene scene1 = createTestScene(1, 0L, 100L, "First scene");
        Scene scene2 = createTestScene(2, 100L, 200L, "Second scene");
        contentPersistencePort.addScenesToChapter(savedChapter.getId(), List.of(scene1, scene2));

        // When: Finding scenes by chapter ID
        List<Scene> foundScenes = contentPersistencePort.findScenesByChapterId(savedChapter.getId());

        // Then: Scenes are found and properly mapped
        assertThat(foundScenes).hasSize(2);
        assertThat(foundScenes).extracting(Scene::getSceneIndex).containsExactly(1, 2);
        assertThat(foundScenes).extracting(Scene::getContextSummary)
                .containsExactly("First scene", "Second scene");

        // And: Each scene can be used as an Event (polymorphism verification)
        foundScenes.forEach(scene -> {
            assertThat(scene.getEventId()).isNotNull();
            assertThat(scene.getStartOffset()).isNotNull();
            assertThat(scene.getEndOffset()).isNotNull();
            assertThat(scene.getEndOffset()).isGreaterThan(scene.getStartOffset());
        });
    }

    private Chapter createTestChapter() {
        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setChapterTitle("Test Chapter for Event Scenes");
        chapter.setRawText("This is a test chapter with multiple scenes for dual-write testing. " +
                "The chapter contains enough text to have meaningful scene boundaries and chunk divisions.");
        chapter.setContentHash("test-hash-" + System.nanoTime());

        PublicationCoordinates coords = new PublicationCoordinates();
        coords.setUniverse("Test Universe");
        coords.setSeries("Test Series");
        coords.setBookTitle("Test Book");
        coords.setBookNumber(1);
        coords.setChapterNumber(1);
        coords.setChapterTitle(chapter.getChapterTitle());
        chapter.setCoordinates(coords);
        
        chapter.setCreatedAt(LocalDateTime.now());
        return chapter;
    }

    private Scene createTestScene(int index, Long startOffset, Long endOffset, String contextSummary) {
        Scene scene = new Scene();
        scene.setId(UUID.randomUUID());
        scene.setSceneIndex(index);
        scene.setStartCharacterOffset(startOffset);
        scene.setEndCharacterOffset(endOffset);
        scene.setContextSummary(contextSummary);
        scene.setText("Scene " + index + " text content");
        scene.setCreatedAt(LocalDateTime.now());
        return scene;
    }

    private Chunk createTestChunk(int chunkNumber, int startChar, int endChar, String text) {
        Chunk chunk = new Chunk();
        chunk.setId(UUID.randomUUID());
        chunk.setChunkNumberInChapter(chunkNumber);
        chunk.setStartCharInChapter(startChar);
        chunk.setEndCharInChapter(endChar);
        chunk.setText(text);
        chunk.setContentHash("chunk-hash-" + chunkNumber);
        return chunk;
    }
}