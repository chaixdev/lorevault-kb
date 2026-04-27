package com.lorevault.api.content.entities;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChapterGraphRepository extends Neo4jRepository<Chapter, UUID> {

    Optional<Chapter> findByContentHash(String contentHash);

    @Query("MATCH (c:Chapter) WHERE c.contentHash = $contentHash RETURN count(c) > 0")
    boolean existsByContentHash(String contentHash);
    
    @Query("""
            MATCH (b:Book {id: $bookId})
            MATCH (c:Chapter)-[:IN_BOOK]->(b)
            OPTIONAL MATCH (c)-[:HAS_SCENE]->(s:Scene)
            RETURN c, collect(s) as scenes
            ORDER BY c.chapterNumber
            """)
    List<Chapter> findByBookId(UUID bookId);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})
            RETURN coalesce(c.eventResolutionCompletedJobId, '') = toString($jobId)
            """)
    boolean hasCompletedEventResolutionForJob(UUID chapterId, UUID jobId);

    @Query("""
            MATCH (c:Chapter {id: $chapterId})
            SET c.eventResolutionCompletedJobId = toString($jobId),
                c.eventResolutionCompletedAt = datetime()
            """)
    void markEventResolutionCompleted(UUID chapterId, UUID jobId);

}
