package com.lorevault.api.schema.neo4j;

import com.lorevault.api.schema.GraphSchemaInitializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j implementation of GraphSchemaInitializer.
 * Creates minimal constraints and indexes needed for the current data model.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Neo4jSchemaInitializer implements GraphSchemaInitializer {

    private final Neo4jClient neo4jClient;

    // Unique constraints on business IDs
    private static final String CHAPTER_ID_UNIQUE =
            "CREATE CONSTRAINT chapter_id_unique IF NOT EXISTS FOR (c:Chapter) REQUIRE c.id IS UNIQUE";
    private static final String SCENE_ID_UNIQUE =
            "CREATE CONSTRAINT scene_id_unique IF NOT EXISTS FOR (s:Scene) REQUIRE s.id IS UNIQUE";
    private static final String CHUNK_ID_UNIQUE =
            "CREATE CONSTRAINT chunk_id_unique IF NOT EXISTS FOR (ch:Chunk) REQUIRE ch.id IS UNIQUE";
    private static final String INGESTION_JOB_ID_UNIQUE =
            "CREATE CONSTRAINT ingestion_job_id_unique IF NOT EXISTS FOR (j:IngestionJob) REQUIRE j.id IS UNIQUE";
    private static final String STATUS_RECORD_ID_UNIQUE =
            "CREATE CONSTRAINT status_record_id_unique IF NOT EXISTS FOR (sr:StatusRecord) REQUIRE sr.id IS UNIQUE";

    // Content hash uniqueness
    private static final String CHAPTER_CONTENT_HASH_UNIQUE =
            "CREATE CONSTRAINT chapter_contentHash_unique IF NOT EXISTS FOR (c:Chapter) REQUIRE c.contentHash IS UNIQUE";

    // Read-path helper indexes (non-unique)
    private static final String CHAPTER_COORDS_INDEX =
            "CREATE INDEX chapter_coordinates IF NOT EXISTS FOR (c:Chapter) ON (c.universe, c.series, c.bookNumber, c.chapterNumber)";
    private static final String CHUNK_CONTENT_HASH_INDEX =
            "CREATE INDEX chunk_contentHash IF NOT EXISTS FOR (ch:Chunk) ON (ch.contentHash)";
    private static final String CHUNK_EMBEDDING_HASH_INDEX =
            "CREATE INDEX chunk_embeddingHash IF NOT EXISTS FOR (ch:Chunk) ON (ch.embeddingHash)";

    @Override
    public void ensureMinimalSchema() {
        List<String> results = new ArrayList<>();
        
        // Create unique constraints
        results.add(executeConstraint(CHAPTER_ID_UNIQUE, "Chapter.id unique"));
        results.add(executeConstraint(SCENE_ID_UNIQUE, "Scene.id unique"));
        results.add(executeConstraint(CHUNK_ID_UNIQUE, "Chunk.id unique"));
        results.add(executeConstraint(INGESTION_JOB_ID_UNIQUE, "IngestionJob.id unique"));
        results.add(executeConstraint(STATUS_RECORD_ID_UNIQUE, "StatusRecord.id unique"));
        results.add(executeConstraint(CHAPTER_CONTENT_HASH_UNIQUE, "Chapter.contentHash unique"));
        
        // Create non-unique indexes
        results.add(executeIndex(CHAPTER_COORDS_INDEX, "Chapter coordinates"));
        results.add(executeIndex(CHUNK_CONTENT_HASH_INDEX, "Chunk.contentHash"));
        results.add(executeIndex(CHUNK_EMBEDDING_HASH_INDEX, "Chunk.embeddingHash"));
        
        long successful = results.stream().filter(r -> r.contains("ensured")).count();
        long failed = results.stream().filter(r -> r.contains("failed")).count();
        
        log.info("Schema initialization complete: {} ensured, {} failed", successful, failed);
        if (failed > 0) {
            log.warn("Some schema artifacts failed to create. Check logs above for details.");
        }
    }

    @Override
    public SchemaReport validateMinimalSchema() {
        // For now, return a simple summary. Could be enhanced later to actually check existence.
        return new SchemaReport(true, true, "Validation not yet implemented - assuming all exists");
    }

    private String executeConstraint(String cypher, String description) {
        try {
            neo4jClient.query(cypher).run();
            log.debug("Ensured constraint: {}", description);
            return "ensured: " + description;
        } catch (Exception e) {
            log.warn("Failed to create constraint {}: {}", description, e.getMessage());
            return "failed: " + description;
        }
    }

    private String executeIndex(String cypher, String description) {
        try {
            neo4jClient.query(cypher).run();
            log.debug("Ensured index: {}", description);
            return "ensured: " + description;
        } catch (Exception e) {
            log.warn("Failed to create index {}: {}", description, e.getMessage());
            return "failed: " + description;
        }
    }
}
