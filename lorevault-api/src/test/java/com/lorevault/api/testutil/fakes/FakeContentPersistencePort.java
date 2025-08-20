package com.lorevault.api.testutil.fakes;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.infrastructure.persistence.neo4j.model.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory fake implementation of ContentPersistencePort for unit/service tests.
 */
public class FakeContentPersistencePort implements ContentPersistencePort {

    public final Map<UUID, ChapterNode> chapters = new ConcurrentHashMap<>();
    public final Map<UUID, List<SceneNode>> scenesByChapter = new ConcurrentHashMap<>();
    public final Map<UUID, List<ChunkNode>> chunksByChapter = new ConcurrentHashMap<>();
    public final Map<UUID, IngestionJobNode> jobs = new ConcurrentHashMap<>();
    public final Map<UUID, List<StatusRecordNode>> statusByJob = new ConcurrentHashMap<>();

    @Override
    public ChapterNode createChapter(ChapterNode chapter) {
        if (chapter.getId() == null) chapter.setId(UUID.randomUUID());
        chapters.put(chapter.getId(), chapter);
        return chapter;
    }

    @Override
    public Optional<ChapterNode> findChapterById(UUID id) {
        return Optional.ofNullable(chapters.get(id));
    }

    @Override
    public Optional<ChapterNode> findChapterByContentHash(String contentHash) {
        return chapters.values().stream().filter(c -> Objects.equals(c.getContentHash(), contentHash)).findFirst();
    }

    @Override
    public boolean chapterExistsByContentHash(String contentHash) {
        return findChapterByContentHash(contentHash).isPresent();
    }

    @Override
    public ChapterNode updateChapter(ChapterNode chapter) {
        chapters.put(chapter.getId(), chapter);
        return chapter;
    }

    @Override
    public SceneNode addSceneToChapter(UUID chapterId, SceneNode scene) {
        scenesByChapter.computeIfAbsent(chapterId, k -> new ArrayList<>()).add(scene);
        return scene;
    }

    @Override
    public List<SceneNode> addScenesToChapter(UUID chapterId, List<SceneNode> scenes) {
        scenesByChapter.computeIfAbsent(chapterId, k -> new ArrayList<>()).addAll(scenes);
        return scenes;
    }

    @Override
    public List<SceneNode> findScenesByChapterId(UUID chapterId) {
        return scenesByChapter.getOrDefault(chapterId, List.of());
    }

    @Override
    public int deleteScenesByChapterId(UUID chapterId) {
        List<SceneNode> removed = scenesByChapter.remove(chapterId);
        return removed == null ? 0 : removed.size();
    }

    @Override
    public List<ChunkNode> addChunksToChapter(UUID chapterId, List<ChunkNode> chunks) {
        chunksByChapter.computeIfAbsent(chapterId, k -> new ArrayList<>()).addAll(chunks);
        return chunks;
    }

    @Override
    public ChunkNode addChunkToScene(UUID sceneId, ChunkNode chunk) {
        // Not necessary for current tests
        return chunk;
    }

    @Override
    public List<ChunkNode> addChunksToScene(UUID sceneId, List<ChunkNode> chunks) {
        return chunks;
    }

    @Override
    public List<ChunkNode> findChunksByChapterId(UUID chapterId) {
        return chunksByChapter.getOrDefault(chapterId, List.of());
    }

    @Override
    public int deleteChunksByChapterId(UUID chapterId) {
        List<ChunkNode> removed = chunksByChapter.remove(chapterId);
        return removed == null ? 0 : removed.size();
    }

    @Override
    public boolean chunksExistForChapter(UUID chapterId) {
        return !findChunksByChapterId(chapterId).isEmpty();
    }

    @Override
    public int countChunksByChapterId(UUID chapterId) {
        return findChunksByChapterId(chapterId).size();
    }

    @Override
    public ChunkNode updateChunk(ChunkNode chunk) {
        // No-op: tests mutate the same object references that are stored
        return chunk;
    }

    @Override
    public List<ChunkNode> updateChunks(List<ChunkNode> chunks) { return chunks; }

    @Override
    public List<ChunkNode> findAllChunksWithEmbeddings() {
        return chunksByChapter.values().stream()
                .flatMap(List::stream)
                .filter(c -> c.getEmbedding() != null)
                .collect(Collectors.toList());
    }

    @Override
    public IngestionJobNode createJob(IngestionJobNode jobNode) {
        if (jobNode.getId() == null) jobNode.setId(UUID.randomUUID());
        jobs.put(jobNode.getId(), jobNode);
        return jobNode;
    }

    @Override
    public IngestionJobNode createJobWithChapter(IngestionJobNode jobNode, UUID chapterId) {
        jobNode.setChapterId(chapterId);
        return createJob(jobNode);
    }

    @Override
    public Optional<IngestionJobNode> findJob(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public IngestionJobNode updateJob(IngestionJobNode jobNode) {
        jobs.put(jobNode.getId(), jobNode);
        return jobNode;
    }

    @Override
    public Optional<IngestionJobNode> findMostRecentJobForChapter(UUID chapterId) {
        return jobs.values().stream()
                .filter(j -> Objects.equals(j.getChapterId(), chapterId))
                .max(Comparator.comparing(IngestionJobNode::getCreatedAt, Comparator.nullsFirst(LocalDateTime::compareTo)));
    }

    @Override
    public boolean hasActiveJobForChapter(UUID chapterId) {
        return jobs.values().stream().anyMatch(j -> Objects.equals(j.getChapterId(), chapterId));
    }

    @Override
    public List<IngestionJobNode> findJobsByChapterIds(List<UUID> chapterIds) {
        Set<UUID> set = new HashSet<>(chapterIds);
        return jobs.values().stream().filter(j -> set.contains(j.getChapterId())).toList();
    }

    @Override
    public List<IngestionJobNode> findAllJobs() {
        return new ArrayList<>(jobs.values());
    }

    @Override
    public StatusRecordNode addStatusRecord(UUID jobId, StatusRecordNode recordNode) {
        statusByJob.computeIfAbsent(jobId, k -> new ArrayList<>()).add(recordNode);
        return recordNode;
    }

    @Override
    public List<StatusRecordNode> findStatusHistoryForJob(UUID jobId) {
        return statusByJob.getOrDefault(jobId, List.of());
    }

    @Override
    public List<ChapterNode> findChaptersByUniverse(String universe) {
        return chapters.values().stream().filter(c -> Objects.equals(c.getUniverse(), universe)).toList();
    }
}
