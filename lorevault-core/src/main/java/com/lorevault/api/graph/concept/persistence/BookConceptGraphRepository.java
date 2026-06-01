package com.lorevault.api.graph.concept.persistence;

import java.util.UUID;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface BookConceptGraphRepository extends Neo4jRepository<BookConcept, UUID> {

    @Query("""
            MATCH (cc:ChapterConcept)-[r:REFERS_TO]->(:BookConcept {bookId: $bookId})
            DELETE r
            WITH DISTINCT $bookId AS bookId
            OPTIONAL MATCH (bc:BookConcept {bookId: bookId})
            DETACH DELETE bc
            """)
    void deleteByBookId(UUID bookId);

    @Query("""
            MATCH (bc:BookConcept {bookId: $bookId})
            RETURN count(bc)
            """)
    long countBookConceptsByBookId(UUID bookId);

    @Query("""
            MATCH (b:Book {id: $bookId})
            WITH b
            MATCH (bc:BookConcept {id: $bookConceptId})
            MERGE (b)-[:HAS_CONCEPT]->(bc)
            """)
    void linkBookToConcept(UUID bookId, UUID bookConceptId);

    @Query("""
            UNWIND $chapterConceptIds AS chapterConceptId
            MATCH (cc:ChapterConcept {id: chapterConceptId})
            WITH cc
            MATCH (bc:BookConcept {id: $bookConceptId})
            MERGE (cc)-[:REFERS_TO]->(bc)
            """)
    void linkChapterConceptsToBookConcept(Iterable<UUID> chapterConceptIds, UUID bookConceptId);
}
