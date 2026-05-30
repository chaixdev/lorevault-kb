package com.lorevault.api.graph.individual.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface ChapterIndividualGraphRepository extends Neo4jRepository<ChapterIndividual, UUID> {

    @Query("""
            MATCH (m:IndividualMention {chapterId: $chapterId})
            RETURN count(m)
            """)
    long countMentionsByChapterId(UUID chapterId);

    @Query("""
            MATCH (ci:ChapterIndividual {chapterId: $chapterId})
            RETURN count(ci)
            """)
    long countChapterIndividualsByChapterId(UUID chapterId);

    @Query("""
            MATCH (m:IndividualMention {chapterId: $chapterId})
            OPTIONAL MATCH (m)-[r:REFERS_TO]->(:ChapterIndividual {chapterId: $chapterId})
            DELETE r
            SET m.resolutionStatus = 'unresolved'
            WITH DISTINCT $chapterId AS chapterId
            OPTIONAL MATCH (ci:ChapterIndividual {chapterId: chapterId})
            DETACH DELETE ci
            """)
    void deleteByChapterId(UUID chapterId);

    List<ChapterIndividual> findByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter)-[:IN_BOOK]->(b:Book {id: $bookId})
            MATCH (c)-[:HAS_INDIVIDUAL]->(ci:ChapterIndividual)
            RETURN ci
            """)
    List<ChapterIndividual> findByBookId(UUID bookId);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})
            WITH c
            MATCH (ci:ChapterIndividual {id: $chapterIndividualId})
            MERGE (c)-[:HAS_INDIVIDUAL]->(ci)
            """)
    void linkChapterToIndividual(UUID chapterId, UUID chapterIndividualId);

    @Query("""
            MATCH (m:IndividualMention {id: $mentionId})
            WITH m
            MATCH (ci:ChapterIndividual {id: $chapterIndividualId})
            MERGE (m)-[:REFERS_TO]->(ci)
            SET m.resolutionStatus = $resolutionStatus
            """)
    void linkMentionToChapterIndividual(UUID mentionId, UUID chapterIndividualId, String resolutionStatus);
}
