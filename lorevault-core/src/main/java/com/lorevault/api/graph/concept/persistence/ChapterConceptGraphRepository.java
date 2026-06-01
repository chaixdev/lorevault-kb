package com.lorevault.api.graph.concept.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface ChapterConceptGraphRepository extends Neo4jRepository<ChapterConcept, UUID> {

    @Query("""
            MATCH (m:ConceptMention {chapterId: $chapterId})
            RETURN count(m)
            """)
    long countMentionsByChapterId(UUID chapterId);

    @Query("""
            MATCH (cc:ChapterConcept {chapterId: $chapterId})
            RETURN count(cc)
            """)
    long countChapterConceptsByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter)-[:IN_BOOK]->(:Book {id: $bookId})
            MATCH (c)-[:HAS_CONCEPT]->(cc:ChapterConcept)
            RETURN cc
            ORDER BY cc.normalizedName, cc.displayName, cc.chapterId, cc.id
            """)
    List<ChapterConcept> findByBookId(UUID bookId);

    @Query("""
            MATCH (m:ConceptMention {chapterId: $chapterId})
            OPTIONAL MATCH (m)-[r:REFERS_TO]->(:ChapterConcept {chapterId: $chapterId})
            DELETE r
            SET m.resolutionStatus = 'unresolved'
            WITH DISTINCT $chapterId AS chapterId
            OPTIONAL MATCH (cc:ChapterConcept {chapterId: chapterId})
            DETACH DELETE cc
            """)
    void deleteByChapterId(UUID chapterId);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})
            WITH c
            MATCH (cc:ChapterConcept {id: $chapterConceptId})
            MERGE (c)-[:HAS_CONCEPT]->(cc)
            """)
    void linkChapterToConcept(UUID chapterId, UUID chapterConceptId);

    @Query("""
            UNWIND $mentionIds AS mentionId
            MATCH (m:ConceptMention {id: mentionId})
            WITH m
            MATCH (cc:ChapterConcept {id: $chapterConceptId})
            MERGE (m)-[:REFERS_TO]->(cc)
            SET m.resolutionStatus = $resolutionStatus
            """)
    void linkMentionsToChapterConcept(List<UUID> mentionIds, UUID chapterConceptId, String resolutionStatus);
}
