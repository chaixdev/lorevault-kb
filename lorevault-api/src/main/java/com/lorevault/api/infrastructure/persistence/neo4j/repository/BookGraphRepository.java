package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.domain.content.Book;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookGraphRepository extends Neo4jRepository<Book, UUID> {
    
    @Query("MATCH (b:Book) WHERE b.title = $title AND b.seriesId = $seriesId RETURN b")
    Optional<Book> findByTitleAndSeriesId(String title, UUID seriesId);
    
    @Query("MATCH (b:Book) WHERE b.title = $title AND b.universeId = $universeId AND b.seriesId IS NULL RETURN b")
    Optional<Book> findStandaloneByTitleAndUniverseId(String title, UUID universeId);

    List<Book> findByUniverseId(UUID universeId);

    List<Book> findBySeriesId(UUID seriesId);
}
