package com.lorevault.api.application.port;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.domain.ingestion.LlmCallRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentPersistencePort {

    // Chapters
    Chapter createChapter(Chapter chapter);
    Optional<Chapter> findChapterById(UUID id);
    Optional<Chapter> findChapterByContentHash(String contentHash);
    boolean chapterExistsByContentHash(String contentHash);
    Chapter updateChapter(Chapter chapter);

    // Scenes
    Scene addSceneToChapter(UUID chapterId, Scene scene);
    List<Scene> addScenesToChapter(UUID chapterId, List<Scene> scenes);
    List<Scene> findScenesByChapterId(UUID chapterId);
    int deleteScenesByChapterId(UUID chapterId);

    // Chunks
    List<Chunk> addChunksToChapter(UUID chapterId, List<Chunk> chunks);
    Chunk addChunkToScene(UUID sceneId, Chunk chunk);
    List<Chunk> addChunksToScene(UUID sceneId, List<Chunk> chunks);
    List<Chunk> findChunksByChapterId(UUID chapterId);
    int deleteChunksByChapterId(UUID chapterId);
    boolean chunksExistForChapter(UUID chapterId);
    int countChunksByChapterId(UUID chapterId);
    Chunk updateChunk(Chunk chunk);
    List<Chunk> updateChunks(List<Chunk> chunks);
    List<Chunk> findAllChunksWithEmbeddings();

    /**
     * Find a single chunk by its ID.
     * Used by RAG to retrieve full chunk text for LLM context.
     */
    Optional<Chunk> findChunkById(UUID id);

    // Jobs
    IngestionJob createJob(IngestionJob job);
    IngestionJob createJobWithChapter(IngestionJob job, UUID chapterId);
    Optional<IngestionJob> findJob(UUID id);
    IngestionJob updateJob(IngestionJob job);
    Optional<IngestionJob> findMostRecentJobForChapter(UUID chapterId);
    boolean hasActiveJobForChapter(UUID chapterId);
    List<IngestionJob> findJobsByChapterIds(List<UUID> chapterIds);
    List<IngestionJob> findAllJobs();

    // Status Records
    StatusRecord addStatusRecord(UUID jobId, StatusRecord record);
    List<StatusRecord> findStatusHistoryForJob(UUID jobId);

    // LLM Call Records
    LlmCallRecord addLlmCallRecord(LlmCallRecord record);
    List<LlmCallRecord> findLlmCallsByJob(UUID jobId);
    List<LlmCallRecord> findLlmCallsByJobAndStep(UUID jobId, String step);

    // Queries
    List<Chapter> findChaptersByUniverse(String universe);
}
