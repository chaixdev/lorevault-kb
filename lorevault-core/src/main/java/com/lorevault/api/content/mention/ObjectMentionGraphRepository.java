package com.lorevault.api.content.mention;

import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface ObjectMentionGraphRepository extends Neo4jRepository<ObjectMention, UUID> {

    @Query("""
            MATCH (s:Scene {id: $sceneId})
            WITH s
            MATCH (m:ObjectMention {id: $mentionId})
            MERGE (s)-[:MENTIONS]->(m)
            """)
    void linkMentionToScene(UUID sceneId, UUID mentionId);
}
