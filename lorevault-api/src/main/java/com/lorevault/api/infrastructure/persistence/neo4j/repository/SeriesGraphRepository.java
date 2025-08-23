package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.infrastructure.persistence.neo4j.model.SeriesNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeriesGraphRepository extends Neo4jRepository<SeriesNode, UUID> {
    
    @Query("MATCH (s:Series) WHERE s.name = $name AND s.universeId = $universeId RETURN s")
    Optional<SeriesNode> findByNameAndUniverseId(String name, UUID universeId);
}