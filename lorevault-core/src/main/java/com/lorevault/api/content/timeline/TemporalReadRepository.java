package com.lorevault.api.content.timeline;

import com.lorevault.api.content.entities.Scene;
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

    /**
     * Return (fromId, toId) pairs for precedence edges between scene events in all chapters
     * up to and including the requested chapter number for a book.
     */
    @Query(
        """
        MATCH (b:Book {id: $bookId})
        MATCH (c:Chapter)-[:IN_BOOK]->(b)
        WHERE c.chapterNumber <= $uptoChapterNumber
        MATCH (c)-[:HAS_SCENE]->(s:Scene:Event)
        WITH collect(DISTINCT s) AS scopedScenes
        UNWIND scopedScenes AS s1
        MATCH (s1)-[:TEMPORAL]->(s2:Scene:Event)
        WHERE s2 IN scopedScenes
        RETURN DISTINCT s1.id AS fromId, s2.id AS toId
        """
    )
    List<TemporalEdgePair> findBookEventEdgesUpToChapter(UUID bookId, int uptoChapterNumber);

    interface TemporalEdgePair {
        UUID getFromId();
        UUID getToId();
    }
}
