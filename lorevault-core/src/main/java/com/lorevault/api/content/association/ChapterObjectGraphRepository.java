package com.lorevault.api.content.association;

import java.util.List;
import java.util.UUID;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface ChapterObjectGraphRepository extends Neo4jRepository<ChapterObject, UUID> {

    @Query("""
            MATCH (m:ObjectMention {chapterId: $chapterId})
            RETURN count(m)
            """)
    long countMentionsByChapterId(UUID chapterId);

    @Query("""
            MATCH (co:ChapterObject {chapterId: $chapterId})
            RETURN count(co)
            """)
    long countChapterObjectsByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter)-[:IN_BOOK]->(:Book {id: $bookId})
            MATCH (c)-[:HAS_OBJECT]->(co:ChapterObject)
            RETURN co
            ORDER BY co.normalizedName, co.displayName, co.chapterId, co.id
            """)
    List<ChapterObject> findByBookId(UUID bookId);

    @Query("""
            MATCH (m:ObjectMention {chapterId: $chapterId})
            OPTIONAL MATCH (m)-[r:REFERS_TO]->(:ChapterObject {chapterId: $chapterId})
            DELETE r
            SET m.resolutionStatus = 'unresolved'
            WITH DISTINCT $chapterId AS chapterId
            OPTIONAL MATCH (co:ChapterObject {chapterId: chapterId})
            DETACH DELETE co
            """)
    void deleteByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})
            WITH c
            MATCH (co:ChapterObject {id: $chapterObjectId})
            MERGE (c)-[:HAS_OBJECT]->(co)
            """)
    void linkChapterToObject(UUID chapterId, UUID chapterObjectId);

    @Query("""
            UNWIND $mentionIds AS mentionId
            MATCH (m:ObjectMention {id: mentionId})
            WITH m
            MATCH (co:ChapterObject {id: $chapterObjectId})
            MERGE (m)-[:REFERS_TO]->(co)
            SET m.resolutionStatus = $resolutionStatus
            """)
    void linkMentionsToChapterObject(List<UUID> mentionIds, UUID chapterObjectId, String resolutionStatus);
}
