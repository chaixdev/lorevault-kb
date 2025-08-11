package com.lorevault.api.graph.repo;

import com.lorevault.api.graph.model.IngestionJobNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngestionJobGraphRepository extends Neo4jRepository<IngestionJobNode, UUID> {

    @Query("MATCH (j:IngestionJob) WHERE j.chapterId = $chapterId RETURN j ORDER BY j.createdAt DESC LIMIT 1")
    Optional<IngestionJobNode> findLatestForChapter(UUID chapterId);

    @Query("MATCH (j:IngestionJob) WHERE j.chapterId IN $chapterIds RETURN j")
    List<IngestionJobNode> findByChapterIds(List<UUID> chapterIds);

    @Query("MATCH (j:IngestionJob) WHERE j.chapterId = $chapterId AND j.currentStatusTerminal = false RETURN count(j) > 0")
    boolean existsActiveForChapter(UUID chapterId);
}
