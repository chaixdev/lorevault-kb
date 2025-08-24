package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Read-only repository for retrieving precedence edges between events.
 * We treat [:MEETS] and [:TEMPORAL] as precedence constraints (earlier -> later).
 */
public interface TemporalReadRepository extends Repository<SceneNode, UUID> {

    /**
     * Return (fromId, toId) pairs for precedence edges within a chapter.
     * Only considers edges where both endpoints are scene events in the same chapter.
     */
    @Query(
        """
        MATCH (c:Chapter {id: $chapterId})
        MATCH (c)-[:HAS_SCENE]->(s1:Scene:Event)
        MATCH (c)-[:HAS_SCENE]->(s2:Scene:Event)
        MATCH (s1)-[:MEETS|TEMPORAL]->(s2)
        RETURN s1.id AS fromId, s2.id AS toId
        """
    )
    List<TemporalEdgePair> findChapterEventEdges(UUID chapterId);

    interface TemporalEdgePair {
        UUID getFromId();
        UUID getToId();
    }
}
