package com.lorevault.api.ai.infrastructure;
import com.lorevault.api.ai.telemetry.LlmCallRecord;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.UUID;

public interface LlmCallRecordGraphRepository extends Neo4jRepository<LlmCallRecord, UUID> {
    @Query("""
    MATCH (r:LlmCallRecord {jobId: $jobId})
    OPTIONAL MATCH (r)-[reqRel:WITH_REQUEST]->(req:LlmCallRequest)
    OPTIONAL MATCH (r)-[respRel:WITH_RESPONSE]->(resp:LlmCallResponse)
    OPTIONAL MATCH (r)-[jobRel:OF_JOB]->(j)
    OPTIONAL MATCH (r)-[statusRel:OF_STAGE]->(s:Stage)
    RETURN r,
      collect(DISTINCT reqRel), collect(DISTINCT req),
      collect(DISTINCT respRel), collect(DISTINCT resp),
      collect(DISTINCT jobRel), collect(DISTINCT j),
      collect(DISTINCT statusRel), collect(DISTINCT s)
    ORDER BY r.createdAt ASC
    """)
    List<LlmCallRecord> findByJobId(UUID jobId);

    @Query("""
    MATCH (r:LlmCallRecord {jobId: $jobId, step: $step})
    OPTIONAL MATCH (r)-[reqRel:WITH_REQUEST]->(req:LlmCallRequest)
    OPTIONAL MATCH (r)-[respRel:WITH_RESPONSE]->(resp:LlmCallResponse)
    OPTIONAL MATCH (r)-[jobRel:OF_JOB]->(j)
    OPTIONAL MATCH (r)-[statusRel:OF_STAGE]->(s:Stage)
    RETURN r,
      collect(DISTINCT reqRel), collect(DISTINCT req),
      collect(DISTINCT respRel), collect(DISTINCT resp),
      collect(DISTINCT jobRel), collect(DISTINCT j),
      collect(DISTINCT statusRel), collect(DISTINCT s)
    ORDER BY r.createdAt ASC
    """)
    List<LlmCallRecord> findByJobIdAndStep(UUID jobId, String step);

    @Query("""
    MATCH (r:LlmCallRecord {jobId: $jobId, step: $step, stageId: $stageId})
    WITH r ORDER BY r.createdAt DESC LIMIT 1
    OPTIONAL MATCH (r)-[reqRel:WITH_REQUEST]->(req:LlmCallRequest)
    OPTIONAL MATCH (r)-[respRel:WITH_RESPONSE]->(resp:LlmCallResponse)
    OPTIONAL MATCH (r)-[jobRel:OF_JOB]->(j)
    OPTIONAL MATCH (r)-[statusRel:OF_STAGE]->(s:Stage)
    RETURN r,
      collect(DISTINCT reqRel), collect(DISTINCT req),
      collect(DISTINCT respRel), collect(DISTINCT resp),
      collect(DISTINCT jobRel), collect(DISTINCT j),
      collect(DISTINCT statusRel), collect(DISTINCT s)
    """)
    java.util.Optional<LlmCallRecord> findLatestByJobStepAndStage(UUID jobId, String step, UUID stageId);

    /**
     * Existence checks via Cypher are more reliable than relying on SDN relationship hydration,
     * especially when custom projections/queries are involved. These queries are used by tests to
     * assert that graph relationships are actually created in Neo4j, independent of mapping.
     */

    @Query("""
        MATCH (r:LlmCallRecord {id: $recordId})-[:OF_JOB]->(j {id: $jobId})
        RETURN COUNT(*) > 0
    """)
    boolean hasOfJobRelation(UUID recordId, UUID jobId);

    @Query("""
        MATCH (r:LlmCallRecord {id: $recordId})-[:OF_STAGE]->(s:Stage {id: $stageId})
        RETURN COUNT(*) > 0
    """)
    boolean hasOfStageRelation(UUID recordId, UUID stageId);
}
