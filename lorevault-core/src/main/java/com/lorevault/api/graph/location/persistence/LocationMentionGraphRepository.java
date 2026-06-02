package com.lorevault.api.graph.location.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface LocationMentionGraphRepository extends Neo4jRepository<LocationMention, UUID> {

    List<LocationMention> findByChapterId(UUID chapterId);

    @Query("""
            MATCH (s:Scene {id: $sceneId})
            WITH s
            MATCH (m:LocationMention {id: $mentionId})
            MERGE (s)-[:CONTAINS]->(m)
            """)
    void linkMentionToScene(UUID sceneId, UUID mentionId);
}
