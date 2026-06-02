package com.lorevault.api.graph.event.scene;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SceneGraphRepository extends Neo4jRepository<Scene, UUID> {

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene) RETURN s ORDER BY s.sceneIndex")
    List<Scene> findByChapterId(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene) DETACH DELETE s")
    void deleteByChapterId(UUID chapterId);

    @Query("MATCH (a:Scene {id: $fromId})-[r:NEXT_IN_READING_ORDER]->(b:Scene {id: $toId}) RETURN count(r)")
    long countNextInReadingOrderBetween(UUID fromId, UUID toId);

    @Query("""
            MATCH (a:Scene {id: $fromId})
            WITH a
            MATCH (b:Scene {id: $toId})
            MERGE (a)-[:NEXT_IN_READING_ORDER]->(b)
            """)
    void createNextInReadingOrderBetween(UUID fromId, UUID toId);

    @Query("""
            MATCH (s:Scene {id: $sceneId})
            WITH s
            MATCH (ch:Chunk {id: $chunkId})
            MERGE (s)-[r:HAS_CHUNK]->(ch)
            SET r.chunkIndex = $chunkIndex
            """)
    void linkChunkToScene(UUID sceneId, UUID chunkId, Integer chunkIndex);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})
            WITH c
            MATCH (s:Scene {id: $sceneId})
            MERGE (c)-[:HAS_SCENE]->(s)
            """)
    void linkSceneToChapter(UUID chapterId, UUID sceneId);

    @Query("MATCH (prev:Scene)-[:NEXT_IN_READING_ORDER]->(:Scene {id: $sceneId}) RETURN prev.id")
    Optional<UUID> findPreviousSceneIdByReadingOrder(UUID sceneId);

    @Query("MATCH (:Scene {id: $sceneId})-[:NEXT_IN_READING_ORDER]->(next:Scene) RETURN next.id")
    Optional<UUID> findNextSceneIdByReadingOrder(UUID sceneId);

    /**
     * Idempotently create or match a Scene by chapterId + sceneIndex.
     * Prevents duplicate Scene nodes from concurrent insertions.
     */
    @Query("""
        MERGE (s:Scene {chapterId: $chapterId, sceneIndex: $sceneIndex})
        ON CREATE SET s.id = $id,
                      s.startOffset = $startOffset,
                      s.endOffset = $endOffset,
                      s.contextSummary = $contextSummary,
                      s.text = $text,
                      s.stageId = $stageId,
                      s.labels = $labels,
                      s.createdAt = $createdAt,
                      s.updatedAt = $updatedAt
        RETURN s
        """)
    Scene mergeScene(
            @Param("id") UUID id,
            @Param("chapterId") UUID chapterId,
            @Param("sceneIndex") Integer sceneIndex,
            @Param("startOffset") Long startOffset,
            @Param("endOffset") Long endOffset,
            @Param("contextSummary") String contextSummary,
            @Param("text") String text,
            @Param("stageId") UUID stageId,
            @Param("labels") List<String> labels,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
