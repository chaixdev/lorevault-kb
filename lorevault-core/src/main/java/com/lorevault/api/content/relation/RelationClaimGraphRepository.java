package com.lorevault.api.content.relation;

import java.util.UUID;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface RelationClaimGraphRepository extends Neo4jRepository<RelationClaim, UUID> {

    @Query("""
            MATCH (s:Scene {id: $sceneId})
            WITH s
            MATCH (rc:RelationClaim {id: $claimId})
            MERGE (s)-[:MENTIONS]->(rc)
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
}
