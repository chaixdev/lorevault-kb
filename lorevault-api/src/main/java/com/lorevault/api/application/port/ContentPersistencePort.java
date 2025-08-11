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
    List<ChunkNode> findChunksByChapterId(UUID chapterId);
    int deleteChunksByChapterId(UUID chapterId);
    boolean chunksExistForChapter(UUID chapterId);
    int countChunksByChapterId(UUID chapterId);

    // Jobs
    IngestionJobNode createJob(IngestionJobNode jobNode);
    Optional<IngestionJobNode> findJob(UUID id);
    IngestionJobNode updateJob(IngestionJobNode jobNode);
    Optional<IngestionJobNode> findMostRecentJobForChapter(UUID chapterId);
    boolean hasActiveJobForChapter(UUID chapterId);
    List<IngestionJobNode> findJobsByChapterIds(List<UUID> chapterIds);
    List<IngestionJobNode> findAllJobs();

    // Status Records
    StatusRecordNode addStatusRecord(UUID jobId, StatusRecordNode recordNode);
    List<StatusRecordNode> findRecentStatusRecords(UUID jobId, int limit);

    // Queries
    List<ChapterNode> findChaptersByUniverse(String universe);
}
