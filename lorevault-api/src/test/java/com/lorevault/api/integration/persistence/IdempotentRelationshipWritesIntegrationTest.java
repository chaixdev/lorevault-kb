package com.lorevault.api.integration.persistence;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.testing.TestImages;
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
 * Must-have safety tests for an event-driven, retry-prone pipeline:
 * relationship writes must be idempotent and null-safe.
 */
@SpringBootTest
@Testcontainers
class IdempotentRelationshipWritesIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>(TestImages.NEO4J_IMAGE)
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
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }

    @Test
    void addScenesToChapter_whenCalledTwiceWithSameSceneIds_doesNotDuplicateHasSceneRelationships() {
        Chapter chapter = createTestChapter();
        Chapter savedChapter = contentPersistencePort.createChapter(chapter);

        UUID sceneId1 = UUID.randomUUID();
        UUID sceneId2 = UUID.randomUUID();
        Scene scene1 = createTestScene(sceneId1, 1, 0L, 50L, "Scene 1");
        Scene scene2 = createTestScene(sceneId2, 2, 50L, 100L, "Scene 2");

        contentPersistencePort.addScenesToChapter(savedChapter.getId(), List.of(scene1, scene2));
        contentPersistencePort.addScenesToChapter(savedChapter.getId(), List.of(scene1, scene2));

        Long linkedScenes = neo4jClient.query("""
                        MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene:Event)
                        RETURN count(s) as linkedScenes
                        """)
                .bind(savedChapter.getId().toString()).to("chapterId")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("linkedScenes").asLong())
                .one()
                .orElse(0L);

        assertThat(linkedScenes).isEqualTo(2L);

        Long uniqueSceneNodes = neo4jClient.query("""
                        MATCH (s:Scene:Event)
                        WHERE s.id IN [$id1, $id2]
                        RETURN count(s) as c
                        """)
                .bind(sceneId1.toString()).to("id1")
                .bind(sceneId2.toString()).to("id2")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("c").asLong())
                .one()
                .orElse(0L);

        assertThat(uniqueSceneNodes).isEqualTo(2L);
    }

    @Test
    void addChunksToChapter_whenCalledTwiceWithSameChunkIds_doesNotDuplicateHasChunkRelationships_andAllowsNullChunkNumber() {
        Chapter chapter = createTestChapter();
        Chapter savedChapter = contentPersistencePort.createChapter(chapter);

        Chunk chunk1 = createTestChunk(UUID.randomUUID(), 1, 0, 10, "chunk-1");
        Chunk chunk2 = createTestChunk(UUID.randomUUID(), null, 10, 20, "chunk-2-null-number");

        contentPersistencePort.addChunksToChapter(savedChapter.getId(), List.of(chunk1, chunk2));
        contentPersistencePort.addChunksToChapter(savedChapter.getId(), List.of(chunk1, chunk2));

        Long linkedChunks = neo4jClient.query("""
                        MATCH (c:Chapter {id: $chapterId})-[:HAS_CHUNK]->(ch:Chunk)
                        RETURN count(ch) as linkedChunks
                        """)
                .bind(savedChapter.getId().toString()).to("chapterId")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("linkedChunks").asLong())
                .one()
                .orElse(0L);

        assertThat(linkedChunks).isEqualTo(2L);
    }

    @Test
    void addChunkToScene_whenChunkNumberNull_doesNotSetChunkIndex_andIsIdempotent() {
        Chapter chapter = createTestChapter();
        Chapter savedChapter = contentPersistencePort.createChapter(chapter);

        Scene scene = createTestScene(UUID.randomUUID(), 1, 0L, 100L, "Scene with chunks");
        UUID sceneId = contentPersistencePort.addScenesToChapter(savedChapter.getId(), List.of(scene)).get(0).getId();

        UUID chunkId = UUID.randomUUID();
        Chunk chunk = createTestChunk(chunkId, null, 0, 10, "chunk-null-number");

        contentPersistencePort.addChunkToScene(sceneId, chunk);
        contentPersistencePort.addChunkToScene(sceneId, chunk);

        Long linkedChunks = neo4jClient.query("""
                        MATCH (s:Scene:Event {id: $sceneId})-[:HAS_CHUNK]->(ch:Chunk)
                        RETURN count(ch) as linkedChunks
                        """)
                .bind(sceneId.toString()).to("sceneId")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("linkedChunks").asLong())
                .one()
                .orElse(0L);

        assertThat(linkedChunks).isEqualTo(1L);

        Integer chunkIndex = neo4jClient.query("""
                        MATCH (s:Scene:Event {id: $sceneId})-[r:HAS_CHUNK]->(ch:Chunk {id: $chunkId})
                        RETURN r.chunkIndex as chunkIndex
                        """)
                .bind(sceneId.toString()).to("sceneId")
                .bind(chunkId.toString()).to("chunkId")
                .fetchAs(Integer.class)
                .mappedBy((typeSystem, record) -> record.get("chunkIndex").isNull() ? null : record.get("chunkIndex").asInt())
                .one()
                .orElse(null);

        assertThat(chunkIndex).isNull();
    }

    private Chapter createTestChapter() {
        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setChapterTitle("Idempotency Test Chapter");
        chapter.setRawText("Some chapter text for idempotency tests.");
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

    private Scene createTestScene(UUID id, int index, Long startOffset, Long endOffset, String contextSummary) {
        Scene scene = new Scene();
        scene.setId(id);
        scene.setSceneIndex(index);
        scene.setStartCharacterOffset(startOffset);
        scene.setEndCharacterOffset(endOffset);
        scene.setContextSummary(contextSummary);
        scene.setText("Scene " + index + " text");
        scene.setCreatedAt(LocalDateTime.now());
        return scene;
    }

    private Chunk createTestChunk(UUID id, Integer chunkNumberInChapter, int startChar, int endChar, String text) {
        Chunk chunk = new Chunk();
        chunk.setId(id);
        chunk.setChunkNumberInChapter(chunkNumberInChapter);
        chunk.setStartCharInChapter(startChar);
        chunk.setEndCharInChapter(endChar);
        chunk.setText(text);
        chunk.setContentHash("chunk-hash-" + System.nanoTime());
        return chunk;
    }
}
