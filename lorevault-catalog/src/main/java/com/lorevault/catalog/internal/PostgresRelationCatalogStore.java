package com.lorevault.catalog.internal;

import com.lorevault.catalog.EmbeddingFunction;
import com.lorevault.catalog.RelationCatalogDefinition;
import com.lorevault.catalog.RelationCatalogId;
import com.lorevault.catalog.RelationKindSignature;
import com.lorevault.catalog.RelationQuery;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL-backed catalog store with idempotent find-or-create semantics
 * and pgvector-based semantic similarity matching.
 *
 * Uses ON CONFLICT DO NOTHING on the unique definition_key constraint
 * so that concurrent resolve() calls for the same key produce exactly
 * one definition row — the winner inserts, the loser re-reads.
 */
@Repository
@ConditionalOnProperty(name = "lorevault.catalog.enabled", havingValue = "true")
class PostgresRelationCatalogStore implements RelationCatalogStore {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresRelationCatalogStore.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EmbeddingFunction embeddingFunction;
    private final ConcurrentHashMap<String, CachedEmbedding> embeddingCache = new ConcurrentHashMap<>();

    private static final float MAX_COSINE_DISTANCE = 0.25f;
    private static final int TOP_MATCH_LIMIT = 3;
    private static final double NEAR_MISS_RATIO = 1.10d;
    private static final long EMBEDDING_CACHE_TTL_MILLIS = 5 * 60 * 1000L;

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

    private static final RowMapper<RelationCatalogDefinition> DEFINITION_WITH_SCORE_ROW_MAPPER = (rs, rowNum) ->
            new RelationCatalogDefinition(
                    new RelationCatalogId(rs.getObject("id", UUID.class)),
                    rs.getString("definition_key"),
                    rs.getString("display_name"),
                    rs.getString("description"),
                    List.of(),
                    List.of(),
                    rs.getObject("created", OffsetDateTime.class).toInstant(),
                    rs.getObject("updated", OffsetDateTime.class).toInstant(),
                    rs.getObject("last_seen", OffsetDateTime.class).toInstant()
            );

    private static final RowMapper<RelationKindSignature> SIGNATURE_ROW_MAPPER = (rs, rowNum) ->
            new RelationKindSignature(
                    rs.getString("subject_kind"),
                    rs.getString("object_kind")
            );

    PostgresRelationCatalogStore(NamedParameterJdbcTemplate jdbcTemplate,
                                 EmbeddingFunction embeddingFunction) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingFunction = embeddingFunction;
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
            if (def != null) {
                touch(def.id().value());
            }
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
    public Optional<RelationCatalogDefinition> findBestMatch(RelationQuery query) {
        String searchText = buildEmbeddingText(query);
        float[] queryVector = embeddingFor(searchText);

        var params = new MapSqlParameterSource()
                .addValue("queryVector", pgvectorString(queryVector))
                .addValue("maxDistance", MAX_COSINE_DISTANCE)
                .addValue("limit", TOP_MATCH_LIMIT);

        List<CandidateDefinition> candidates = jdbcTemplate.query(
                """
                SELECT id, definition_key, display_name, description, created, updated, last_seen,
                       embedding <=> :queryVector::vector AS cosine_distance
                FROM catalog_definition
                WHERE embedding IS NOT NULL
                  AND embedding <=> :queryVector::vector < :maxDistance
                ORDER BY embedding <=> :queryVector::vector
                LIMIT :limit
                """,
                params,
                CANDIDATE_ROW_MAPPER
        );
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        logNearMisses(query, candidates);
        RelationCatalogDefinition def = candidates.getFirst().definition();
        touch(def.id().value());
        return Optional.of(enrichWithRelations(def));
    }

    @Override
    public RelationCatalogDefinition create(RelationQuery query) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String displayName = query.rawName() != null ? query.rawName() : query.definitionKey();
        String searchText = buildEmbeddingText(query);
        float[] embedding = embeddingFor(searchText);
        String pgEmbedding = pgvectorString(embedding);

        OffsetDateTime nowOdt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("definitionKey", query.definitionKey())
                .addValue("displayName", displayName)
                .addValue("description", query.description())
                .addValue("embedding", pgEmbedding)
                .addValue("now", nowOdt);

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO catalog_definition (id, definition_key, display_name, description, embedding, created, updated, last_seen)
                VALUES (:id, :definitionKey, :displayName, :description, :embedding::vector, :now, :now, :now)
                ON CONFLICT (definition_key) DO NOTHING
                """,
                params
        );

        if (inserted == 0) {
            return findByDefinitionKey(query.definitionKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Definition key '" + query.definitionKey() + "' not found after ON CONFLICT DO NOTHING"));
        }

        // Add signature and variant rows
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
     * Build the text to embed for semantic similarity search.
     * Format: "{description}: {rawName}"
     * Entity-kind compatibility is a separate post-filter or signature-matching concern,
     * not part of the embedding vector.
     */
    private static String buildEmbeddingText(RelationQuery query) {
        StringBuilder sb = new StringBuilder();
        if (query.description() != null && !query.description().isBlank()) {
            sb.append(query.description()).append(": ");
        }
        sb.append(query.rawName() != null ? query.rawName() : query.definitionKey());
        return sb.toString();
    }

    private float[] embeddingFor(String searchText) {
        long now = System.currentTimeMillis();
        CachedEmbedding cached = embeddingCache.get(searchText);
        if (cached != null && now - cached.createdAtMillis() <= EMBEDDING_CACHE_TTL_MILLIS) {
            return Arrays.copyOf(cached.vector(), cached.vector().length);
        }
        float[] vector = embeddingFunction.embed(searchText);
        embeddingCache.put(searchText, new CachedEmbedding(Arrays.copyOf(vector, vector.length), now));
        return vector;
    }

    private void logNearMisses(RelationQuery query, List<CandidateDefinition> candidates) {
        if (candidates.size() < 2 || !LOG.isDebugEnabled()) {
            return;
        }
        CandidateDefinition best = candidates.getFirst();
        CandidateDefinition second = candidates.get(1);
        if (second.cosineDistance() <= best.cosineDistance() * NEAR_MISS_RATIO) {
            LOG.debug(
                    "[RELATION_CATALOG] Near-match ambiguity: queryKey={}, queryName={}, bestKey={}, bestDistance={}, secondKey={}, secondDistance={}, candidateCount={}",
                    query.definitionKey(),
                    query.rawName(),
                    best.definition().definitionKey(),
                    best.cosineDistance(),
                    second.definition().definitionKey(),
                    second.cosineDistance(),
                    candidates.size()
            );
        }
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

    /**
     * Touch the last_seen timestamp so staleness/eviction features
     * can distinguish active vs. dormant definitions.
     */
    private void touch(UUID id) {
        jdbcTemplate.update(
                "UPDATE catalog_definition SET last_seen = NOW() WHERE id = :id",
                new MapSqlParameterSource("id", id)
        );
    }

    /**
     * Format a float array as a pgvector-compatible string: '[1.0,2.0,3.0]'.
     * No outer quotes — the JDBC driver handles quoting. Use ::vector cast in SQL.
     */
    private static String pgvectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private record CachedEmbedding(float[] vector, long createdAtMillis) {}

    private record CandidateDefinition(RelationCatalogDefinition definition, double cosineDistance) {}

    private static final RowMapper<CandidateDefinition> CANDIDATE_ROW_MAPPER = (rs, rowNum) -> new CandidateDefinition(
            DEFINITION_WITH_SCORE_ROW_MAPPER.mapRow(rs, rowNum),
            rs.getDouble("cosine_distance")
    );
}
