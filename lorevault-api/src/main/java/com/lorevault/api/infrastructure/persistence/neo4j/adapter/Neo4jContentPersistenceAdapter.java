package com.lorevault.api.infrastructure.persistence.neo4j.adapter;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.IngestionJobNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneHasChunk;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
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
    private final Neo4jMapper mapper;

    @Override
    public Chapter createChapter(Chapter chapter) {
        ChapterNode node = mapper.toNode(chapter);
        if (node.getId() == null) {
            node.setId(UUID.randomUUID());
        }
        return mapper.toDomain(chapterRepo.save(node));
    }

    @Override
    public Optional<Chapter> findChapterById(UUID id) {
        return chapterRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Chapter> findChapterByContentHash(String contentHash) {
        return chapterRepo.findByContentHash(contentHash).map(mapper::toDomain);
    }

    @Override
    public boolean chapterExistsByContentHash(String contentHash) {
        return chapterRepo.existsByContentHash(contentHash);
    }

    @Override
    public Chapter updateChapter(Chapter chapter) {
        return mapper.toDomain(chapterRepo.save(mapper.toNode(chapter)));
    }

    @Override
    public Scene addSceneToChapter(UUID chapterId, Scene scene) {
        ChapterNode chapterNode = chapterRepo.findById(chapterId).orElseThrow();
        SceneNode sceneNode = mapper.toNode(scene);
        if (sceneNode.getId() == null) sceneNode.setId(UUID.randomUUID());
        sceneNode = sceneRepo.save(sceneNode);
        var scenes = chapterNode.getScenes();
        if (scenes != null) {
            scenes.add(sceneNode);
        }
        chapterRepo.save(chapterNode);
        return mapper.toDomain(sceneNode);
    }

    @Override
    public List<Scene> addScenesToChapter(UUID chapterId, List<Scene> scenes) {
        return scenes.stream().map(s -> addSceneToChapter(chapterId, s)).collect(Collectors.toList());
    }

    @Override
    public List<Scene> findScenesByChapterId(UUID chapterId) {
        return mapper.toSceneDomainList(sceneRepo.findByChapterId(chapterId));
    }

    @Override
    public int deleteScenesByChapterId(UUID chapterId) {
        var existing = sceneRepo.findByChapterId(chapterId);
        sceneRepo.deleteByChapterId(chapterId);
        return existing.size();
    }

    @Override
    public List<Chunk> addChunksToChapter(UUID chapterId, List<Chunk> chunks) {
        ChapterNode chapter = chapterRepo.findById(chapterId).orElseThrow();
        List<ChunkNode> chunkNodes = mapper.toChunkNodeList(chunks);
        for (ChunkNode chunk : chunkNodes) {
            if (chunk.getId() == null) chunk.setId(UUID.randomUUID());
            chunkRepo.save(chunk);
        }
        var existing = chapter.getChunks();
        if (existing != null) {
            existing.addAll(chunkNodes);
            existing.sort(Comparator.comparing(ChunkNode::getChunkNumberInChapter));
        }
        chapterRepo.save(chapter);
        return mapper.toChunkDomainList(chunkNodes);
    }

    @Override
    public Chunk addChunkToScene(UUID sceneId, Chunk chunk) {
        SceneNode scene = sceneRepo.findById(sceneId).orElseThrow();
        ChunkNode chunkNode = mapper.toNode(chunk);
        if (chunkNode.getId() == null) chunkNode.setId(UUID.randomUUID());
        chunkNode = chunkRepo.save(chunkNode);
        if (scene.getChunks() == null) scene.setChunks(new ArrayList<>());
        SceneHasChunk rel = new SceneHasChunk();
        rel.setChunk(chunkNode);
        try {
            rel.setChunkIndex(chunkNode.getChunkNumberInChapter());
        } catch (Exception ignored) {}
        scene.getChunks().add(rel);
        sceneRepo.save(scene);
        return mapper.toDomain(chunkNode);
    }

    @Override
    public List<Chunk> addChunksToScene(UUID sceneId, List<Chunk> chunks) {
        return chunks.stream().map(c -> addChunkToScene(sceneId, c)).toList();
    }

    @Override
    public IngestionJob createJob(IngestionJob job) {
        IngestionJobNode jobNode = mapper.toNode(job);
        if (jobNode.getId() == null) jobNode.setId(UUID.randomUUID());
        return mapper.toDomain(jobRepo.save(jobNode));
    }

    @Override
    public Optional<IngestionJob> findJob(UUID id) {
        return jobRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public IngestionJob updateJob(IngestionJob job) {
        return mapper.toDomain(jobRepo.save(mapper.toNode(job)));
    }

    @Override
    public Optional<IngestionJob> findMostRecentJobForChapter(UUID chapterId) {
        return jobRepo.findLatestForChapter(chapterId).map(mapper::toDomain);
    }

    @Override
    public boolean hasActiveJobForChapter(UUID chapterId) {
        return jobRepo.existsActiveForChapter(chapterId);
    }

    @Override
    public List<IngestionJob> findJobsByChapterIds(List<UUID> chapterIds) {
        if (chapterIds == null || chapterIds.isEmpty()) return List.of();
        return mapper.toIngestionJobDomainList(jobRepo.findByChapterIds(chapterIds));
    }

    @Override
    public List<IngestionJob> findAllJobs() {
        return mapper.toIngestionJobDomainList(jobRepo.findAll());
    }

    @Override
    public List<Chapter> findChaptersByUniverse(String universe) {
        if (universe == null || universe.isBlank()) return List.of();
        return mapper.toChapterDomainList(chapterRepo.findAll().stream()
                .filter(c -> universe.equals(c.getUniverse()))
                .collect(Collectors.toList()));
    }

    @Override
    public StatusRecord addStatusRecord(UUID jobId, StatusRecord record) {
        var recordNode = mapper.toNode(record);
        if (recordNode.getId() == null) recordNode.setId(UUID.randomUUID());
        if (recordNode.getJobId() == null) recordNode.setJobId(jobId);

        recordNode = statusRepo.save(recordNode);

        jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        jobRepo.swapCurrentStatus(jobId, recordNode.getId());

        return mapper.toDomain(recordNode);
    }

    @Override
    public List<StatusRecord> findStatusHistoryForJob(UUID jobId) {
        return mapper.toStatusRecordDomainList(statusRepo.findStatusHistoryForJob(jobId));
    }

    @Override
    public List<Chunk> findChunksByChapterId(UUID chapterId) {
        long start = System.currentTimeMillis();
        List<ChunkNode> viaScenes = chunkRepo.findByChapterIdViaScenes(chapterId);
        if (!viaScenes.isEmpty()) {
            long ms = System.currentTimeMillis() - start;
            System.out.println("[Neo4jAdapter] findChunksByChapterId viaScenes size=" + viaScenes.size() + " ms=" + ms + " chapter=" + chapterId);
            return mapper.toChunkDomainList(viaScenes);
        }
        List<ChunkNode> legacy = chunkRepo.findByChapterId(chapterId);
        long ms = System.currentTimeMillis() - start;
        System.out.println("[Neo4jAdapter] findChunksByChapterId legacy size=" + legacy.size() + " ms=" + ms + " chapter=" + chapterId);
        return mapper.toChunkDomainList(legacy);
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
    public IngestionJob createJobWithChapter(IngestionJob job, UUID chapterId) {
        var jobNode = mapper.toNode(job);
        if (jobNode.getId() == null) jobNode.setId(UUID.randomUUID());
        ChapterNode chapter = chapterRepo.findById(chapterId).orElseThrow();
        jobNode.setChapter(chapter);
        jobNode.setChapterId(chapter.getId());
        return mapper.toDomain(jobRepo.save(jobNode));
    }

    @Override
    public Chunk updateChunk(Chunk chunk) {
        return mapper.toDomain(chunkRepo.save(mapper.toNode(chunk)));
    }

    @Override
    public List<Chunk> updateChunks(List<Chunk> chunks) {
        long start = System.currentTimeMillis();
        if (chunks == null || chunks.isEmpty()) return List.of();
        List<ChunkNode> saved = chunks.stream().map(mapper::toNode).map(chunkRepo::save).toList();
        long ms = System.currentTimeMillis() - start;
        System.out.println("[Neo4jAdapter] updateChunks persisted=" + saved.size() + " ms=" + ms);
        return mapper.toChunkDomainList(saved);
    }

    @Override
    public List<Chunk> findAllChunksWithEmbeddings() {
        long start = System.currentTimeMillis();
        List<ChunkNode> chunks = chunkRepo.findAllWithEmbeddings();
        long ms = System.currentTimeMillis() - start;
        System.out.println("[Neo4jAdapter] findAllChunksWithEmbeddings size=" + chunks.size() + " ms=" + ms);
        return mapper.toChunkDomainList(chunks);
    }
}
