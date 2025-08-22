package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.UUID;

/**
 * Minimal repository for traversing temporal links between event scenes.
 */
public interface TemporalGraphRepository extends Neo4jRepository<SceneNode, UUID> {

    @Query("MATCH (e:Scene:Event {id: $eventId})-[:TEMPORAL]->(later:Scene:Event) RETURN later ORDER BY later.sceneIndex")
    List<SceneNode> findLaterEvents(UUID eventId);

    @Query("MATCH (earlier:Scene:Event)-[:TEMPORAL]->(e:Scene:Event {id: $eventId}) RETURN earlier ORDER BY earlier.sceneIndex")
    List<SceneNode> findEarlierEvents(UUID eventId);
}
