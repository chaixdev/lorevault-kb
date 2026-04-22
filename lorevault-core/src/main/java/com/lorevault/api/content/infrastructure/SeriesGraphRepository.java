package com.lorevault.api.content.infrastructure;

import com.lorevault.api.content.domain.Series;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeriesGraphRepository extends Neo4jRepository<Series, UUID> {
    
    @Query("MATCH (s:Series) WHERE s.name = $name AND s.universeId = $universeId RETURN s")
    Optional<Series> findByNameAndUniverseId(String name, UUID universeId);

    List<Series> findByUniverseId(UUID universeId);
}
