package com.lorevault.api.infrastructure.persistence.neo4j.repository;

import com.lorevault.api.domain.ingestion.StatusRecord;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
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
            RETURN sr
            ORDER BY idx
    """)
    List<StatusRecord> findStatusHistoryForJob(UUID jobId);

}
