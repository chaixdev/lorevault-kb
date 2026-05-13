package com.lorevault.api.content.mention;

import java.util.List;
import java.util.UUID;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface CollectiveMentionGraphRepository extends Neo4jRepository<CollectiveMention, UUID> {

    List<CollectiveMention> findByChapterId(UUID chapterId);

    @Query("""
            MATCH (s:Scene {id: $sceneId})
            WITH s
            MATCH (m:CollectiveMention {id: $mentionId})
            MERGE (s)-[:CONTAINS]->(m)
            """)
    void linkMentionToScene(UUID sceneId, UUID mentionId);
}
