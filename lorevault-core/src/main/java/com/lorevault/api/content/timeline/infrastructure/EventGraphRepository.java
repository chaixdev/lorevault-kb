package com.lorevault.api.content.timeline.infrastructure;

import com.lorevault.api.content.scene.Scene;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.UUID;

/**
 * Read-only repository methods for event-modality queries over Scene nodes.
 */
public interface EventGraphRepository extends Neo4jRepository<Scene, UUID> {

    @Query("MATCH (s:Scene:Event) RETURN s ORDER BY s.sceneIndex")
    List<Scene> findAllSceneEvents();

    @Query("MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene:Event) RETURN s ORDER BY s.sceneIndex")
    List<Scene> findSceneEventsByChapter(UUID chapterId);
}
