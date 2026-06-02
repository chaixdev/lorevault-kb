package com.lorevault.api.graph.event.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface BookEventGraphRepository extends Neo4jRepository<BookEvent, UUID> {

    @Query("""
            MATCH (b:Book {id: $bookId})
            WITH b
            MATCH (be:BookEvent {id: $bookEventId})
            MERGE (b)-[:HAS_EVENT]->(be)
            """)
    void linkBookToEvent(UUID bookId, UUID bookEventId);

    /**
     * Delete REFERS_TO relationships from the given ChapterEvents to their BookEvents,
     * then detach-delete any BookEvents left orphaned (no remaining REFERS_TO inbound).
     */
    @Query("""
            UNWIND $chapterEventIds AS chapterEventId
            MATCH (ce:ChapterEvent {id: chapterEventId})-[r:REFERS_TO]->(be:BookEvent)
            DELETE r
            WITH DISTINCT be
            WHERE NOT EXISTS { MATCH (:ChapterEvent)-[:REFERS_TO]->(be) }
            DETACH DELETE be
            """)
    void clearLinksAndDeleteOrphanBookEvents(List<String> chapterEventIds);

    @Query("""
            MATCH (ce:ChapterEvent {chapterId: $chapterId})-[:REFERS_TO]->(be:BookEvent)
            RETURN count(be)
            """)
    long countByChapterId(UUID chapterId);
}
