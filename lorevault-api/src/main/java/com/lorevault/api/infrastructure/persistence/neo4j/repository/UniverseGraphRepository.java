package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.infrastructure.persistence.neo4j.model.UniverseNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UniverseGraphRepository extends Neo4jRepository<UniverseNode, UUID> {
    
    @Query("MATCH (u:Universe) WHERE u.name = $name RETURN u")
    Optional<UniverseNode> findByName(String name);
}