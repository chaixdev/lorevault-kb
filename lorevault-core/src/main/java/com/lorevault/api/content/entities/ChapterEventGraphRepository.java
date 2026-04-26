package com.lorevault.api.content.entities;

import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface ChapterEventGraphRepository extends Neo4jRepository<ChapterEvent, UUID> {

    @Query("""
            MATCH (m:EventMention {chapterId: $chapterId})
            RETURN count(m)
            """)
    long countMentionsByChapterId(UUID chapterId);

    @Query("""
            MATCH (ce:ChapterEvent {chapterId: $chapterId})
            RETURN count(ce)
            """)
    long countChapterEventsByChapterId(UUID chapterId);

    /**
     * Delete all ChapterEvent nodes for a chapter and their inbound REFERS_TO edges.
     * Resets mention resolutionStatus to 'unresolved' so the resolution pass is idempotent.
     */
    @Query("""
            MATCH (m:EventMention {chapterId: $chapterId})
            OPTIONAL MATCH (m)-[r:REFERS_TO]->(:ChapterEvent {chapterId: $chapterId})
            DELETE r
            SET m.resolutionStatus = 'unresolved'
            WITH DISTINCT $chapterId AS chapterId
            OPTIONAL MATCH (ce:ChapterEvent {chapterId: chapterId})
            DETACH DELETE ce
            """)
    void deleteByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})
            WITH c
            MATCH (ce:ChapterEvent {id: $chapterEventId})
            MERGE (c)-[:HAS_EVENT]->(ce)
            """)
    void linkChapterToEvent(UUID chapterId, UUID chapterEventId);

    /**
     * Link a specific EventMention to a ChapterEvent by mention id.
     * Used by Stage 3 after connected-component aggregation.
     */
    @Query("""
            MATCH (m:EventMention {id: $mentionId})
            WITH m
            MATCH (ce:ChapterEvent {id: $chapterEventId})
            MERGE (m)-[:REFERS_TO]->(ce)
            SET m.resolutionStatus = $resolutionStatus
            """)
    void linkMentionToChapterEvent(UUID mentionId, UUID chapterEventId, String resolutionStatus);
}
