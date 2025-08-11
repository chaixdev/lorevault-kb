package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.Optional;
import java.util.UUID;

public interface ChapterGraphRepository extends Neo4jRepository<ChapterNode, UUID> {

    Optional<ChapterNode> findByContentHash(String contentHash);

    @Query("MATCH (c:Chapter) WHERE c.contentHash = $contentHash RETURN count(c) > 0")
    boolean existsByContentHash(String contentHash);
}
