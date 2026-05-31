package com.lorevault.api.config;

import com.lorevault.api.graph.event.persistence.ChapterEvent;
import com.lorevault.api.library.chunk.Chunk;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j implementation of GraphSchemaInitializer.
 * Creates minimal constraints and indexes needed for the current data model.
 */
@Component
public class Neo4jSchemaInitializer implements GraphSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(Neo4jSchemaInitializer.class);

    private final Neo4jClient neo4jClient;
    private final LoreVaultEmbeddingProperties embeddingProperties;

    public Neo4jSchemaInitializer(Neo4jClient neo4jClient, LoreVaultEmbeddingProperties embeddingProperties) {
        this.neo4jClient = neo4jClient;
        this.embeddingProperties = embeddingProperties;
    }

    // Unique constraints on business IDs
    private static final String CHAPTER_ID_UNIQUE =
            "CREATE CONSTRAINT chapter_id_unique IF NOT EXISTS FOR (c:Chapter) REQUIRE c.id IS UNIQUE";
    private static final String SCENE_ID_UNIQUE =
            "CREATE CONSTRAINT scene_id_unique IF NOT EXISTS FOR (s:Scene) REQUIRE s.id IS UNIQUE";
    private static final String CHUNK_ID_UNIQUE =
            "CREATE CONSTRAINT chunk_id_unique IF NOT EXISTS FOR (ch:Chunk) REQUIRE ch.id IS UNIQUE";

    // ── Durable orchestration constraints (new model) ──────────────────
    private static final String CHAPTER_INGESTION_JOB_ID_UNIQUE =
            "CREATE CONSTRAINT chapter_ingestion_job_id_unique IF NOT EXISTS FOR (j:ChapterIngestionJob) REQUIRE j.id IS UNIQUE";
    private static final String STAGE_ID_UNIQUE =
            "CREATE CONSTRAINT stage_id_unique IF NOT EXISTS FOR (s:Stage) REQUIRE s.id IS UNIQUE";
    private static final String STAGE_JOB_STEP_UNIQUE =
            "CREATE CONSTRAINT stage_job_step_unique IF NOT EXISTS FOR (s:Stage) REQUIRE (s.jobId, s.step) IS UNIQUE";
    private static final String STAGE_OUTPUT_ID_UNIQUE =
            "CREATE CONSTRAINT stage_output_id_unique IF NOT EXISTS FOR (o:StageOutput) REQUIRE o.id IS UNIQUE";
    private static final String LLM_CALL_RECORD_ID_UNIQUE =
            "CREATE CONSTRAINT llm_call_record_id_unique IF NOT EXISTS FOR (r:LlmCallRecord) REQUIRE r.id IS UNIQUE";
    private static final String CHAPTER_INDIVIDUAL_ID_UNIQUE =
            "CREATE CONSTRAINT chapter_individual_id_unique IF NOT EXISTS FOR (ci:ChapterIndividual) REQUIRE ci.id IS UNIQUE";
    private static final String CHAPTER_INDIVIDUAL_SCOPE_UNIQUE =
            "CREATE CONSTRAINT chapter_individual_scope_unique IF NOT EXISTS FOR (ci:ChapterIndividual) REQUIRE (ci.chapterId, ci.normalizedName) IS UNIQUE";
    private static final String BOOK_INDIVIDUAL_ID_UNIQUE =
            "CREATE CONSTRAINT book_individual_id_unique IF NOT EXISTS FOR (bi:BookIndividual) REQUIRE bi.id IS UNIQUE";
    private static final String BOOK_INDIVIDUAL_SCOPE_UNIQUE =
            "CREATE CONSTRAINT book_individual_scope_unique IF NOT EXISTS FOR (bi:BookIndividual) REQUIRE (bi.bookId, bi.normalizedName) IS UNIQUE";
    private static final String CHAPTER_EVENT_ID_UNIQUE =
            "CREATE CONSTRAINT chapter_event_id_unique IF NOT EXISTS FOR (ce:ChapterEvent) REQUIRE ce.id IS UNIQUE";
    private static final String BOOK_EVENT_ID_UNIQUE =
            "CREATE CONSTRAINT book_event_id_unique IF NOT EXISTS FOR (be:BookEvent) REQUIRE be.id IS UNIQUE";
    // ChapterEvent identity is derived from co-reference chains (SAME_EVENT links), not lexical name.
    // No (chapterId, normalizedName) scope-unique constraint — that would bake lexical sameness into storage.
    private static final String EVENT_MENTION_ID_UNIQUE =
            "CREATE CONSTRAINT event_mention_id_unique IF NOT EXISTS FOR (m:EventMention) REQUIRE m.id IS UNIQUE";
    private static final String INDIVIDUAL_MENTION_ID_UNIQUE =
            "CREATE CONSTRAINT individual_mention_id_unique IF NOT EXISTS FOR (m:IndividualMention) REQUIRE m.id IS UNIQUE";
    private static final String LOCATION_MENTION_ID_UNIQUE =
            "CREATE CONSTRAINT location_mention_id_unique IF NOT EXISTS FOR (m:LocationMention) REQUIRE m.id IS UNIQUE";
    private static final String OBJECT_MENTION_ID_UNIQUE =
            "CREATE CONSTRAINT object_mention_id_unique IF NOT EXISTS FOR (m:ObjectMention) REQUIRE m.id IS UNIQUE";
    private static final String COLLECTIVE_MENTION_ID_UNIQUE =
            "CREATE CONSTRAINT collective_mention_id_unique IF NOT EXISTS FOR (m:CollectiveMention) REQUIRE m.id IS UNIQUE";
    private static final String CHAPTER_LOCATION_ID_UNIQUE =
            "CREATE CONSTRAINT chapter_location_id_unique IF NOT EXISTS FOR (cl:ChapterLocation) REQUIRE cl.id IS UNIQUE";
    private static final String CHAPTER_LOCATION_SCOPE_UNIQUE =
            "CREATE CONSTRAINT chapter_location_scope_unique IF NOT EXISTS FOR (cl:ChapterLocation) REQUIRE (cl.chapterId, cl.normalizedName) IS UNIQUE";
    private static final String BOOK_LOCATION_ID_UNIQUE =
            "CREATE CONSTRAINT book_location_id_unique IF NOT EXISTS FOR (bl:BookLocation) REQUIRE bl.id IS UNIQUE";
    private static final String BOOK_LOCATION_SCOPE_UNIQUE =
            "CREATE CONSTRAINT book_location_scope_unique IF NOT EXISTS FOR (bl:BookLocation) REQUIRE (bl.bookId, bl.normalizedName) IS UNIQUE";
    private static final String CHAPTER_OBJECT_ID_UNIQUE =
            "CREATE CONSTRAINT chapter_object_id_unique IF NOT EXISTS FOR (co:ChapterObject) REQUIRE co.id IS UNIQUE";
    private static final String CHAPTER_OBJECT_SCOPE_UNIQUE =
            "CREATE CONSTRAINT chapter_object_scope_unique IF NOT EXISTS FOR (co:ChapterObject) REQUIRE (co.chapterId, co.normalizedName) IS UNIQUE";
    private static final String BOOK_OBJECT_ID_UNIQUE =
            "CREATE CONSTRAINT book_object_id_unique IF NOT EXISTS FOR (bo:BookObject) REQUIRE bo.id IS UNIQUE";
    private static final String BOOK_OBJECT_SCOPE_UNIQUE =
            "CREATE CONSTRAINT book_object_scope_unique IF NOT EXISTS FOR (bo:BookObject) REQUIRE (bo.bookId, bo.normalizedName) IS UNIQUE";
    private static final String CHAPTER_COLLECTIVE_ID_UNIQUE =
            "CREATE CONSTRAINT chapter_collective_id_unique IF NOT EXISTS FOR (cc:ChapterCollective) REQUIRE cc.id IS UNIQUE";
    private static final String CHAPTER_COLLECTIVE_SCOPE_UNIQUE =
            "CREATE CONSTRAINT chapter_collective_scope_unique IF NOT EXISTS FOR (cc:ChapterCollective) REQUIRE (cc.chapterId, cc.normalizedName) IS UNIQUE";
    private static final String BOOK_COLLECTIVE_ID_UNIQUE =
            "CREATE CONSTRAINT book_collective_id_unique IF NOT EXISTS FOR (bc:BookCollective) REQUIRE bc.id IS UNIQUE";
    private static final String BOOK_COLLECTIVE_SCOPE_UNIQUE =
            "CREATE CONSTRAINT book_collective_scope_unique IF NOT EXISTS FOR (bc:BookCollective) REQUIRE (bc.bookId, bc.normalizedName) IS UNIQUE";

    // RelationClaim constraints
    private static final String RELATION_CLAIM_ID_UNIQUE =
            "CREATE CONSTRAINT relation_claim_id_unique IF NOT EXISTS FOR (rc:RelationClaim) REQUIRE rc.id IS UNIQUE";

    // Book reduction claim uniqueness (mutex for concurrent book-level reduction per lane)
    private static final String DROP_LEGACY_BOOK_REDUCTION_CLAIM_BOOK_ID_UNIQUE =
            "DROP CONSTRAINT book_consolidation_claim_book_id_unique IF EXISTS";
    private static final String DELETE_LEGACY_BOOK_REDUCTION_CLAIMS =
            "MATCH (c:BookConsolidationClaim) WHERE c.id IS NULL OR c.claimedAt IS NULL DELETE c";
    private static final String BOOK_REDUCTION_CLAIM_ID_UNIQUE =
            "CREATE CONSTRAINT book_consolidation_claim_id_unique IF NOT EXISTS FOR (c:BookConsolidationClaim) REQUIRE c.id IS UNIQUE";

    // Content hash uniqueness
    private static final String CHAPTER_CONTENT_HASH_UNIQUE =
            "CREATE CONSTRAINT chapter_contentHash_unique IF NOT EXISTS FOR (c:Chapter) REQUIRE c.contentHash IS UNIQUE";

    // Event identity uniqueness (explicit, though Scene.id unique already enforces this)
    private static final String EVENT_ID_UNIQUE =
            "CREATE CONSTRAINT event_id_unique IF NOT EXISTS FOR (e:Event) REQUIRE e.id IS UNIQUE";

    // Read-path helper indexes (non-unique)
    private static final String CHAPTER_COORDS_INDEX =
            "CREATE INDEX chapter_coordinates IF NOT EXISTS FOR (c:Chapter) ON (c.universe, c.series, c.bookNumber, c.chapterNumber)";
    private static final String CHUNK_CONTENT_HASH_INDEX =
            "CREATE INDEX chunk_contentHash IF NOT EXISTS FOR (ch:Chunk) ON (ch.contentHash)";
    private static final String CHUNK_EMBEDDING_HASH_INDEX =
            "CREATE INDEX chunk_embeddingHash IF NOT EXISTS FOR (ch:Chunk) ON (ch.embeddingHash)";
    private static final String INDIVIDUAL_MENTION_CHAPTER_NAME_INDEX =
            "CREATE INDEX individual_mention_chapter_name IF NOT EXISTS FOR (m:IndividualMention) ON (m.chapterId, m.normalizedName)";
    private static final String BOOK_INDIVIDUAL_BOOK_NAME_INDEX =
            "CREATE INDEX book_individual_book_name IF NOT EXISTS FOR (bi:BookIndividual) ON (bi.bookId, bi.normalizedName)";
    private static final String LOCATION_MENTION_CHAPTER_NAME_INDEX =
            "CREATE INDEX location_mention_chapter_name IF NOT EXISTS FOR (m:LocationMention) ON (m.chapterId, m.normalizedName)";
    private static final String OBJECT_MENTION_CHAPTER_NAME_INDEX =
            "CREATE INDEX object_mention_chapter_name IF NOT EXISTS FOR (m:ObjectMention) ON (m.chapterId, m.normalizedName)";
    private static final String COLLECTIVE_MENTION_CHAPTER_NAME_INDEX =
            "CREATE INDEX collective_mention_chapter_name IF NOT EXISTS FOR (m:CollectiveMention) ON (m.chapterId, m.normalizedName)";
    private static final String EVENT_MENTION_CHAPTER_NAME_INDEX =
            "CREATE INDEX event_mention_chapter_name IF NOT EXISTS FOR (m:EventMention) ON (m.chapterId, m.normalizedName)";
    private static final String CHAPTER_EVENT_CHAPTER_NAME_INDEX =
            "CREATE INDEX chapter_event_chapter_name IF NOT EXISTS FOR (ce:ChapterEvent) ON (ce.chapterId, ce.normalizedName)";
    private static final String BOOK_EVENT_BOOK_NAME_INDEX =
            "CREATE INDEX book_event_book_name IF NOT EXISTS FOR (be:BookEvent) ON (be.bookId, be.normalizedName)";
    private static final String BOOK_LOCATION_BOOK_NAME_INDEX =
            "CREATE INDEX book_location_book_name IF NOT EXISTS FOR (bl:BookLocation) ON (bl.bookId, bl.normalizedName)";
    private static final String CHAPTER_OBJECT_CHAPTER_NAME_INDEX =
            "CREATE INDEX chapter_object_chapter_name IF NOT EXISTS FOR (co:ChapterObject) ON (co.chapterId, co.normalizedName)";
    private static final String BOOK_OBJECT_BOOK_NAME_INDEX =
            "CREATE INDEX book_object_book_name IF NOT EXISTS FOR (bo:BookObject) ON (bo.bookId, bo.normalizedName)";
    private static final String CHAPTER_COLLECTIVE_CHAPTER_NAME_INDEX =
            "CREATE INDEX chapter_collective_chapter_name IF NOT EXISTS FOR (cc:ChapterCollective) ON (cc.chapterId, cc.normalizedName)";
    private static final String BOOK_COLLECTIVE_BOOK_NAME_INDEX =
            "CREATE INDEX book_collective_book_name IF NOT EXISTS FOR (bc:BookCollective) ON (bc.bookId, bc.normalizedName)";
    private static final String LLM_CALL_RECORD_JOB_ID_INDEX =
            "CREATE INDEX llm_call_record_job_id IF NOT EXISTS FOR (r:LlmCallRecord) ON (r.jobId)";
    private static final String LLM_CALL_RECORD_JOB_STEP_INDEX =
            "CREATE INDEX llm_call_record_job_step IF NOT EXISTS FOR (r:LlmCallRecord) ON (r.jobId, r.step)";
    private static final String LLM_CALL_RECORD_JOB_STEP_STATUS_INDEX =
            "CREATE INDEX llm_call_record_job_step_status IF NOT EXISTS FOR (r:LlmCallRecord) ON (r.jobId, r.step, r.statusRecordId)";

    // ── Durable orchestration indexes ─────────────────────────────────
    private static final String STAGE_OUTPUT_CHAPTER_STEP_INDEX =
            "CREATE INDEX stage_output_chapter_step IF NOT EXISTS FOR (o:StageOutput) ON (o.chapterId, o.step)";
    private static final String STAGE_OUTPUT_BOOK_STEP_INDEX =
            "CREATE INDEX stage_output_book_step IF NOT EXISTS FOR (o:StageOutput) ON (o.bookId, o.step)";
    private static final String LLM_CALL_RECORD_JOB_STEP_STAGE_INDEX =
            "CREATE INDEX llm_call_record_job_step_stage IF NOT EXISTS FOR (r:LlmCallRecord) ON (r.jobId, r.step, r.stageId)";

    // RelationClaim query indexes
    private static final String RELATION_CLAIM_CHAPTER_DEFKEY_INDEX =
            "CREATE INDEX relation_claim_chapter_defkey IF NOT EXISTS FOR (rc:RelationClaim) ON (rc.chapterId, rc.definitionKey)";
    private static final String RELATION_CLAIM_BOOK_DEFKEY_INDEX =
            "CREATE INDEX relation_claim_book_defkey IF NOT EXISTS FOR (rc:RelationClaim) ON (rc.bookId, rc.definitionKey)";

    // Per-chapter ordering index for events
    private static final String EVENT_PER_CHAPTER_SCENE_INDEX =
            "CREATE INDEX event_per_chapter_scene_idx IF NOT EXISTS FOR (e:Event) ON (e.chapterId, e.sceneIndex)";



    @Override
    public void ensureMinimalSchema() {
        List<String> results = new ArrayList<>();
        
        // Create unique constraints
        results.add(executeConstraint(CHAPTER_ID_UNIQUE, "Chapter.id unique"));
        results.add(executeConstraint(SCENE_ID_UNIQUE, "Scene.id unique"));
        results.add(executeConstraint(CHUNK_ID_UNIQUE, "Chunk.id unique"));
        results.add(executeConstraint(LLM_CALL_RECORD_ID_UNIQUE, "LlmCallRecord.id unique"));
        results.add(executeConstraint(CHAPTER_INDIVIDUAL_ID_UNIQUE, "ChapterIndividual.id unique"));
        results.add(executeConstraint(CHAPTER_INDIVIDUAL_SCOPE_UNIQUE, "ChapterIndividual(chapterId, normalizedName) unique"));
        results.add(executeConstraint(BOOK_INDIVIDUAL_ID_UNIQUE, "BookIndividual.id unique"));
        results.add(executeConstraint(BOOK_INDIVIDUAL_SCOPE_UNIQUE, "BookIndividual(bookId, normalizedName) unique"));
        results.add(executeConstraint(CHAPTER_EVENT_ID_UNIQUE, "ChapterEvent.id unique"));
        results.add(executeConstraint(BOOK_EVENT_ID_UNIQUE, "BookEvent.id unique"));
        results.add(executeConstraint(EVENT_MENTION_ID_UNIQUE, "EventMention.id unique"));
        results.add(executeConstraint(INDIVIDUAL_MENTION_ID_UNIQUE, "IndividualMention.id unique"));
        results.add(executeConstraint(LOCATION_MENTION_ID_UNIQUE, "LocationMention.id unique"));
        results.add(executeConstraint(OBJECT_MENTION_ID_UNIQUE, "ObjectMention.id unique"));
        results.add(executeConstraint(COLLECTIVE_MENTION_ID_UNIQUE, "CollectiveMention.id unique"));
        results.add(executeConstraint(CHAPTER_LOCATION_ID_UNIQUE, "ChapterLocation.id unique"));
        results.add(executeConstraint(CHAPTER_LOCATION_SCOPE_UNIQUE, "ChapterLocation(chapterId, normalizedName) unique"));
        results.add(executeConstraint(BOOK_LOCATION_ID_UNIQUE, "BookLocation.id unique"));
        results.add(executeConstraint(BOOK_LOCATION_SCOPE_UNIQUE, "BookLocation(bookId, normalizedName) unique"));
        results.add(executeConstraint(CHAPTER_OBJECT_ID_UNIQUE, "ChapterObject.id unique"));
        results.add(executeConstraint(CHAPTER_OBJECT_SCOPE_UNIQUE, "ChapterObject(chapterId, normalizedName) unique"));
        results.add(executeConstraint(BOOK_OBJECT_ID_UNIQUE, "BookObject.id unique"));
        results.add(executeConstraint(BOOK_OBJECT_SCOPE_UNIQUE, "BookObject(bookId, normalizedName) unique"));
        results.add(executeConstraint(CHAPTER_COLLECTIVE_ID_UNIQUE, "ChapterCollective.id unique"));
        results.add(executeConstraint(CHAPTER_COLLECTIVE_SCOPE_UNIQUE, "ChapterCollective(chapterId, normalizedName) unique"));
        results.add(executeConstraint(BOOK_COLLECTIVE_ID_UNIQUE, "BookCollective.id unique"));
        results.add(executeConstraint(BOOK_COLLECTIVE_SCOPE_UNIQUE, "BookCollective(bookId, normalizedName) unique"));
        results.add(executeConstraint(CHAPTER_CONTENT_HASH_UNIQUE, "Chapter.contentHash unique"));
        results.add(executeConstraint(DROP_LEGACY_BOOK_REDUCTION_CLAIM_BOOK_ID_UNIQUE, "Legacy BookConsolidationClaim.bookId unique dropped"));
        results.add(executeConstraint(DELETE_LEGACY_BOOK_REDUCTION_CLAIMS, "Legacy BookConsolidationClaim rows deleted"));
        results.add(executeConstraint(BOOK_REDUCTION_CLAIM_ID_UNIQUE, "BookConsolidationClaim.id unique"));
        results.add(executeConstraint(RELATION_CLAIM_ID_UNIQUE, "RelationClaim.id unique"));

        // Durable orchestration constraints
        results.add(executeConstraint(CHAPTER_INGESTION_JOB_ID_UNIQUE, "ChapterIngestionJob.id unique"));
        results.add(executeConstraint(STAGE_ID_UNIQUE, "Stage.id unique"));
        results.add(executeConstraint(STAGE_JOB_STEP_UNIQUE, "Stage(jobId, step) unique"));
        results.add(executeConstraint(STAGE_OUTPUT_ID_UNIQUE, "StageOutput.id unique"));
        
        // Event identity constraint
        results.add(executeConstraint(EVENT_ID_UNIQUE, "Event.id unique"));
        
        // Create non-unique indexes
        results.add(executeIndex(CHAPTER_COORDS_INDEX, "Chapter coordinates"));
        results.add(executeIndex(CHUNK_CONTENT_HASH_INDEX, "Chunk.contentHash"));
        results.add(executeIndex(CHUNK_EMBEDDING_HASH_INDEX, "Chunk.embeddingHash"));
        results.add(executeIndex(INDIVIDUAL_MENTION_CHAPTER_NAME_INDEX, "IndividualMention(chapterId, normalizedName)"));
        results.add(executeIndex(BOOK_INDIVIDUAL_BOOK_NAME_INDEX, "BookIndividual(bookId, normalizedName)"));
        results.add(executeIndex(LOCATION_MENTION_CHAPTER_NAME_INDEX, "LocationMention(chapterId, normalizedName)"));
        results.add(executeIndex(OBJECT_MENTION_CHAPTER_NAME_INDEX, "ObjectMention(chapterId, normalizedName)"));
        results.add(executeIndex(COLLECTIVE_MENTION_CHAPTER_NAME_INDEX, "CollectiveMention(chapterId, normalizedName)"));
        results.add(executeIndex(EVENT_MENTION_CHAPTER_NAME_INDEX, "EventMention(chapterId, normalizedName)"));
        results.add(executeIndex(CHAPTER_EVENT_CHAPTER_NAME_INDEX, "ChapterEvent(chapterId, normalizedName)"));
        results.add(executeIndex(BOOK_EVENT_BOOK_NAME_INDEX, "BookEvent(bookId, normalizedName)"));
        results.add(executeIndex(BOOK_LOCATION_BOOK_NAME_INDEX, "BookLocation(bookId, normalizedName)"));
        results.add(executeIndex(CHAPTER_OBJECT_CHAPTER_NAME_INDEX, "ChapterObject(chapterId, normalizedName)"));
        results.add(executeIndex(BOOK_OBJECT_BOOK_NAME_INDEX, "BookObject(bookId, normalizedName)"));
        results.add(executeIndex(CHAPTER_COLLECTIVE_CHAPTER_NAME_INDEX, "ChapterCollective(chapterId, normalizedName)"));
        results.add(executeIndex(BOOK_COLLECTIVE_BOOK_NAME_INDEX, "BookCollective(bookId, normalizedName)"));
        results.add(executeIndex(LLM_CALL_RECORD_JOB_ID_INDEX, "LlmCallRecord(jobId)"));
        results.add(executeIndex(LLM_CALL_RECORD_JOB_STEP_INDEX, "LlmCallRecord(jobId, step)"));
        results.add(executeIndex(LLM_CALL_RECORD_JOB_STEP_STATUS_INDEX, "LlmCallRecord(jobId, step, statusRecordId)"));
        
        // Durable orchestration indexes
        results.add(executeIndex(STAGE_OUTPUT_CHAPTER_STEP_INDEX, "StageOutput(chapterId, step)"));
        results.add(executeIndex(STAGE_OUTPUT_BOOK_STEP_INDEX, "StageOutput(bookId, step)"));
        results.add(executeIndex(LLM_CALL_RECORD_JOB_STEP_STAGE_INDEX, "LlmCallRecord(jobId, step, stageId)"));
        
        // RelationClaim indexes
        results.add(executeIndex(RELATION_CLAIM_CHAPTER_DEFKEY_INDEX, "RelationClaim(chapterId, definitionKey)"));
        results.add(executeIndex(RELATION_CLAIM_BOOK_DEFKEY_INDEX, "RelationClaim(bookId, definitionKey)"));

        // Event per-chapter ordering index
        results.add(executeIndex(EVENT_PER_CHAPTER_SCENE_INDEX, "Event(chapterId, sceneIndex)"));


        
        // Create vector search indexes
        results.add(ensureChunkVectorIndex());
        results.add(ensureChapterEventVectorIndex());
        
        long successful = results.stream().filter(r -> r.contains("ensured")).count();
        long failed = results.stream().filter(r -> r.contains("failed")).count();
        
        log.info("Schema initialization complete: {} ensured, {} failed", successful, failed);
        if (failed > 0) {
            log.error("Some schema artifacts failed to create. Check logs above for details.");
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
            log.error("Failed to create constraint {}: {}", description, e.getMessage());
            if (description.startsWith("BookConsolidationClaim") || description.startsWith("Legacy BookConsolidationClaim")) {
                throw new IllegalStateException("Critical schema operation failed: " + description, e);
            }
            return "failed: " + description;
        }
    }

    private String executeIndex(String cypher, String description) {
        try {
            neo4jClient.query(cypher).run();
            log.debug("Ensured index: {}", description);
            return "ensured: " + description;
        } catch (Exception e) {
            log.error("Failed to create index {}: {}", description, e.getMessage());
            return "failed: " + description;
        }
    }



    private String ensureChunkVectorIndex() {
        String description = "Chunk embedding vector index";
        try {
            Integer existingDimensions = existingChunkVectorDimensions();
            int expectedDimensions = embeddingProperties.model().dimensions();

            if (existingDimensions != null && existingDimensions != expectedDimensions) {
                log.warn(
                        "Rebuilding vector index {} due to dimension drift: existing={}, expected={}",
                        Chunk.VECTOR_INDEX_NAME,
                        existingDimensions,
                        expectedDimensions
                );
                neo4jClient.query("DROP INDEX " + Chunk.VECTOR_INDEX_NAME + " IF EXISTS").run();
            }

            neo4jClient.query(chunkVectorIndexCypher(expectedDimensions)).run();
            log.debug("Ensured vector index: {}", description);
            return "ensured: " + description;
        } catch (Exception e) {
            log.error("Failed to create vector index {}: {}", description, e.getMessage());
            log.debug("Vector index creation requires Neo4j 5.x with vector capabilities");
            return "failed: " + description;
        }
    }

    private Integer existingChunkVectorDimensions() {
        return neo4jClient.query(
                        "SHOW VECTOR INDEXES YIELD name, options " +
                        "WHERE name = $indexName " +
                        "RETURN options.indexConfig.`vector.dimensions` AS dimensions"
                )
                .bind(Chunk.VECTOR_INDEX_NAME).to("indexName")
                .fetch()
                .one()
                .map(row -> row.get("dimensions"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .orElse(null);
    }

    private String chunkVectorIndexCypher(int dimensions) {
        return "CREATE VECTOR INDEX " + Chunk.VECTOR_INDEX_NAME + " IF NOT EXISTS FOR (ch:Chunk) ON (ch.embedding) " +
               "OPTIONS {indexConfig: {`vector.dimensions`: " + dimensions + ", `vector.similarity_function`: 'cosine'}}";
    }

    private String ensureChapterEventVectorIndex() {
        String description = "ChapterEvent embedding vector index";
        try {
            Integer existingDimensions = existingChapterEventVectorDimensions();
            int expectedDimensions = embeddingProperties.model().dimensions();

            if (existingDimensions != null && existingDimensions != expectedDimensions) {
                log.warn(
                        "Rebuilding vector index {} due to dimension drift: existing={}, expected={}",
                        ChapterEvent.VECTOR_INDEX_NAME,
                        existingDimensions,
                        expectedDimensions
                );
                neo4jClient.query("DROP INDEX " + ChapterEvent.VECTOR_INDEX_NAME + " IF EXISTS").run();
            }

            neo4jClient.query(chapterEventVectorIndexCypher(expectedDimensions)).run();
            log.debug("Ensured vector index: {}", description);
            return "ensured: " + description;
        } catch (Exception e) {
            log.error("Failed to create vector index {}: {}", description, e.getMessage());
            log.debug("Vector index creation requires Neo4j 5.x with vector capabilities");
            return "failed: " + description;
        }
    }

    private Integer existingChapterEventVectorDimensions() {
        return neo4jClient.query(
                        "SHOW VECTOR INDEXES YIELD name, options " +
                        "WHERE name = $indexName " +
                        "RETURN options.indexConfig.`vector.dimensions` AS dimensions"
                )
                .bind(ChapterEvent.VECTOR_INDEX_NAME).to("indexName")
                .fetch()
                .one()
                .map(row -> row.get("dimensions"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .orElse(null);
    }

    private String chapterEventVectorIndexCypher(int dimensions) {
        return "CREATE VECTOR INDEX " + ChapterEvent.VECTOR_INDEX_NAME + " IF NOT EXISTS FOR (ce:ChapterEvent) ON (ce.embedding) " +
               "OPTIONS {indexConfig: {`vector.dimensions`: " + dimensions + ", `vector.similarity_function`: 'cosine'}}";
    }

}
