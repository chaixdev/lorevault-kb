package com.lorevault.api.content.timeline;

import com.lorevault.api.content.entities.Scene;
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
     * Create default NEXT_IN_READING_ORDER edges between consecutive scenes within each chapter of a book.
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
            WITH earlier, later
            MERGE (earlier)-[a:NEXT_IN_READING_ORDER]->(later)
            SET a.source = coalesce(a.source, 'default-ordering')
            RETURN count(a)
            """)
    int mergeInChapterDefaultEdges(@Param("bookId") UUID bookId);

    /**
     * Create default NEXT_IN_READING_ORDER edges across adjacent chapters within a book.
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
            WITH lastScene, firstScene
            MERGE (lastScene)-[a:NEXT_IN_READING_ORDER]->(firstScene)
            SET a.source = coalesce(a.source, 'default-ordering')
            RETURN count(a)
            """)
    int mergeCrossChapterDefaultEdge(@Param("bookId") UUID bookId);

    /**
     * Count existing TEMPORAL edges for a chapter (for testing/verification).
     *
     * @param chapterId The chapter ID
     * @return Number of temporal edges originating from scenes in this chapter
     */
    @Query("""
    MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene)-[a:NEXT_IN_READING_ORDER]->()
        RETURN count(a)
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
        OPTIONAL MATCH (a)-[existingForward:TEMPORAL]->(b)
        DELETE existingForward
        WITH a, b
        OPTIONAL MATCH (b)-[existingReverse:TEMPORAL]->(a)
        DELETE existingReverse
        WITH a, b
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

    @Query("""
        MATCH (a:Scene {id: $fromId})
        MATCH (b:Scene {id: $toId})
        OPTIONAL MATCH (a)-[t:TEMPORAL]->(b)
        RETURN t.temporalRelation
        LIMIT 1
        """)
    String findTemporalRelationBetween(
            @Param("fromId") UUID fromId,
            @Param("toId") UUID toId
    );

    @Query("""
        MATCH (a:Scene {id: $fromId})
        MATCH (b:Scene {id: $toId})
        MERGE (a)-[r:AMBIGUOUS_RELATION]->(b)
        SET r.provenance = coalesce($provenance, 'inferred'),
            r.ambiguous = true,
            r.payload = coalesce($payload, ''),
            r.evidenceSnippet = coalesce($evidenceSnippet, ''),
            r.jobId = coalesce($jobId, ''),
            r.chapterId = coalesce($chapterId, ''),
            r.statusRecordId = coalesce($statusRecordId, ''),
            r.llmCallRecordId = coalesce($llmCallRecordId, ''),
            r.updatedAt = timestamp()
        RETURN id(r)
        """)
    Long upsertAmbiguousRelation(
            @Param("fromId") UUID fromId,
            @Param("toId") UUID toId,
            @Param("provenance") String provenance,
            @Param("payload") String payload,
            @Param("evidenceSnippet") String evidenceSnippet,
            @Param("jobId") String jobId,
            @Param("chapterId") String chapterId,
            @Param("statusRecordId") String statusRecordId,
            @Param("llmCallRecordId") String llmCallRecordId
    );
}
