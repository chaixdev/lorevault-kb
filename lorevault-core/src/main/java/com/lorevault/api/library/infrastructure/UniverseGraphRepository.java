package com.lorevault.api.library.infrastructure;

import com.lorevault.api.library.domain.Universe;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UniverseGraphRepository extends Neo4jRepository<Universe, UUID> {
    
    @Query("MATCH (u:Universe) WHERE u.name = $name RETURN u")
    Optional<Universe> findByName(String name);
}
