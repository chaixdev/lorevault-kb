package com.lorevault.api.graph.collective.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface ChapterCollectiveGraphRepository extends Neo4jRepository<ChapterCollective, UUID> {

    @Query("""
            MATCH (m:CollectiveMention {chapterId: $chapterId})
            RETURN count(m)
            """)
    long countMentionsByChapterId(UUID chapterId);

    @Query("""
            MATCH (cc:ChapterCollective {chapterId: $chapterId})
            RETURN count(cc)
            """)
    long countChapterCollectivesByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter)-[:IN_BOOK]->(:Book {id: $bookId})
            MATCH (c)-[:HAS_COLLECTIVE]->(cc:ChapterCollective)
            RETURN cc
            ORDER BY cc.normalizedName, cc.displayName, cc.chapterId, cc.id
            """)
    List<ChapterCollective> findByBookId(UUID bookId);

    @Query("""
            MATCH (m:CollectiveMention {chapterId: $chapterId})
            OPTIONAL MATCH (m)-[r:REFERS_TO]->(:ChapterCollective {chapterId: $chapterId})
            DELETE r
            SET m.resolutionStatus = 'unresolved'
            WITH DISTINCT $chapterId AS chapterId
            OPTIONAL MATCH (cc:ChapterCollective {chapterId: chapterId})
            DETACH DELETE cc
            """)
    void deleteByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})
            WITH c
            MATCH (cc:ChapterCollective {id: $chapterCollectiveId})
            MERGE (c)-[:HAS_COLLECTIVE]->(cc)
            """)
    void linkChapterToCollective(UUID chapterId, UUID chapterCollectiveId);

    @Query("""
            UNWIND $mentionIds AS mentionId
            MATCH (m:CollectiveMention {id: mentionId})
            WITH m
            MATCH (cc:ChapterCollective {id: $chapterCollectiveId})
            MERGE (m)-[:REFERS_TO]->(cc)
            SET m.resolutionStatus = $resolutionStatus
            """)
    void linkMentionsToChapterCollective(List<UUID> mentionIds, UUID chapterCollectiveId, String resolutionStatus);
}
