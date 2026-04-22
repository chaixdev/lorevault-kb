package com.lorevault.api.content.infrastructure;

import com.lorevault.api.content.domain.Scene;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.UUID;

public interface SceneGraphRepository extends Neo4jRepository<Scene, UUID> {

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene) RETURN s ORDER BY s.sceneIndex")
    List<Scene> findByChapterId(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene) DETACH DELETE s")
    void deleteByChapterId(UUID chapterId);

    @Deprecated(since = "April 2026", forRemoval = false)
    @Query("MATCH (a:Scene {id: $fromId})-[r:NEXT_IN_READING_ORDER]->(b:Scene {id: $toId}) RETURN count(r)")
    long countMeetsBetween(UUID fromId, UUID toId);

    @Deprecated(since = "April 2026", forRemoval = false)
    @Query("""
            MATCH (a:Scene {id: $fromId})
            WITH a
            MATCH (b:Scene {id: $toId})
            MERGE (a)-[:NEXT_IN_READING_ORDER]->(b)
            """)
    void createMeetsBetween(UUID fromId, UUID toId);

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
}
