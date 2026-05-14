package com.lorevault.catalog.internal;

import com.lorevault.catalog.RelationCatalogDefinition;
import com.lorevault.catalog.RelationCatalogId;
import com.lorevault.catalog.RelationKindSignature;
import com.lorevault.catalog.RelationQuery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL-backed catalog store with idempotent find-or-create semantics.
 *
 * Uses ON CONFLICT DO NOTHING on the unique definition_key constraint
 * so that concurrent resolve() calls for the same key produce exactly
 * one definition row — the winner inserts, the loser re-reads.
 */
@Repository
@ConditionalOnProperty(name = "lorevault.catalog.enabled", havingValue = "true")
class PostgresRelationCatalogStore implements RelationCatalogStore {

    private final JdbcClient jdbcClient;

    PostgresRelationCatalogStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<RelationCatalogDefinition> findByDefinitionKey(String definitionKey) {
        return jdbcClient.sql("""
                SELECT id, definition_key, display_name, description, created, updated, last_seen
                FROM catalog_definition
                WHERE definition_key = :definitionKey
                """)
                .param("definitionKey", definitionKey)
                .query(RelationCatalogDefinition.class)
                .optional()
                .map(this::enrichWithRelations);
    }

    @Override
    public Optional<RelationCatalogDefinition> findById(RelationCatalogId id) {
        return jdbcClient.sql("""
                SELECT id, definition_key, display_name, description, created, updated, last_seen
                FROM catalog_definition
                WHERE id = :id
                """)
                .param("id", id.value())
                .query(RelationCatalogDefinition.class)
                .optional()
                .map(this::enrichWithRelations);
    }

    @Override
    public RelationCatalogDefinition create(RelationQuery query) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String displayName = query.rawName() != null ? query.rawName() : query.definitionKey();

        // Idempotent insert: ON CONFLICT DO NOTHING on the unique definition_key.
        // If the insert is a no-op (concurrent caller won the race), re-read the existing row.
        int inserted = jdbcClient.sql("""
                INSERT INTO catalog_definition (id, definition_key, display_name, description, created, updated, last_seen)
                VALUES (:id, :definitionKey, :displayName, :description, :now, :now, :now)
                ON CONFLICT (definition_key) DO NOTHING
                """)
                .param("id", id)
                .param("definitionKey", query.definitionKey())
                .param("displayName", displayName)
                .param("description", query.description())
                .param("now", now)
                .update();

        if (inserted == 0) {
            // Concurrent insert won — re-read the existing definition
            return findByDefinitionKey(query.definitionKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Definition key '" + query.definitionKey() + "' not found after ON CONFLICT DO NOTHING"));
        }

        // We inserted — add signature and variant rows
        if (query.subjectKind() != null && query.objectKind() != null) {
            jdbcClient.sql("""
                    INSERT INTO catalog_definition_signature (definition_id, subject_kind, object_kind)
                    VALUES (:definitionId, :subjectKind, :objectKind)
                    ON CONFLICT (definition_id, subject_kind, object_kind) DO NOTHING
                    """)
                    .param("definitionId", id)
                    .param("subjectKind", query.subjectKind())
                    .param("objectKind", query.objectKind())
                    .update();
        }

        if (query.rawName() != null) {
            jdbcClient.sql("""
                    INSERT INTO catalog_definition_variant (definition_id, raw_name)
                    VALUES (:definitionId, :rawName)
                    ON CONFLICT (definition_id, raw_name) DO NOTHING
                    """)
                    .param("definitionId", id)
                    .param("rawName", query.rawName())
                    .update();
        }

        List<RelationKindSignature> signatures = query.subjectKind() != null && query.objectKind() != null
                ? List.of(new RelationKindSignature(query.subjectKind(), query.objectKind()))
                : List.of();
        List<String> variants = query.rawName() != null ? List.of(query.rawName()) : List.of();

        return new RelationCatalogDefinition(
                new RelationCatalogId(id),
                query.definitionKey(),
                displayName,
                query.description(),
                signatures,
                variants,
                now, now, now
        );
    }

    /**
     * Enrich a definition with its signature and variant rows.
     */
    private RelationCatalogDefinition enrichWithRelations(RelationCatalogDefinition def) {
        List<RelationKindSignature> signatures = jdbcClient.sql("""
                SELECT subject_kind, object_kind
                FROM catalog_definition_signature
                WHERE definition_id = :id
                """)
                .param("id", def.id().value())
                .query(RelationKindSignature.class)
                .list();

        List<String> variants = jdbcClient.sql("""
                SELECT raw_name
                FROM catalog_definition_variant
                WHERE definition_id = :id
                """)
                .param("id", def.id().value())
                .query(String.class)
                .list();

        return new RelationCatalogDefinition(
                def.id(), def.definitionKey(), def.displayName(), def.description(),
                signatures, variants,
                def.created(), def.updated(), def.lastSeen()
        );
    }
}
