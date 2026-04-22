package com.lorevault.api.content.infrastructure;

import java.util.UUID;

import com.lorevault.api.content.domain.EventMention;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface EventMentionGraphRepository extends Neo4jRepository<EventMention, UUID> {

    @Query("""
            MATCH (s:Scene {id: $sceneId})
            WITH s
            MATCH (m:EventMention {id: $mentionId})
            MERGE (s)-[:MENTIONS]->(m)
            """)
    void linkMentionToScene(UUID sceneId, UUID mentionId);
}
