package com.lorevault.api.content.association;

import java.util.UUID;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface BookEventGraphRepository extends Neo4jRepository<BookEvent, UUID> {

    @Query("""
            MATCH (b:Book {id: $bookId})
            WITH b
            MATCH (be:BookEvent {id: $bookEventId})
            MERGE (b)-[:HAS_EVENT]->(be)
            """)
    void linkBookToEvent(UUID bookId, UUID bookEventId);
}
