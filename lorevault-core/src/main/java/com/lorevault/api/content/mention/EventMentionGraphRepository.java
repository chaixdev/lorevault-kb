package com.lorevault.api.content.mention;

import java.util.List;
import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

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
     * Used by Stage 3 (ChapterEventResolutionService) to load all mentions for aggregation.
     */
    @Query("""
            MATCH (s:Scene {chapterId: $chapterId})-[:MENTIONS]->(m:EventMention {chapterId: $chapterId})
            RETURN m
            ORDER BY coalesce(s.sceneIndex, 0), coalesce(m.extractionIndex, 0)
            """)
    List<EventMention> findByChapterIdOrdered(UUID chapterId);

    @Query("""
            MATCH (s:Scene)-[:MENTIONS]->(m:EventMention)
            WHERE s.id IN $sceneIds
            RETURN m
            ORDER BY m.sceneId, m.extractionIndex
            """)
    List<EventMention> findMentionsBySceneIds(@Param("sceneIds") List<String> sceneIds);

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
     *
     * <p>MERGE matches only on the endpoint nodes and relationship type — metadata fields
     * (confidence, passId, model) are SET after the MERGE so that overlapping windows
     * assessing the same pair update an existing edge rather than producing duplicates.</p>
     */
    @Query("""
            MATCH (a:EventMention {id: $mentionIdA})
            WITH a
            MATCH (b:EventMention {id: $mentionIdB})
            MERGE (a)-[r:SAME_EVENT]->(b)
            SET r.confidence = $confidence, r.passId = $passId, r.source = $source, r.model = $model
            """)
    void createSameEventLink(UUID mentionIdA, UUID mentionIdB, double confidence, String passId, String source, String model);
}
