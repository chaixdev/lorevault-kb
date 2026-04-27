package com.lorevault.api.config;

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
    public Neo4jSchemaInitializer(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

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
    // ChapterEvent identity is derived from co-reference chains (SAME_EVENT links), not lexical name.
    // No (chapterId, normalizedName) scope-unique constraint — that would bake lexical sameness into storage.
    private static final String EVENT_MENTION_ID_UNIQUE =
            "CREATE CONSTRAINT event_mention_id_unique IF NOT EXISTS FOR (m:EventMention) REQUIRE m.id IS UNIQUE";
    private static final String CHAPTER_LOCATION_ID_UNIQUE =
            "CREATE CONSTRAINT chapter_location_id_unique IF NOT EXISTS FOR (cl:ChapterLocation) REQUIRE cl.id IS UNIQUE";
    private static final String CHAPTER_LOCATION_SCOPE_UNIQUE =
            "CREATE CONSTRAINT chapter_location_scope_unique IF NOT EXISTS FOR (cl:ChapterLocation) REQUIRE (cl.chapterId, cl.normalizedName) IS UNIQUE";
    private static final String BOOK_LOCATION_ID_UNIQUE =
            "CREATE CONSTRAINT book_location_id_unique IF NOT EXISTS FOR (bl:BookLocation) REQUIRE bl.id IS UNIQUE";
    private static final String BOOK_LOCATION_SCOPE_UNIQUE =
            "CREATE CONSTRAINT book_location_scope_unique IF NOT EXISTS FOR (bl:BookLocation) REQUIRE (bl.bookId, bl.normalizedName) IS UNIQUE";

    // Book reduction claim uniqueness (mutex for concurrent book-level reduction)
    private static final String BOOK_REDUCTION_CLAIM_BOOK_ID_UNIQUE =
            "CREATE CONSTRAINT book_reduction_claim_book_id_unique IF NOT EXISTS FOR (c:BookReductionClaim) REQUIRE c.bookId IS UNIQUE";

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
    private static final String EVENT_MENTION_CHAPTER_NAME_INDEX =
            "CREATE INDEX event_mention_chapter_name IF NOT EXISTS FOR (m:EventMention) ON (m.chapterId, m.normalizedName)";
    private static final String CHAPTER_EVENT_CHAPTER_NAME_INDEX =
            "CREATE INDEX chapter_event_chapter_name IF NOT EXISTS FOR (ce:ChapterEvent) ON (ce.chapterId, ce.normalizedName)";
    private static final String BOOK_LOCATION_BOOK_NAME_INDEX =
            "CREATE INDEX book_location_book_name IF NOT EXISTS FOR (bl:BookLocation) ON (bl.bookId, bl.normalizedName)";
    private static final String LLM_CALL_RECORD_JOB_ID_INDEX =
            "CREATE INDEX llm_call_record_job_id IF NOT EXISTS FOR (r:LlmCallRecord) ON (r.jobId)";
    private static final String LLM_CALL_RECORD_JOB_STEP_INDEX =
            "CREATE INDEX llm_call_record_job_step IF NOT EXISTS FOR (r:LlmCallRecord) ON (r.jobId, r.step)";
    private static final String LLM_CALL_RECORD_JOB_STEP_STATUS_INDEX =
            "CREATE INDEX llm_call_record_job_step_status IF NOT EXISTS FOR (r:LlmCallRecord) ON (r.jobId, r.step, r.statusRecordId)";

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
        results.add(executeConstraint(INGESTION_JOB_ID_UNIQUE, "IngestionJob.id unique"));
        results.add(executeConstraint(STATUS_RECORD_ID_UNIQUE, "StatusRecord.id unique"));
        results.add(executeConstraint(LLM_CALL_RECORD_ID_UNIQUE, "LlmCallRecord.id unique"));
        results.add(executeConstraint(CHAPTER_INDIVIDUAL_ID_UNIQUE, "ChapterIndividual.id unique"));
        results.add(executeConstraint(CHAPTER_INDIVIDUAL_SCOPE_UNIQUE, "ChapterIndividual(chapterId, normalizedName) unique"));
        results.add(executeConstraint(BOOK_INDIVIDUAL_ID_UNIQUE, "BookIndividual.id unique"));
        results.add(executeConstraint(BOOK_INDIVIDUAL_SCOPE_UNIQUE, "BookIndividual(bookId, normalizedName) unique"));
        results.add(executeConstraint(CHAPTER_EVENT_ID_UNIQUE, "ChapterEvent.id unique"));
        results.add(executeConstraint(EVENT_MENTION_ID_UNIQUE, "EventMention.id unique"));
        results.add(executeConstraint(CHAPTER_LOCATION_ID_UNIQUE, "ChapterLocation.id unique"));
        results.add(executeConstraint(CHAPTER_LOCATION_SCOPE_UNIQUE, "ChapterLocation(chapterId, normalizedName) unique"));
        results.add(executeConstraint(BOOK_LOCATION_ID_UNIQUE, "BookLocation.id unique"));
        results.add(executeConstraint(BOOK_LOCATION_SCOPE_UNIQUE, "BookLocation(bookId, normalizedName) unique"));
        results.add(executeConstraint(CHAPTER_CONTENT_HASH_UNIQUE, "Chapter.contentHash unique"));
        results.add(executeConstraint(BOOK_REDUCTION_CLAIM_BOOK_ID_UNIQUE, "BookReductionClaim.bookId unique"));
        
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
        results.add(executeIndex(EVENT_MENTION_CHAPTER_NAME_INDEX, "EventMention(chapterId, normalizedName)"));
        results.add(executeIndex(CHAPTER_EVENT_CHAPTER_NAME_INDEX, "ChapterEvent(chapterId, normalizedName)"));
        results.add(executeIndex(BOOK_LOCATION_BOOK_NAME_INDEX, "BookLocation(bookId, normalizedName)"));
        results.add(executeIndex(LLM_CALL_RECORD_JOB_ID_INDEX, "LlmCallRecord(jobId)"));
        results.add(executeIndex(LLM_CALL_RECORD_JOB_STEP_INDEX, "LlmCallRecord(jobId, step)"));
        results.add(executeIndex(LLM_CALL_RECORD_JOB_STEP_STATUS_INDEX, "LlmCallRecord(jobId, step, statusRecordId)"));
        
        // Event per-chapter ordering index
        results.add(executeIndex(EVENT_PER_CHAPTER_SCENE_INDEX, "Event(chapterId, sceneIndex)"));
        
        // Create vector search index
        results.add(executeVectorIndex(CHUNK_VECTOR_INDEX, "Chunk embedding vector index"));
        
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

    private String executeVectorIndex(String cypher, String description) {
        try {
            neo4jClient.query(cypher).run();
            log.debug("Ensured vector index: {}", description);
            return "ensured: " + description;
        } catch (Exception e) {
            log.error("Failed to create vector index {}: {}", description, e.getMessage());
            log.debug("Vector index creation requires Neo4j 5.x with vector capabilities");
            return "failed: " + description;
        }
    }

    // Vector search index - Neo4j 5.x vector index for semantic search
    private static final String CHUNK_VECTOR_INDEX =
            "CREATE VECTOR INDEX chunk_embedding_idx IF NOT EXISTS FOR (ch:Chunk) ON (ch.embedding) " +
            "OPTIONS {indexConfig: {`vector.dimensions`: 2560, `vector.similarity_function`: 'cosine'}}";
}
