package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.infrastructure.persistence.neo4j.model.BookNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookGraphRepository extends Neo4jRepository<BookNode, UUID> {
    
    @Query("MATCH (b:Book) WHERE b.title = $title AND b.seriesId = $seriesId RETURN b")
    Optional<BookNode> findByTitleAndSeriesId(String title, UUID seriesId);
    
    @Query("MATCH (b:Book) WHERE b.title = $title AND b.universeId = $universeId AND b.seriesId IS NULL RETURN b")
    Optional<BookNode> findStandaloneByTitleAndUniverseId(String title, UUID universeId);

    List<BookNode> findByUniverseId(UUID universeId);

    List<BookNode> findBySeriesId(UUID seriesId);
}