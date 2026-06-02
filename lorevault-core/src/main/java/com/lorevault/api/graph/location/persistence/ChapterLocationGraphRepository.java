package com.lorevault.api.graph.location.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface ChapterLocationGraphRepository extends Neo4jRepository<ChapterLocation, UUID> {

    @Query("""
            MATCH (m:LocationMention {chapterId: $chapterId})
            RETURN count(m)
            """)
    long countMentionsByChapterId(UUID chapterId);

    @Query("""
            MATCH (cl:ChapterLocation {chapterId: $chapterId})
            RETURN count(cl)
            """)
    long countChapterLocationsByChapterId(UUID chapterId);

    List<ChapterLocation> findByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter)-[:IN_BOOK]->(:Book {id: $bookId})
            MATCH (c)-[:HAS_LOCATION]->(cl:ChapterLocation)
            RETURN cl
            ORDER BY cl.normalizedName, cl.displayName
            """)
    List<ChapterLocation> findByBookId(UUID bookId);

    @Query("""
            MATCH (m:LocationMention {chapterId: $chapterId})
            OPTIONAL MATCH (m)-[r:REFERS_TO]->(:ChapterLocation {chapterId: $chapterId})
            DELETE r
            SET m.resolutionStatus = 'unresolved'
            WITH DISTINCT $chapterId AS chapterId
            OPTIONAL MATCH (cl:ChapterLocation {chapterId: chapterId})
            DETACH DELETE cl
            """)
    void deleteByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})
            WITH c
            MATCH (cl:ChapterLocation {id: $chapterLocationId})
            MERGE (c)-[:HAS_LOCATION]->(cl)
            """)
    void linkChapterToLocation(UUID chapterId, UUID chapterLocationId);

    @Query("""
            UNWIND $mentionIds AS mentionId
            MATCH (m:LocationMention {id: mentionId})
            WITH m
            MATCH (cl:ChapterLocation {id: $chapterLocationId})
            MERGE (m)-[:REFERS_TO]->(cl)
            SET m.resolutionStatus = $resolutionStatus
            """)
    void linkMentionsToChapterLocation(List<UUID> mentionIds, UUID chapterLocationId, String resolutionStatus);
}
