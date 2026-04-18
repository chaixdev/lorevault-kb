package com.lorevault.api.timeline;

import com.lorevault.api.content.Scene;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Read-only repository for retrieving precedence edges between events.
 * Only [:TEMPORAL] edges are treated as temporal precedence constraints.
 */
public interface TemporalReadRepository extends Repository<Scene, UUID> {

    /**
     * Return (fromId, toId) pairs for precedence edges within a chapter.
     * Only considers edges where both endpoints are scene events in the same chapter.
     */
    @Query(
        """
        MATCH (c:Chapter {id: $chapterId})
        MATCH (c)-[:HAS_SCENE]->(s1:Scene:Event)
        MATCH (c)-[:HAS_SCENE]->(s2:Scene:Event)
        MATCH (s1)-[:TEMPORAL]->(s2)
        RETURN s1.id AS fromId, s2.id AS toId
        """
    )
    List<TemporalEdgePair> findChapterEventEdges(UUID chapterId);

    interface TemporalEdgePair {
        UUID getFromId();
        UUID getToId();
    }
}
