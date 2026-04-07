package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.domain.ingestion.IngestionJob;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngestionJobGraphRepository extends Neo4jRepository<IngestionJob, UUID> {

    Optional<IngestionJob> findFirstByChapterIdOrderByCreatedAtDesc(UUID chapterId);

    List<IngestionJob> findByChapterIdIn(List<UUID> chapterIds);

    @Query("""
            MATCH (j:IngestionJob {chapterId: $chapterId})-[:HAS_CURRENT_STATUS]->(s:StatusRecord)
            WHERE s.status NOT IN ['COMPLETE','FAILED']
            RETURN count(j) > 0
            """)
    boolean existsActiveForChapter(UUID chapterId);

    @Query(
        """
        MATCH (j:IngestionJob {id: $jobId})
        OPTIONAL MATCH (j)-[:HAS_CURRENT_STATUS]->(old:StatusRecord)
        WITH j, old
        MATCH (s:StatusRecord {id: $statusId})
        FOREACH (_ IN CASE WHEN old IS NULL THEN [] ELSE [1] END | MERGE (old)-[:HAS_NEXT_STATUS]->(s))
        WITH j, s
        OPTIONAL MATCH (j)-[r:HAS_CURRENT_STATUS]->()
        DELETE r
        MERGE (j)-[:HAS_CURRENT_STATUS]->(s)
        WITH j, s
        OPTIONAL MATCH (j)-[i:HAS_INITIAL_STATUS]->()
        FOREACH (_ IN CASE WHEN i IS NULL THEN [1] ELSE [] END | CREATE (j)-[:HAS_INITIAL_STATUS]->(s))
        """
    )
    void swapCurrentStatus(UUID jobId, UUID statusId);
}
