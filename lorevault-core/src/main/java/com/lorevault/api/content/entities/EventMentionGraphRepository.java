package com.lorevault.api.content.entities;

import java.util.List;
import java.util.UUID;

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

    /**
     * Returns all EventMention nodes for a chapter in deterministic order:
     * by scene extraction order first, then by extractionIndex within each scene.
     * This ordering is required for the rolling-triad co-reference pass (Stage 2).
     */
    @Query("""
            MATCH (s:Scene {chapterId: $chapterId})-[:MENTIONS]->(m:EventMention {chapterId: $chapterId})
            RETURN m
            ORDER BY coalesce(s.sceneIndex, 0), coalesce(m.extractionIndex, 0)
            """)
    List<EventMention> findByChapterIdOrdered(UUID chapterId);

    /**
     * Delete all SAME_EVENT co-reference links between EventMention nodes in a chapter.
     * Called at the start of each Stage 2 pass to ensure idempotency.
     */
    @Query("""
            MATCH (a:EventMention {chapterId: $chapterId})-[r:SAME_EVENT]-(b:EventMention {chapterId: $chapterId})
            DELETE r
            """)
    void deleteCoreferenceLinks(UUID chapterId);

    /**
     * Create a SAME_EVENT link between two EventMention nodes.
     * Direction is intentionally undirected-style (MERGE both ways via min/max id ordering) —
     * the relationship carries symmetric semantics.
     * Properties: confidence (0.0-1.0), passId (UUID string), model (string).
     */
    @Query("""
            MATCH (a:EventMention {id: $mentionIdA})
            WITH a
            MATCH (b:EventMention {id: $mentionIdB})
            MERGE (a)-[:SAME_EVENT {confidence: $confidence, passId: $passId, model: $model}]->(b)
            """)
    void createSameEventLink(UUID mentionIdA, UUID mentionIdB, double confidence, String passId, String model);

    /**
     * Find connected components (clusters) of EventMention nodes linked by SAME_EVENT.
     * Returns rows of [componentId (the lowest UUID in each component), mentionId].
     * Callers group by componentId to get each chain.
     *
     * Note: Cypher does not have native connected-component traversal, so this uses
     * a bounded variable-length path match scoped to a single chapter.
     * The canonical component representative is the mention with the lexicographically
     * smallest id string — used only as a stable grouping key, not as a logical head.
     */
    @Query("""
            MATCH (root:EventMention {chapterId: $chapterId})
            OPTIONAL MATCH (root)-[:SAME_EVENT*0..]-(peer:EventMention {chapterId: $chapterId})
            WITH root, collect(DISTINCT coalesce(peer.id, root.id)) AS componentMemberIds
            WITH root,
                 [x IN componentMemberIds | toString(x)] AS componentMemberStrings
            WITH root,
                 componentMemberStrings,
                 reduce(minId = componentMemberStrings[0], x IN componentMemberStrings |
                     CASE WHEN x < minId THEN x ELSE minId END
                 ) AS componentId
            RETURN toString(root.id) AS mentionId, componentId
            """)
    List<SameEventComponentRow> findSameEventComponents(UUID chapterId);

    interface SameEventComponentRow {
        String getMentionId();
        String getComponentId();
    }
}
