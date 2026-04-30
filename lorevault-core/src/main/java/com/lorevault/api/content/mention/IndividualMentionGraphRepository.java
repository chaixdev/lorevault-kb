package com.lorevault.api.content.mention;

import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface IndividualMentionGraphRepository extends Neo4jRepository<IndividualMention, UUID> {

    @Query("""
            MATCH (s:Scene {id: $sceneId})
            WITH s
            MATCH (m:IndividualMention {id: $mentionId})
            MERGE (s)-[:MENTIONS]->(m)
            """)
    void linkMentionToScene(UUID sceneId, UUID mentionId);
}
