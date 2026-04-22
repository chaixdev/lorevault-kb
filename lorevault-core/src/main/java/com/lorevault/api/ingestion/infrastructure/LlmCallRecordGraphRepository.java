package com.lorevault.api.ingestion.infrastructure;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.IngestionFailure;

import com.lorevault.api.ingestion.domain.LlmCallRecord;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.UUID;

public interface LlmCallRecordGraphRepository extends Neo4jRepository<LlmCallRecord, UUID> {

    @Query("""
    MATCH (r:LlmCallRecord {jobId: $jobId})
    OPTIONAL MATCH (r)-[:OF_JOB]->(j:IngestionJob)
    OPTIONAL MATCH (r)-[:OF_STATUS]->(s:StatusRecord)
    RETURN r, j AS job, s AS status ORDER BY r.createdAt ASC
    """)
    List<LlmCallRecord> findByJobId(UUID jobId);

    @Query("""
    MATCH (r:LlmCallRecord {jobId: $jobId, step: $step})
    OPTIONAL MATCH (r)-[:OF_JOB]->(j:IngestionJob)
    OPTIONAL MATCH (r)-[:OF_STATUS]->(s:StatusRecord)
    RETURN r, j AS job, s AS status ORDER BY r.createdAt ASC
    """)
    List<LlmCallRecord> findByJobIdAndStep(UUID jobId, String step);

    @Query("""
    MATCH (r:LlmCallRecord {jobId: $jobId, step: $step, statusRecordId: $statusRecordId})
    OPTIONAL MATCH (r)-[:OF_JOB]->(j:IngestionJob)
    OPTIONAL MATCH (r)-[:OF_STATUS]->(s:StatusRecord)
    RETURN r, j AS job, s AS status
    ORDER BY r.createdAt DESC
    LIMIT 1
    """)
    java.util.Optional<LlmCallRecord> findLatestByJobStepAndStatusRecord(UUID jobId, String step, UUID statusRecordId);

    /**
     * Existence checks via Cypher are more reliable than relying on SDN relationship hydration,
     * especially when custom projections/queries are involved. These queries are used by tests to
     * assert that graph relationships are actually created in Neo4j, independent of mapping.
     */

    @Query("""
        MATCH (r:LlmCallRecord {id: $recordId})-[:OF_JOB]->(j:IngestionJob {id: $jobId})
        RETURN COUNT(*) > 0
    """)
    boolean hasOfJobRelation(UUID recordId, UUID jobId);

    @Query("""
        MATCH (r:LlmCallRecord {id: $recordId})-[:OF_STATUS]->(s:StatusRecord {id: $statusId})
        RETURN COUNT(*) > 0
    """)
    boolean hasOfStatusRelation(UUID recordId, UUID statusId);
}
