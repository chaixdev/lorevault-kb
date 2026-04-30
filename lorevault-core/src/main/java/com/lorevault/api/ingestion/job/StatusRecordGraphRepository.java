package com.lorevault.api.ingestion.job;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatusRecordGraphRepository extends Neo4jRepository<StatusRecord, UUID> {

    @Query("""
            MATCH (j:IngestionJob {id: $jobId})
            MATCH (j)-[:HAS_INITIAL_STATUS]->(head:StatusRecord)
            MATCH (j)-[:HAS_CURRENT_STATUS]->(tail:StatusRecord)
            MATCH p = (head)-[:HAS_NEXT_STATUS*0..]->(tail)
            WITH head, nodes(p) AS chain
            UNWIND chain AS sr
            WITH head, sr, length(shortestPath((head)-[:HAS_NEXT_STATUS*0..]->(sr))) AS idx
            WITH DISTINCT sr, idx
            RETURN sr
            ORDER BY idx
    """)
    List<StatusRecord> findStatusHistoryForJob(UUID jobId);

    @Query("""
            MATCH (s:StatusRecord {jobId: $jobId, status: 'SCENE_TRIAD_ANALYSIS'})
            RETURN s
            ORDER BY s.timestamp DESC
            """)
    List<StatusRecord> findTriadStatusesForJob(UUID jobId);

    @Query("""
            MATCH (s:StatusRecord {jobId: $jobId, status: 'SCENE_TRIAD_ANALYSIS'})
            WHERE s.`prop.currentSceneId` = $currentSceneId
            RETURN s
            ORDER BY s.timestamp DESC
            LIMIT 1
            """)
    Optional<StatusRecord> findLatestTriadStatusByCurrentSceneId(UUID jobId, String currentSceneId);

}
