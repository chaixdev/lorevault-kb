package com.lorevault.api.graph.concept.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface ConceptMentionGraphRepository extends Neo4jRepository<ConceptMention, UUID> {

    List<ConceptMention> findByChapterId(UUID chapterId);

    @Query("""
            MATCH (s:Scene {id: $sceneId})
            WITH s
            MATCH (m:ConceptMention {id: $mentionId})
            MERGE (s)-[:CONTAINS]->(m)
            """)
    void linkMentionToScene(UUID sceneId, UUID mentionId);
}
