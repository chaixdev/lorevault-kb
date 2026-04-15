package com.lorevault.api.timeline;

import com.lorevault.api.content.Scene;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.UUID;

/**
 * Repository for creating default temporal edges between scene events.
 * Uses MERGE operations to ensure idempotency and exact property values.
 */
public interface TemporalEdgeWriteRepository extends Neo4jRepository<Scene, UUID> {

    /**
     * Create default MEETS edges between consecutive scenes within each chapter of a book.
     * Idempotent via MERGE. Applies to all chapters belonging to the given book.
     *
     * @param bookId The book ID to create edges for
     * @return Number of edges created
     */
    @Query("""
            MATCH (b:Book {id: $bookId})
            MATCH (c:Chapter)-[:IN_BOOK]->(b)
            MATCH (c)-[:HAS_SCENE]->(s:Scene)
            WITH c, s ORDER BY c.chapterNumber, s.sceneIndex
            WITH c, collect(s) AS scenes
            UNWIND range(0, size(scenes) - 2) AS i
            WITH scenes[i] AS earlier, scenes[i + 1] AS later
            // Guard: skip if adding earlier->later would introduce a cycle via TEMPORAL edges
            WITH earlier, later
            WHERE NOT EXISTS { MATCH (later)-[:TEMPORAL*1..50]->(earlier) }
            MERGE (earlier)-[t:TEMPORAL]->(later)
            SET t.temporalRelation = 'R:temporal.meets',
                t.certainty = 'Heuristic',
                t.weight = 0.5,
                t.source = coalesce(t.source, 'default-ordering')
            RETURN count(t)
            """)
    int mergeInChapterDefaultEdges(@Param("bookId") UUID bookId);

    /**
     * Create default MEETS edges across adjacent chapters within a book.
     * Links the last scene of chapter N to the first scene of chapter N+1.
     * Idempotent via MERGE. Processes all adjacent pairs in the given book.
     *
     * @param bookId The book ID to create cross-chapter edges for
     * @return Number of cross-chapter edges created
     */
    @Query("""
            MATCH (b:Book {id: $bookId})
            MATCH (c1:Chapter)-[:IN_BOOK]->(b)
            MATCH (c2:Chapter)-[:IN_BOOK]->(b)
            WHERE c2.chapterNumber = c1.chapterNumber + 1
            
            // last scene of c1
            OPTIONAL MATCH (c1)-[:HAS_SCENE]->(s1:Scene)
            WITH b, c1, c2, s1
            ORDER BY c1.chapterNumber, s1.sceneIndex DESC
            WITH b, c1, c2, head(collect(s1)) AS lastScene
            
            // first scene of c2
            OPTIONAL MATCH (c2)-[:HAS_SCENE]->(s2:Scene)
            WITH lastScene, c2, s2
            ORDER BY c2.chapterNumber, s2.sceneIndex ASC
            WITH lastScene, head(collect(s2)) AS firstScene
            
            WHERE lastScene IS NOT NULL AND firstScene IS NOT NULL
            // Guard: skip if adding lastScene->firstScene would introduce a cycle via TEMPORAL edges
            WITH lastScene, firstScene
            WHERE NOT EXISTS { MATCH (firstScene)-[:TEMPORAL*1..500]->(lastScene) }
            MERGE (lastScene)-[t:TEMPORAL]->(firstScene)
            SET t.temporalRelation = 'R:temporal.meets',
                t.certainty = 'Heuristic',
                t.weight = 0.5,
                t.source = coalesce(t.source, 'default-ordering')
            RETURN count(t)
            """)
    int mergeCrossChapterDefaultEdge(@Param("bookId") UUID bookId);

    /**
     * Count existing TEMPORAL edges for a chapter (for testing/verification).
     *
     * @param chapterId The chapter ID
     * @return Number of temporal edges originating from scenes in this chapter
     */
    @Query("""
    MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene)-[t:TEMPORAL]->()
        RETURN count(t)
        """)
    int countTemporalEdgesFromChapter(@Param("chapterId") UUID chapterId);

    /**
     * Count how many in-chapter candidate pairs would create a cycle
     * (i.e., a bounded path exists from candidate later to earlier).
     */
    @Query("""
        MATCH (b:Book {id: $bookId})
        MATCH (c:Chapter)-[:IN_BOOK]->(b)
        MATCH (c)-[:HAS_SCENE]->(s:Scene)
        WITH c, s ORDER BY c.chapterNumber, s.sceneIndex
        WITH c, collect(s) AS scenes
        UNWIND range(0, size(scenes) - 2) AS i
        WITH scenes[i] AS earlier, scenes[i + 1] AS later
        WITH earlier, later
    MATCH (later)-[:TEMPORAL*1..50]->(earlier)
        RETURN count(*)
        """)
    int countInChapterCycleCandidates(@Param("bookId") UUID bookId);

    /**
     * Count how many cross-chapter candidate pairs would create a cycle
     * (i.e., a bounded path exists from firstScene to lastScene).
     */
    @Query("""
        MATCH (b:Book {id: $bookId})
        MATCH (c1:Chapter)-[:IN_BOOK]->(b)
        MATCH (c2:Chapter)-[:IN_BOOK]->(b)
        WHERE c2.chapterNumber = c1.chapterNumber + 1

        OPTIONAL MATCH (c1)-[:HAS_SCENE]->(s1:Scene)
        WITH b, c1, c2, s1
        ORDER BY c1.chapterNumber, s1.sceneIndex DESC
        WITH b, c1, c2, head(collect(s1)) AS lastScene

        OPTIONAL MATCH (c2)-[:HAS_SCENE]->(s2:Scene)
        WITH lastScene, c2, s2
        ORDER BY c2.chapterNumber, s2.sceneIndex ASC
        WITH lastScene, head(collect(s2)) AS firstScene

        WHERE lastScene IS NOT NULL AND firstScene IS NOT NULL
    MATCH (firstScene)-[:TEMPORAL*1..500]->(lastScene)
        RETURN count(*)
        """)
    int countCrossChapterCycleCandidates(@Param("bookId") UUID bookId);

    /**
     * Upsert a TEMPORAL edge between two scenes with full properties.
     * Properties are set exactly to provided values. Evidence chunk id is optional.
     */
    @Query("""
        MATCH (a:Scene {id: $fromId})
        MATCH (b:Scene {id: $toId})
        MERGE (a)-[t:TEMPORAL]->(b)
        SET t.temporalRelation = $type,
            t.certainty = $certainty,
            t.weight = coalesce($weight, 0.0),
            t.source = coalesce($source, 'ai-scene-analysis'),
            t.rationale = coalesce($rationale, ''),
            t.evidenceStartOffset = $evidenceStart,
            t.evidenceEndOffset = $evidenceEnd,
            t.evidenceChunkId = $evidenceChunkId
        RETURN id(t)
        """)
    Long upsertTemporalEdge(
            @Param("fromId") UUID fromId,
            @Param("toId") UUID toId,
            @Param("type") String type,
            @Param("certainty") String certainty,
            @Param("weight") Double weight,
            @Param("source") String source,
            @Param("rationale") String rationale,
            @Param("evidenceStart") Long evidenceStart,
            @Param("evidenceEnd") Long evidenceEnd,
            @Param("evidenceChunkId") UUID evidenceChunkId
    );
}
