package com.lorevault.api.content;

import java.util.UUID;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface IndividualGraphRepository extends Neo4jRepository<Individual, UUID> {

    @Query("MATCH (s:Scene {id: $sceneId}), (i:Individual {id: $individualId}) MERGE (s)-[:MENTIONS]->(i)")
    void linkMentionedIndividual(UUID sceneId, UUID individualId);
}
