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
}
