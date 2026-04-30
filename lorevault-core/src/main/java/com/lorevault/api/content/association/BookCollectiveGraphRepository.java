package com.lorevault.api.content.association;

import java.util.UUID;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface BookCollectiveGraphRepository extends Neo4jRepository<BookCollective, UUID> {

    @Query("""
            MATCH (cc:ChapterCollective)-[r:REFERS_TO]->(:BookCollective {bookId: $bookId})
            DELETE r
            WITH DISTINCT $bookId AS bookId
            OPTIONAL MATCH (bc:BookCollective {bookId: bookId})
            DETACH DELETE bc
            """)
    void deleteByBookId(UUID bookId);

    @Query("""
            MATCH (bc:BookCollective {bookId: $bookId})
            RETURN count(bc)
            """)
    long countBookCollectivesByBookId(UUID bookId);

    @Query("""
            MATCH (b:Book {id: $bookId})
            WITH b
            MATCH (bc:BookCollective {id: $bookCollectiveId})
            MERGE (b)-[:HAS_COLLECTIVE]->(bc)
            """)
    void linkBookToCollective(UUID bookId, UUID bookCollectiveId);

    @Query("""
            UNWIND $chapterCollectiveIds AS chapterCollectiveId
            MATCH (cc:ChapterCollective {id: chapterCollectiveId})
            WITH cc
            MATCH (bc:BookCollective {id: $bookCollectiveId})
            MERGE (cc)-[:REFERS_TO]->(bc)
            """)
    void linkChapterCollectivesToBookCollective(Iterable<UUID> chapterCollectiveIds, UUID bookCollectiveId);
}
