package com.lorevault.api.testutil.fakes;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.StatusRecord;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory fake implementation of ContentPersistencePort for unit/service tests.
 */
public class FakeContentPersistencePort implements ContentPersistencePort {

    public final Map<UUID, Chapter> chapters = new ConcurrentHashMap<>();
    public final Map<UUID, List<Scene>> scenesByChapter = new ConcurrentHashMap<>();
    public final Map<UUID, List<Chunk>> chunksByChapter = new ConcurrentHashMap<>();
    public final Map<UUID, IngestionJob> jobs = new ConcurrentHashMap<>();
    public final Map<UUID, List<StatusRecord>> statusByJob = new ConcurrentHashMap<>();

    @Override
    public Chapter createChapter(Chapter chapter) {
        if (chapter.getId() == null) chapter.setId(UUID.randomUUID());
        chapters.put(chapter.getId(), chapter);
        return chapter;
    }

    @Override
    public Optional<Chapter> findChapterById(UUID id) {
        return Optional.ofNullable(chapters.get(id));
    }

    @Override
    public Optional<Chapter> findChapterByContentHash(String contentHash) {
        return chapters.values().stream().filter(c -> Objects.equals(c.getContentHash(), contentHash)).findFirst();
    }

    @Override
    public boolean chapterExistsByContentHash(String contentHash) {
        return findChapterByContentHash(contentHash).isPresent();
    }

    @Override
    public Chapter updateChapter(Chapter chapter) {
        chapters.put(chapter.getId(), chapter);
        return chapter;
    }

    @Override
    public Scene addSceneToChapter(UUID chapterId, Scene scene) {
        scenesByChapter.computeIfAbsent(chapterId, k -> new ArrayList<>()).add(scene);
        return scene;
    }

    @Override
    public List<Scene> addScenesToChapter(UUID chapterId, List<Scene> scenes) {
        scenesByChapter.computeIfAbsent(chapterId, k -> new ArrayList<>()).addAll(scenes);
        return scenes;
    }

    @Override
    public List<Scene> findScenesByChapterId(UUID chapterId) {
        return scenesByChapter.getOrDefault(chapterId, List.of());
    }

    @Override
    public int deleteScenesByChapterId(UUID chapterId) {
        List<Scene> removed = scenesByChapter.remove(chapterId);
        return removed == null ? 0 : removed.size();
    }

    @Override
    public List<Chunk> addChunksToChapter(UUID chapterId, List<Chunk> chunks) {
        chunksByChapter.computeIfAbsent(chapterId, k -> new ArrayList<>()).addAll(chunks);
        return chunks;
    }

    @Override
    public Chunk addChunkToScene(UUID sceneId, Chunk chunk) {
        // Not necessary for current tests
        return chunk;
    }

    @Override
    public List<Chunk> addChunksToScene(UUID sceneId, List<Chunk> chunks) {
        return chunks;
    }

    @Override
    public List<Chunk> findChunksByChapterId(UUID chapterId) {
        return chunksByChapter.getOrDefault(chapterId, List.of());
    }

    @Override
    public int deleteChunksByChapterId(UUID chapterId) {
        List<Chunk> removed = chunksByChapter.remove(chapterId);
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
    public Chunk updateChunk(Chunk chunk) {
        // No-op: tests mutate the same object references that are stored
        return chunk;
    }

    @Override
    public List<Chunk> updateChunks(List<Chunk> chunks) { return chunks; }

    @Override
    public List<Chunk> findAllChunksWithEmbeddings() {
        return chunksByChapter.values().stream()
                .flatMap(List::stream)
                .filter(c -> c.getEmbedding() != null)
                .collect(Collectors.toList());
    }

    @Override
    public IngestionJob createJob(IngestionJob job) {
        if (job.getId() == null) job.setId(UUID.randomUUID());
        jobs.put(job.getId(), job);
        return job;
    }

    @Override
    public IngestionJob createJobWithChapter(IngestionJob job, UUID chapterId) {
        job.setChapterId(chapterId);
        return createJob(job);
    }

    @Override
    public Optional<IngestionJob> findJob(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public IngestionJob updateJob(IngestionJob job) {
        jobs.put(job.getId(), job);
        return job;
    }

    @Override
    public Optional<IngestionJob> findMostRecentJobForChapter(UUID chapterId) {
        return jobs.values().stream()
                .filter(j -> Objects.equals(j.getChapterId(), chapterId))
                .max(Comparator.comparing(IngestionJob::getCreatedAt, Comparator.nullsFirst(LocalDateTime::compareTo)));
    }

    @Override
    public boolean hasActiveJobForChapter(UUID chapterId) {
        return jobs.values().stream().anyMatch(j -> Objects.equals(j.getChapterId(), chapterId));
    }

    @Override
    public List<IngestionJob> findJobsByChapterIds(List<UUID> chapterIds) {
        Set<UUID> set = new HashSet<>(chapterIds);
        return jobs.values().stream().filter(j -> set.contains(j.getChapterId())).toList();
    }

    @Override
    public List<IngestionJob> findAllJobs() {
        return new ArrayList<>(jobs.values());
    }

    @Override
    public StatusRecord addStatusRecord(UUID jobId, StatusRecord record) {
        statusByJob.computeIfAbsent(jobId, k -> new ArrayList<>()).add(record);
        return record;
    }

    @Override
    public List<StatusRecord> findStatusHistoryForJob(UUID jobId) {
        return statusByJob.getOrDefault(jobId, List.of());
    }

    @Override
    public List<Chapter> findChaptersByUniverse(String universe) {
        return chapters.values().stream().filter(c -> Objects.equals(c.getUniverse(), universe)).toList();
    }
}
