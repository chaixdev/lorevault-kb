package com.lorevault.api.infrastructure.persistence.neo4j.adapter;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.infrastructure.persistence.neo4j.model.*;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional
public class Neo4jContentPersistenceAdapter implements ContentPersistencePort {

    private final ChapterGraphRepository chapterRepo;
    private final SceneGraphRepository sceneRepo;
    private final ChunkGraphRepository chunkRepo;
    private final IngestionJobGraphRepository jobRepo;
    private final StatusRecordGraphRepository statusRepo;

    @Override
    public ChapterNode createChapter(ChapterNode chapter) {
        if (chapter.getId() == null) {
            chapter.setId(UUID.randomUUID());
        }
        return chapterRepo.save(chapter);
    }

    @Override
    public Optional<ChapterNode> findChapterById(UUID id) {
        return chapterRepo.findById(id);
    }

    @Override
    public Optional<ChapterNode> findChapterByContentHash(String contentHash) {
        return chapterRepo.findByContentHash(contentHash);
    }

    @Override
    public boolean chapterExistsByContentHash(String contentHash) {
        return chapterRepo.existsByContentHash(contentHash);
    }

    @Override
    public ChapterNode updateChapter(ChapterNode chapter) {
        return chapterRepo.save(chapter);
    }

    @Override
    public SceneNode addSceneToChapter(UUID chapterId, SceneNode scene) {
        ChapterNode chapter = chapterRepo.findById(chapterId).orElseThrow();
        if (scene.getId() == null) scene.setId(UUID.randomUUID());
        scene = sceneRepo.save(scene);
        var scenes = chapter.getScenes();
        if (scenes != null) {
            scenes.add(scene);
        }
        chapterRepo.save(chapter);
        return scene;
    }

    @Override
    public List<SceneNode> addScenesToChapter(UUID chapterId, List<SceneNode> scenes) {
        return scenes.stream().map(s -> addSceneToChapter(chapterId, s)).collect(Collectors.toList());
    }

    @Override
    public List<SceneNode> findScenesByChapterId(UUID chapterId) {
        return sceneRepo.findByChapterId(chapterId);
    }

    @Override
    public int deleteScenesByChapterId(UUID chapterId) {
        var existing = sceneRepo.findByChapterId(chapterId);
        sceneRepo.deleteByChapterId(chapterId);
        return existing.size();
    }

    @Override
    public List<ChunkNode> addChunksToChapter(UUID chapterId, List<ChunkNode> chunks) { /* deprecated path, keep for backward compatibility */
        ChapterNode chapter = chapterRepo.findById(chapterId).orElseThrow();
        for (ChunkNode chunk : chunks) {
            if (chunk.getId() == null) chunk.setId(UUID.randomUUID());
            chunkRepo.save(chunk);
        }
        var existing = chapter.getChunks();
        if (existing != null) {
            existing.addAll(chunks);
            existing.sort(Comparator.comparing(ChunkNode::getChunkNumberInChapter));
        }
        chapterRepo.save(chapter);
        return chunks;
    }

    @Override
    public ChunkNode addChunkToScene(UUID sceneId, ChunkNode chunk) {
        SceneNode scene = sceneRepo.findById(sceneId).orElseThrow();
        if (chunk.getId() == null) chunk.setId(UUID.randomUUID());
        chunk = chunkRepo.save(chunk);
    if (scene.getChunks() == null) scene.setChunks(new ArrayList<>());
        SceneHasChunk rel = new SceneHasChunk();
        rel.setChunk(chunk);
        // Set index from legacy node field when available (migration path)
        try {
            rel.setChunkIndex(chunk.getChunkNumberInChapter());
        } catch (Exception ignored) {}
    scene.getChunks().add(rel);
        sceneRepo.save(scene);
        return chunk;
    }

    @Override
    public List<ChunkNode> addChunksToScene(UUID sceneId, List<ChunkNode> chunks) {
        return chunks.stream().map(c -> addChunkToScene(sceneId, c)).toList();
    }

    @Override
    public IngestionJobNode createJob(IngestionJobNode jobNode) {
        if (jobNode.getId() == null) jobNode.setId(UUID.randomUUID());
        return jobRepo.save(jobNode);
    }

    @Override
    public Optional<IngestionJobNode> findJob(UUID id) {
        return jobRepo.findById(id);
    }

    @Override
    public IngestionJobNode updateJob(IngestionJobNode jobNode) {
        return jobRepo.save(jobNode);
    }

    @Override
    public Optional<IngestionJobNode> findMostRecentJobForChapter(UUID chapterId) {
        return jobRepo.findLatestForChapter(chapterId);
    }

    @Override
    public boolean hasActiveJobForChapter(UUID chapterId) {
        return jobRepo.existsActiveForChapter(chapterId);
    }

    @Override
    public List<IngestionJobNode> findJobsByChapterIds(List<UUID> chapterIds) {
        if (chapterIds == null || chapterIds.isEmpty()) return List.of();
        return jobRepo.findByChapterIds(chapterIds);
    }

    @Override
    public List<IngestionJobNode> findAllJobs() {
        return jobRepo.findAll();
    }

    @Override
    public List<ChapterNode> findChaptersByUniverse(String universe) {
        if (universe == null || universe.isBlank()) return List.of();
        return chapterRepo.findAll().stream()
                .filter(c -> universe.equals(c.getUniverse()))
                .collect(Collectors.toList());
    }

    @Override
    public StatusRecordNode addStatusRecord(UUID jobId, StatusRecordNode recordNode) {
        if (recordNode.getId() == null) recordNode.setId(UUID.randomUUID());
        if (recordNode.getJobId() == null) recordNode.setJobId(jobId);

        // Save the status record first
        recordNode = statusRepo.save(recordNode);

        // Ensure the job exists; fail fast if not
        jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        // Atomic pointer swap via repository method
        jobRepo.swapCurrentStatus(jobId, recordNode.getId());

        return recordNode;
    }

    @Override
    public List<StatusRecordNode> findStatusHistoryForJob(UUID jobId) {
        return statusRepo.findStatusHistoryForJob(jobId);
    }

    @Override
    public List<ChunkNode> findChunksByChapterId(UUID chapterId) {
        long start = System.currentTimeMillis();
        List<ChunkNode> viaScenes = chunkRepo.findByChapterIdViaScenes(chapterId);
        if (!viaScenes.isEmpty()) {
            long ms = System.currentTimeMillis() - start;
            // simple debug log without introducing log dependency changes
            System.out.println("[Neo4jAdapter] findChunksByChapterId viaScenes size=" + viaScenes.size() + " ms=" + ms + " chapter=" + chapterId);
            return viaScenes;
        }
        List<ChunkNode> legacy = chunkRepo.findByChapterId(chapterId);
        long ms = System.currentTimeMillis() - start;
        System.out.println("[Neo4jAdapter] findChunksByChapterId legacy size=" + legacy.size() + " ms=" + ms + " chapter=" + chapterId);
        return legacy;
    }

    @Override
    public int deleteChunksByChapterId(UUID chapterId) {
        int count = chunkRepo.countByChapterIdViaScenes(chapterId);
        if (count > 0) {
            chunkRepo.deleteByChapterIdViaScenes(chapterId);
            return count;
        }
        int legacy = chunkRepo.countByChapterId(chapterId);
        if (legacy > 0) chunkRepo.deleteByChapterId(chapterId);
        return legacy;
    }

    @Override
    public boolean chunksExistForChapter(UUID chapterId) {
        return chunkRepo.existsForChapterViaScenes(chapterId) || chunkRepo.existsForChapter(chapterId);
    }

    @Override
    public int countChunksByChapterId(UUID chapterId) {
        int via = chunkRepo.countByChapterIdViaScenes(chapterId);
        return via > 0 ? via : chunkRepo.countByChapterId(chapterId);
    }

    @Override
    public IngestionJobNode createJobWithChapter(IngestionJobNode jobNode, UUID chapterId) {
        if (jobNode.getId() == null) jobNode.setId(UUID.randomUUID());
        ChapterNode chapter = chapterRepo.findById(chapterId).orElseThrow();
        jobNode.setChapter(chapter);
        jobNode.setChapterId(chapter.getId());
        return jobRepo.save(jobNode);
    }

    @Override
    public ChunkNode updateChunk(ChunkNode chunk) {
        return chunkRepo.save(chunk);
    }

    @Override
    public List<ChunkNode> updateChunks(List<ChunkNode> chunks) {
        long start = System.currentTimeMillis();
        if (chunks == null || chunks.isEmpty()) return List.of();
        List<ChunkNode> saved = chunks.stream().map(chunkRepo::save).toList();
        long ms = System.currentTimeMillis() - start;
        System.out.println("[Neo4jAdapter] updateChunks persisted=" + saved.size() + " ms=" + ms);
        return saved;
    }
}
