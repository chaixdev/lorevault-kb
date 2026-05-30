package com.lorevault.api.library.chunk;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.UUID;

public interface ChunkGraphRepository extends Neo4jRepository<Chunk, UUID> {

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_CHUNK]->(ch:Chunk) RETURN ch ORDER BY ch.chunkNumberInChapter")
    List<Chunk> findByChapterId(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_CHUNK]->(ch:Chunk) RETURN count(ch) > 0")
    boolean existsForChapter(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_CHUNK]->(ch:Chunk) RETURN count(ch)")
    int countByChapterId(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_CHUNK]->(ch:Chunk) DETACH DELETE ch")
    void deleteByChapterId(UUID chapterId);

    // Scene-based patterns (new scene->chunk model)
        @Query("""
                        MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene)-[r:HAS_CHUNK]->(ch:Chunk)
                        RETURN ch ORDER BY s.sceneIndex, r.chunkIndex
                        """)
    List<Chunk> findByChapterIdViaScenes(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(ch:Chunk) RETURN count(ch) > 0")
    boolean existsForChapterViaScenes(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(ch:Chunk) RETURN count(ch)")
    int countByChapterIdViaScenes(UUID chapterId);

    @Query("MATCH (:Scene {id: $sceneId})-[:HAS_CHUNK]->(ch:Chunk) RETURN ch ORDER BY ch.chunkNumberInChapter")
    List<Chunk> findBySceneId(UUID sceneId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(ch:Chunk) DETACH DELETE ch")
    void deleteByChapterIdViaScenes(UUID chapterId);

    // Embedding targeting queries
    @Query("""
            MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene)-[r:HAS_CHUNK]->(ch:Chunk)
            WHERE ch.embedding IS NULL OR ch.embeddingHash IS NULL
            RETURN ch ORDER BY s.sceneIndex, r.chunkIndex
            """)
    List<Chunk> findUnembeddedByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene)-[r:HAS_CHUNK]->(ch:Chunk)
            WHERE ch.embeddingHash <> $expectedHash
            RETURN ch ORDER BY s.sceneIndex, r.chunkIndex
            """)
    List<Chunk> findStaleEmbeddingsByChapterId(UUID chapterId, String expectedHash);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(ch:Chunk)
            WHERE ch.embedding IS NOT NULL
            RETURN count(ch)
            """)
    int countEmbeddingsByChapterId(UUID chapterId);

    // Global embedding queries for semantic search
    @Query("""
            MATCH (ch:Chunk)
            WHERE ch.embedding IS NOT NULL AND ch.embeddingHash IS NOT NULL
            RETURN ch
            """)
    List<Chunk> findAllWithEmbeddings();
}
