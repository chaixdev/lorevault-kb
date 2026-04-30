package com.lorevault.api.content.association;

import java.util.UUID;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface BookObjectGraphRepository extends Neo4jRepository<BookObject, UUID> {

    @Query("""
            MATCH (co:ChapterObject)-[r:REFERS_TO]->(:BookObject {bookId: $bookId})
            DELETE r
            WITH DISTINCT $bookId AS bookId
            OPTIONAL MATCH (bo:BookObject {bookId: bookId})
            DETACH DELETE bo
            """)
    void deleteByBookId(UUID bookId);

    @Query("""
            MATCH (bo:BookObject {bookId: $bookId})
            RETURN count(bo)
            """)
    long countBookObjectsByBookId(UUID bookId);

    @Query("""
            MATCH (b:Book {id: $bookId})
            WITH b
            MATCH (bo:BookObject {id: $bookObjectId})
            MERGE (b)-[:HAS_OBJECT]->(bo)
            """)
    void linkBookToObject(UUID bookId, UUID bookObjectId);

    @Query("""
            UNWIND $chapterObjectIds AS chapterObjectId
            MATCH (co:ChapterObject {id: chapterObjectId})
            WITH co
            MATCH (bo:BookObject {id: $bookObjectId})
            MERGE (co)-[:REFERS_TO]->(bo)
            """)
    void linkChapterObjectsToBookObject(Iterable<UUID> chapterObjectIds, UUID bookObjectId);
}
