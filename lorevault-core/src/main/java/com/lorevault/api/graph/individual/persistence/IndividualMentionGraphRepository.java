package com.lorevault.api.graph.individual.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface IndividualMentionGraphRepository extends Neo4jRepository<IndividualMention, UUID> {

    @Query("""
            MATCH (s:Scene {id: $sceneId})
            WITH s
            MATCH (m:IndividualMention {id: $mentionId})
            MERGE (s)-[:CONTAINS]->(m)
            """)
    void linkMentionToScene(UUID sceneId, UUID mentionId);

    List<IndividualMention> findByChapterId(UUID chapterId);
}
