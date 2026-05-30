package com.lorevault.api.graph.location.persistence;

import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface BookLocationGraphRepository extends Neo4jRepository<BookLocation, UUID> {

    @Query("""
            MATCH (cl:ChapterLocation)-[r:REFERS_TO]->(:BookLocation {bookId: $bookId})
            DELETE r
            WITH DISTINCT $bookId AS bookId
            OPTIONAL MATCH (bl:BookLocation {bookId: bookId})
            DETACH DELETE bl
            """)
    void deleteByBookId(UUID bookId);

    @Query("""
            MATCH (c:Chapter)-[:IN_BOOK]->(:Book {id: $bookId})
            MATCH (c)-[:HAS_LOCATION]->(cl:ChapterLocation)
            WHERE cl.normalizedName = $normalizedName
            RETURN count(DISTINCT cl)
            """)
    long countChapterLocationsForBookAndName(UUID bookId, String normalizedName);

    @Query("""
            MATCH (bl:BookLocation {bookId: $bookId})
            RETURN count(bl)
            """)
    long countBookLocationsByBookId(UUID bookId);

    @Query("""
            MATCH (b:Book {id: $bookId})
            WITH b
            MATCH (bl:BookLocation {id: $bookLocationId})
            MERGE (b)-[:HAS_LOCATION]->(bl)
            """)
    void linkBookToLocation(UUID bookId, UUID bookLocationId);

    @Query("""
            UNWIND $chapterLocationIds AS chapterLocationId
            MATCH (cl:ChapterLocation {id: chapterLocationId})
            WITH cl
            MATCH (bl:BookLocation {id: $bookLocationId})
            MERGE (cl)-[:REFERS_TO]->(bl)
            """)
    void linkChapterLocationsToBookLocation(Iterable<UUID> chapterLocationIds, UUID bookLocationId);
}
