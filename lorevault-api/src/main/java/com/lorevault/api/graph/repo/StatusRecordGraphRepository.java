package com.lorevault.api.graph.repo;

import com.lorevault.api.graph.model.StatusRecordNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.UUID;

public interface StatusRecordGraphRepository extends Neo4jRepository<StatusRecordNode, UUID> {

    @Query("MATCH (s:StatusRecord) WHERE s.jobId = $jobId RETURN s ORDER BY s.timestamp DESC LIMIT $limit")
    List<StatusRecordNode> findRecentForJob(UUID jobId, int limit);
}
