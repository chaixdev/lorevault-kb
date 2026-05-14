package com.lorevault.catalog.internal;

import com.lorevault.catalog.RelationCatalogDefinition;
import com.lorevault.catalog.RelationCatalogId;
import com.lorevault.catalog.RelationKindSignature;
import com.lorevault.catalog.RelationQuery;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<RelationCatalogDefinition> DEFINITION_ROW_MAPPER = (rs, rowNum) ->
            new RelationCatalogDefinition(
                    new RelationCatalogId(rs.getObject("id", UUID.class)),
                    rs.getString("definition_key"),
                    rs.getString("display_name"),
                    rs.getString("description"),
                    List.of(),  // enriched separately
                    List.of(),  // enriched separately
                    rs.getObject("created", OffsetDateTime.class).toInstant(),
                    rs.getObject("updated", OffsetDateTime.class).toInstant(),
                    rs.getObject("last_seen", OffsetDateTime.class).toInstant()
            );

    private static final RowMapper<RelationKindSignature> SIGNATURE_ROW_MAPPER = (rs, rowNum) ->
            new RelationKindSignature(
                    rs.getString("subject_kind"),
                    rs.getString("object_kind")
            );

    PostgresRelationCatalogStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RelationCatalogDefinition> findByDefinitionKey(String definitionKey) {
        try {
            var def = jdbcTemplate.queryForObject(
                    """
                    SELECT id, definition_key, display_name, description, created, updated, last_seen
                    FROM catalog_definition
                    WHERE definition_key = :definitionKey
                    """,
                    new MapSqlParameterSource("definitionKey", definitionKey),
                    DEFINITION_ROW_MAPPER
            );
            return Optional.ofNullable(def).map(this::enrichWithRelations);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RelationCatalogDefinition> findById(RelationCatalogId id) {
        try {
            var def = jdbcTemplate.queryForObject(
                    """
                    SELECT id, definition_key, display_name, description, created, updated, last_seen
                    FROM catalog_definition
                    WHERE id = :id
                    """,
                    new MapSqlParameterSource().addValue("id", id.value()),
                    DEFINITION_ROW_MAPPER
            );
            return Optional.ofNullable(def).map(this::enrichWithRelations);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public RelationCatalogDefinition create(RelationQuery query) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String displayName = query.rawName() != null ? query.rawName() : query.definitionKey();

        // Idempotent insert: ON CONFLICT DO NOTHING on the unique definition_key.
        // If the insert is a no-op (concurrent caller won the race), re-read the existing row.
        OffsetDateTime nowOdt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("definitionKey", query.definitionKey())
                .addValue("displayName", displayName)
                .addValue("description", query.description())
                .addValue("now", nowOdt);

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO catalog_definition (id, definition_key, display_name, description, created, updated, last_seen)
                VALUES (:id, :definitionKey, :displayName, :description, :now, :now, :now)
                ON CONFLICT (definition_key) DO NOTHING
                """,
                params
        );

        if (inserted == 0) {
            // Concurrent insert won — re-read the existing definition
            return findByDefinitionKey(query.definitionKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Definition key '" + query.definitionKey() + "' not found after ON CONFLICT DO NOTHING"));
        }

        // We inserted — add signature and variant rows
        if (query.subjectKind() != null && query.objectKind() != null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO catalog_definition_signature (definition_id, subject_kind, object_kind)
                    VALUES (:definitionId, :subjectKind, :objectKind)
                    ON CONFLICT (definition_id, subject_kind, object_kind) DO NOTHING
                    """,
                    new MapSqlParameterSource()
                            .addValue("definitionId", id)
                            .addValue("subjectKind", query.subjectKind())
                            .addValue("objectKind", query.objectKind())
            );
        }

        if (query.rawName() != null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO catalog_definition_variant (definition_id, raw_name)
                    VALUES (:definitionId, :rawName)
                    ON CONFLICT (definition_id, raw_name) DO NOTHING
                    """,
                    new MapSqlParameterSource()
                            .addValue("definitionId", id)
                            .addValue("rawName", query.rawName())
            );
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
        var idParam = new MapSqlParameterSource().addValue("id", def.id().value());

        List<RelationKindSignature> signatures = jdbcTemplate.query(
                """
                SELECT subject_kind, object_kind
                FROM catalog_definition_signature
                WHERE definition_id = :id
                """,
                idParam,
                SIGNATURE_ROW_MAPPER
        );

        List<String> variants = jdbcTemplate.queryForList(
                """
                SELECT raw_name
                FROM catalog_definition_variant
                WHERE definition_id = :id
                """,
                idParam,
                String.class
        );

        return new RelationCatalogDefinition(
                def.id(), def.definitionKey(), def.displayName(), def.description(),
                signatures, variants,
                def.created(), def.updated(), def.lastSeen()
        );
    }
}