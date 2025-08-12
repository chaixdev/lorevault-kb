package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.UUID;

public interface ChunkGraphRepository extends Neo4jRepository<ChunkNode, UUID> {

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_CHUNK]->(ch:Chunk) RETURN ch ORDER BY ch.chunkNumberInChapter")
    List<ChunkNode> findByChapterId(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_CHUNK]->(ch:Chunk) RETURN count(ch) > 0")
    boolean existsForChapter(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_CHUNK]->(ch:Chunk) RETURN count(ch)")
    int countByChapterId(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_CHUNK]->(ch:Chunk) DETACH DELETE ch")
    void deleteByChapterId(UUID chapterId);

    // Scene-based patterns (new scene->chunk model)
    @Query("""
            MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(ch:Chunk)
            RETURN ch ORDER BY ch.chunkNumberInChapter
            """)
    List<ChunkNode> findByChapterIdViaScenes(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(ch:Chunk) RETURN count(ch) > 0")
    boolean existsForChapterViaScenes(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(ch:Chunk) RETURN count(ch)")
    int countByChapterIdViaScenes(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(ch:Chunk) DETACH DELETE ch")
    void deleteByChapterIdViaScenes(UUID chapterId);

    // Embedding targeting queries
    @Query("""
            MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(ch:Chunk)
            WHERE ch.embedding IS NULL OR ch.embeddingHash IS NULL
            RETURN ch ORDER BY ch.chunkNumberInChapter
            """)
    List<ChunkNode> findUnembeddedByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(:Scene)-[:HAS_CHUNK]->(ch:Chunk)
            WHERE ch.embeddingHash <> $expectedHash
            RETURN ch ORDER BY ch.chunkNumberInChapter
            """)
    List<ChunkNode> findStaleEmbeddingsByChapterId(UUID chapterId, String expectedHash);
}
