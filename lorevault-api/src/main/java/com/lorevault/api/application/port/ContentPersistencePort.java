package com.lorevault.api.application.port;

import com.lorevault.api.infrastructure.persistence.neo4j.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentPersistencePort {

    // Chapters
    ChapterNode createChapter(ChapterNode chapter);
    Optional<ChapterNode> findChapterById(UUID id);
    Optional<ChapterNode> findChapterByContentHash(String contentHash);
    boolean chapterExistsByContentHash(String contentHash);
    ChapterNode updateChapter(ChapterNode chapter);

    // Scenes
    SceneNode addSceneToChapter(UUID chapterId, SceneNode scene);
    List<SceneNode> addScenesToChapter(UUID chapterId, List<SceneNode> scenes);
    List<SceneNode> findScenesByChapterId(UUID chapterId);
    int deleteScenesByChapterId(UUID chapterId);

    // Chunks
    List<ChunkNode> addChunksToChapter(UUID chapterId, List<ChunkNode> chunks);
    ChunkNode addChunkToScene(UUID sceneId, ChunkNode chunk);
    List<ChunkNode> addChunksToScene(UUID sceneId, List<ChunkNode> chunks);
    List<ChunkNode> findChunksByChapterId(UUID chapterId);
    int deleteChunksByChapterId(UUID chapterId);
    boolean chunksExistForChapter(UUID chapterId);
    int countChunksByChapterId(UUID chapterId);
    ChunkNode updateChunk(ChunkNode chunk);
    List<ChunkNode> updateChunks(List<ChunkNode> chunks);

    // Jobs
    IngestionJobNode createJob(IngestionJobNode jobNode);
    IngestionJobNode createJobWithChapter(IngestionJobNode jobNode, UUID chapterId);
    Optional<IngestionJobNode> findJob(UUID id);
    IngestionJobNode updateJob(IngestionJobNode jobNode);
    Optional<IngestionJobNode> findMostRecentJobForChapter(UUID chapterId);
    boolean hasActiveJobForChapter(UUID chapterId);
    List<IngestionJobNode> findJobsByChapterIds(List<UUID> chapterIds);
    List<IngestionJobNode> findAllJobs();

    // Status Records
    StatusRecordNode addStatusRecord(UUID jobId, StatusRecordNode recordNode);
    List<StatusRecordNode> findStatusHistoryForJob(UUID jobId);

    // Queries
    List<ChapterNode> findChaptersByUniverse(String universe);
}
