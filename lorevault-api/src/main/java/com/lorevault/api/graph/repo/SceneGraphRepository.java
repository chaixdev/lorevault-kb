package com.lorevault.api.graph.repo;

import com.lorevault.api.graph.model.SceneNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.UUID;

public interface SceneGraphRepository extends Neo4jRepository<SceneNode, UUID> {

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene) RETURN s ORDER BY s.sceneIndex")
    List<SceneNode> findByChapterId(UUID chapterId);

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene) DETACH DELETE s")
    void deleteByChapterId(UUID chapterId);
}
