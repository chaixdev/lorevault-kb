package com.lorevault.api.content.infrastructure;

import java.util.UUID;

import com.lorevault.api.content.domain.BookIndividual;
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
            MATCH (c:Chapter)-[:IN_BOOK]->(b:Book {id: $bookId})
            MATCH (c)-[:HAS_INDIVIDUAL]->(ci:ChapterIndividual)
            WHERE ci.normalizedName = $normalizedName
            RETURN count(DISTINCT ci)
            """)
    long countChapterIndividualsForBookAndName(UUID bookId, String normalizedName);

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

    @Query("""
            MATCH (c:Chapter)-[:IN_BOOK]->(:Book {id: $bookId})
            MATCH (c)-[:HAS_INDIVIDUAL]->(ci:ChapterIndividual {normalizedName: $normalizedName})
            MATCH (bi:BookIndividual {id: $bookIndividualId})
            MERGE (ci)-[:REFERS_TO]->(bi)
            """)
    void linkChapterIndividualsForBookAndNameToBookIndividual(UUID bookId, String normalizedName, UUID bookIndividualId);
}
