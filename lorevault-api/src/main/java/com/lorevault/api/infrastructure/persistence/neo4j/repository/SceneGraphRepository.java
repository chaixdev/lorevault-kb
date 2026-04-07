package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.domain.content.Scene;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.UUID;

public interface SceneGraphRepository extends Neo4jRepository<Scene, UUID> {

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene) RETURN s ORDER BY s.sceneIndex")
    List<Scene> findByChapterId(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene) DETACH DELETE s")
    void deleteByChapterId(UUID chapterId);

    @Query("MATCH (a:Scene {id: $fromId})-[r:MEETS]->(b:Scene {id: $toId}) RETURN count(r)")
    long countMeetsBetween(UUID fromId, UUID toId);

    @Query("MATCH (a:Scene {id: $fromId}), (b:Scene {id: $toId}) MERGE (a)-[:MEETS]->(b)")
    void createMeetsBetween(UUID fromId, UUID toId);
}
