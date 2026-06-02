package com.lorevault.api.orchestration.job;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChapterIngestionJobGraphRepository extends Neo4jRepository<ChapterIngestionJob, UUID> {

    Optional<ChapterIngestionJob> findFirstByChapterIdOrderByCreatedAtDesc(UUID chapterId);

    @Query("""
            MATCH (j:ChapterIngestionJob {chapterId: $chapterId})
            RETURN j.id
            ORDER BY j.createdAt DESC
            LIMIT 1
            """)
    Optional<UUID> findLatestJobIdByChapterId(UUID chapterId);

    List<ChapterIngestionJob> findByChapterIdIn(List<UUID> chapterIds);

    @Query("""
            MATCH (j:ChapterIngestionJob {chapterId: $chapterId})-[:HAS_STAGE]->(s:Stage)
            WHERE s.status <> 'COMPLETED' AND s.status <> 'SKIPPED' AND s.status <> 'FAILED'
            RETURN count(j) > 0
            """)
    boolean existsActiveForChapter(UUID chapterId);
}
