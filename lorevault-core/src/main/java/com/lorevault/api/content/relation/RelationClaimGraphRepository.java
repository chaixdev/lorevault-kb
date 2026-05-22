package com.lorevault.api.content.relation;

import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface RelationClaimGraphRepository extends Neo4jRepository<RelationClaim, UUID> {

    @Query("""
            MATCH (s:Scene {id: $sceneId})
            WITH s
            MATCH (rc:RelationClaim {id: $claimId})
            MERGE (s)-[:CONTAINS]->(rc)
            """)
    void linkClaimToScene(UUID sceneId, UUID claimId);

    /**
     * Links this RelationClaim to its subject entity Mention node.
     * Best-effort — the mention may not exist yet at claim creation time.
     */
    @Query("""
            MATCH (rc:RelationClaim {id: $claimId})
            WITH rc
            MATCH (m:Mention {id: $subjectMentionId})
            MERGE (rc)-[:RELATES_SUBJECT]->(m)
            """)
    void linkSubjectMention(UUID claimId, UUID subjectMentionId);

    /**
     * Links this RelationClaim to its object entity Mention node.
     * Best-effort — the mention may not exist yet at claim creation time.
     */
    @Query("""
            MATCH (rc:RelationClaim {id: $claimId})
            WITH rc
            MATCH (m:Mention {id: $objectMentionId})
            MERGE (rc)-[:RELATES_OBJECT]->(m)
            """)
    void linkObjectMention(UUID claimId, UUID objectMentionId);

    /**
     * Idempotency guard: counts existing claims with the same scene and content identity.
     * Uses (sceneId, subjectName, relationName, objectName) as the dedup key
     * instead of positional extraction index, which can shift on retry.
     */
    @Query("""
            MATCH (rc:RelationClaim {sceneId: $sceneId, subjectName: $subjectName, relationName: $relationName, objectName: $objectName})
            RETURN count(rc)
            """)
    long countBySceneIdAndContentIdentity(UUID sceneId, String subjectName, String relationName, String objectName);
}
