package com.lorevault.api.content.association;

import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface BookIndividualGraphRepository extends Neo4jRepository<BookIndividual, UUID> {

    @Query("""
            MATCH (ci:ChapterIndividual)-[r:REFERS_TO]->(:BookIndividual {bookId: $bookId})
            DELETE r
            WITH DISTINCT $bookId AS bookId
            OPTIONAL MATCH (bi:BookIndividual {bookId: bookId})
            DETACH DELETE bi
            """)
    void deleteByBookId(UUID bookId);

    @Query("""
            MATCH (bi:BookIndividual {bookId: $bookId})
            RETURN count(bi)
            """)
    long countBookIndividualsByBookId(UUID bookId);

    @Query("""
            MATCH (b:Book {id: $bookId})
            WITH b
            MATCH (bi:BookIndividual {id: $bookIndividualId})
            MERGE (b)-[:HAS_INDIVIDUAL]->(bi)
            """)
    void linkBookToIndividual(UUID bookId, UUID bookIndividualId);

    @Query("""
            MATCH (ci:ChapterIndividual {id: $chapterIndividualId})
            WITH ci
            MATCH (bi:BookIndividual {id: $bookIndividualId})
            MERGE (ci)-[:REFERS_TO]->(bi)
            """)
    void linkChapterIndividualToBookIndividual(UUID chapterIndividualId, UUID bookIndividualId);

}
