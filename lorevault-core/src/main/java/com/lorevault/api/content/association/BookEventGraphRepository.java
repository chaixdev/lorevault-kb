package com.lorevault.api.content.association;

import java.util.UUID;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface BookEventGraphRepository extends Neo4jRepository<BookEvent, UUID> {
}
