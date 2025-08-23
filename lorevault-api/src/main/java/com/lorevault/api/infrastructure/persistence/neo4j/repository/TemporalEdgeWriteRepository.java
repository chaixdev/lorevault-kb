package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.UUID;

/**
 * Repository for creating default temporal edges between scene events.
 * Uses MERGE operations to ensure idempotency and exact property values.
 */
public interface TemporalEdgeWriteRepository extends Neo4jRepository<SceneNode, UUID> {

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
            MERGE (earlier)-[t:MEETS]->(later)
            SET t.type = 'HEURISTIC',
                t.confidence = 0.5
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
            MERGE (lastScene)-[t:MEETS]->(firstScene)
            SET t.type = 'HEURISTIC', t.confidence = 0.5
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
        MATCH (c:Chapter {id: $chapterId})-[:HAS_SCENE]->(s:Scene)-[t:MEETS]->()
        RETURN count(t)
        """)
    int countTemporalEdgesFromChapter(@Param("chapterId") UUID chapterId);
}