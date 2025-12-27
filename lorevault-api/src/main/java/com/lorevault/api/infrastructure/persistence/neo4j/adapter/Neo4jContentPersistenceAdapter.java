package com.lorevault.api.infrastructure.persistence.neo4j.adapter;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.domain.content.Series;
import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.domain.ingestion.LlmCallRecord;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.IngestionJobNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneHasChunk;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.UniverseNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SeriesNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.BookNode;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.*;
import com.lorevault.api.infrastructure.persistence.neo4j.model.LlmCallRecordNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@RequiredArgsConstructor
@Transactional
@SuppressWarnings("null")
public class Neo4jContentPersistenceAdapter implements ContentPersistencePort {

    private final ChapterGraphRepository chapterRepo;
    private final SceneGraphRepository sceneRepo;
    private final ChunkGraphRepository chunkRepo;
    private final IngestionJobGraphRepository jobRepo;
    private final StatusRecordGraphRepository statusRepo;
    private final LlmCallRecordGraphRepository llmCallRepo;
    
    // Hierarchy repositories
    private final UniverseGraphRepository universeRepo;
    private final SeriesGraphRepository seriesRepo;
    private final BookGraphRepository bookRepo;
    
    // Read repositories (for event ordering and chapter lookup)
    private final ChapterReadRepository chapterReadRepo;
    private final TemporalReadRepository temporalReadRepo;
    
    private final Neo4jMapper mapper;

    @Override
    public Chapter createChapter(Chapter chapter) {
        ChapterNode node = Objects.requireNonNull(mapper.toNode(chapter), "chapter must not be null");
        if (node.getId() == null) {
            node.setId(UUID.randomUUID());
        }
        // Establish Chapter -> Book relationship if bookId provided
        UUID bookId = chapter.getBookId();
        if (bookId != null) {
            bookRepo.findById(bookId).ifPresent(node::setBook);
        }
        return mapper.toDomain(chapterRepo.save(node));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Chapter> findChapterById(UUID id) {
        return chapterRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Chapter> findChapterByContentHash(String contentHash) {
        return chapterRepo.findByContentHash(contentHash).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean chapterExistsByContentHash(String contentHash) {
        return chapterRepo.existsByContentHash(contentHash);
    }

    @Override
    public Chapter updateChapter(Chapter chapter) {
        ChapterNode node = Objects.requireNonNull(mapper.toNode(chapter), "chapter must not be null");
        return mapper.toDomain(chapterRepo.save(node));
    }

    @Override
    public Scene addSceneToChapter(UUID chapterId, Scene scene) {
        ChapterNode chapterNode = chapterRepo.findById(chapterId).orElseThrow();
        SceneNode sceneNode = Objects.requireNonNull(mapper.toNode(scene), "scene must not be null");
        if (sceneNode.getId() == null) sceneNode.setId(UUID.randomUUID());
        // Ensure the scene carries the chapterId for efficient lookups when also labeled :Event
        if (sceneNode.getChapterId() == null) {
            sceneNode.setChapterId(chapterNode.getId());
        }
        sceneNode = sceneRepo.save(sceneNode);
        // Ensure HAS_SCENE relationship exists; initialize list if needed
        var scenes = chapterNode.getScenes();
        if (scenes == null) {
            scenes = new ArrayList<>();
            chapterNode.setScenes(scenes);
        }

        // Idempotency: avoid duplicating HAS_SCENE relationships on retries.
        UUID sceneId = sceneNode.getId();
        boolean alreadyLinked = scenes.stream()
                .map(SceneNode::getId)
                .anyMatch(existingId -> Objects.equals(existingId, sceneId));
        if (!alreadyLinked) {
            scenes.add(sceneNode);
        }
        chapterRepo.save(chapterNode);
        return mapper.toDomain(sceneNode);
    }

    @Override
    public List<Scene> addScenesToChapter(UUID chapterId, List<Scene> scenes) {
        ChapterNode chapterNode = chapterRepo.findById(chapterId).orElseThrow();
        List<SceneNode> sceneNodes = scenes.stream()
                .map(s -> Objects.requireNonNull(mapper.toNode(s), "scene must not be null"))
                .collect(Collectors.toList());
        
        sceneNodes.forEach(sceneNode -> {
            if (sceneNode.getId() == null) sceneNode.setId(UUID.randomUUID());
            if (sceneNode.getChapterId() == null) {
                sceneNode.setChapterId(chapterNode.getId());
            }
        });
        
        sceneNodes = sceneRepo.saveAll(sceneNodes);
        
        var chapterScenes = chapterNode.getScenes();
        if (chapterScenes == null) {
            chapterScenes = new ArrayList<>();
            chapterNode.setScenes(chapterScenes);
        }

        // Idempotency: avoid duplicating HAS_SCENE relationships on retries.
        Set<UUID> existingSceneIds = chapterScenes.stream()
                .map(SceneNode::getId)
                .collect(Collectors.toSet());
        for (SceneNode sceneNode : sceneNodes) {
            UUID sceneId = sceneNode.getId();
            if (sceneId == null || existingSceneIds.add(sceneId)) {
                chapterScenes.add(sceneNode);
            }
        }
        chapterRepo.save(chapterNode);
        
        return mapper.toSceneDomainList(sceneNodes);
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public List<UUID> findChapterIdsUpTo(UUID bookId, int uptoChapterNumber) {
        return chapterReadRepo.findChapterIdsUpTo(bookId, uptoChapterNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AbstractMap.SimpleEntry<UUID, UUID>> findChapterTemporalEdges(UUID chapterId) {
        return temporalReadRepo.findChapterEventEdges(chapterId).stream()
                .map(p -> new AbstractMap.SimpleEntry<>(p.getFromId(), p.getToId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Chunk> addChunksToChapter(UUID chapterId, List<Chunk> chunks) {
        ChapterNode chapter = chapterRepo.findById(chapterId).orElseThrow();
        List<ChunkNode> chunkNodes = chunks.stream()
                .map(c -> Objects.requireNonNull(mapper.toNode(c), "chunk must not be null"))
                .toList();
        chunkNodes.forEach(chunk -> {
            if (chunk.getId() == null) chunk.setId(UUID.randomUUID());
        });
        chunkRepo.saveAll(chunkNodes);
        
        var existing = chapter.getChunks();
        if (existing == null) {
            existing = new ArrayList<>();
            chapter.setChunks(existing);
        }

        // Idempotency: avoid duplicating HAS_CHUNK relationships on retries.
        Set<UUID> existingChunkIds = existing.stream()
                .map(ChunkNode::getId)
                .collect(Collectors.toSet());
        for (ChunkNode chunkNode : chunkNodes) {
            UUID chunkId = chunkNode.getId();
            if (chunkId == null || existingChunkIds.add(chunkId)) {
                existing.add(chunkNode);
            }
        }

        existing.sort(Comparator.comparing(
                ChunkNode::getChunkNumberInChapter,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));
        chapterRepo.save(chapter);
        return mapper.toChunkDomainList(chunkNodes);
    }

    @Override
    public Chunk addChunkToScene(UUID sceneId, Chunk chunk) {
        SceneNode scene = sceneRepo.findById(sceneId).orElseThrow();
        ChunkNode chunkNode = Objects.requireNonNull(mapper.toNode(chunk), "chunk must not be null");
        if (chunkNode.getId() == null) chunkNode.setId(UUID.randomUUID());
        ChunkNode savedChunkNode = chunkRepo.save(chunkNode);
        if (scene.getChunks() == null) scene.setChunks(new ArrayList<>());

        // Idempotency: avoid duplicating HAS_CHUNK relationships for the same chunk.
        boolean alreadyLinked = scene.getChunks().stream()
                .map(SceneHasChunk::getChunk)
                .filter(Objects::nonNull)
                .map(ChunkNode::getId)
            .anyMatch(existingId -> Objects.equals(existingId, savedChunkNode.getId()));

        if (!alreadyLinked) {
            SceneHasChunk rel = new SceneHasChunk();
            rel.setChunk(savedChunkNode);

            // Defensive: chunkIndex is optional; avoid NPEs on partially-built chunks.
            Integer idx = savedChunkNode.getChunkNumberInChapter();
            if (idx != null) {
                rel.setChunkIndex(idx);
            }

            scene.getChunks().add(rel);
        }
        sceneRepo.save(scene);
        return mapper.toDomain(savedChunkNode);
    }

    @Override
    public List<Chunk> addChunksToScene(UUID sceneId, List<Chunk> chunks) {
        return chunks.stream().map(c -> addChunkToScene(sceneId, c)).toList();
    }

    @Override
    public IngestionJob createJob(IngestionJob job) {
        IngestionJobNode jobNode = Objects.requireNonNull(mapper.toNode(job), "job must not be null");
        if (jobNode.getId() == null) jobNode.setId(UUID.randomUUID());
        return mapper.toDomain(jobRepo.save(jobNode));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IngestionJob> findJob(UUID id) {
    // Load job along with current status so callers can access currentStatus without additional queries
    return jobRepo.findByIdWithCurrentStatus(id).map(mapper::toDomain);
    }

    @Override
    public IngestionJob updateJob(IngestionJob job) {
        IngestionJobNode node = Objects.requireNonNull(mapper.toNode(job), "job must not be null");
        return mapper.toDomain(jobRepo.save(node));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IngestionJob> findMostRecentJobForChapter(UUID chapterId) {
        return jobRepo.findLatestForChapter(chapterId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveJobForChapter(UUID chapterId) {
        return jobRepo.existsActiveForChapter(chapterId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngestionJob> findJobsByChapterIds(List<UUID> chapterIds) {
        if (chapterIds == null || chapterIds.isEmpty()) return List.of();
        return mapper.toIngestionJobDomainList(jobRepo.findByChapterIds(chapterIds));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngestionJob> findAllJobs() {
        return mapper.toIngestionJobDomainList(jobRepo.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Chapter> findChaptersByUniverse(String universe) {
        if (universe == null || universe.isBlank()) return List.of();
        return mapper.toChapterDomainList(chapterRepo.findAll().stream()
                .filter(c -> universe.equals(c.getUniverse()))
                .collect(Collectors.toList()));
    }

    @Override
    public StatusRecord addStatusRecord(UUID jobId, StatusRecord record) {
        var recordNode = Objects.requireNonNull(mapper.toNode(record), "record must not be null");
        if (recordNode.getId() == null) recordNode.setId(UUID.randomUUID());
        if (recordNode.getJobId() == null) recordNode.setJobId(jobId);

        recordNode = statusRepo.save(recordNode);

        jobRepo.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        jobRepo.swapCurrentStatus(jobId, recordNode.getId());

        return mapper.toDomain(recordNode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatusRecord> findStatusHistoryForJob(UUID jobId) {
        return mapper.toStatusRecordDomainList(statusRepo.findStatusHistoryForJob(jobId));
    }

    // LLM Call Records
    @Override
    public LlmCallRecord addLlmCallRecord(LlmCallRecord record) {
        LlmCallRecordNode node = Objects.requireNonNull(mapper.toNode(record), "record must not be null");
        if (node.getId() == null) node.setId(UUID.randomUUID());
        
        // Establish relationships for Neo4j graph visualization
        UUID jobId = record.getJobId();
        if (jobId != null) {
            jobRepo.findById(jobId).ifPresent(node::setJob);
        }
        UUID statusRecordId = record.getStatusRecordId();
        if (statusRecordId != null) {
            statusRepo.findById(statusRecordId).ifPresent(node::setStatus);
        }
        
        node = llmCallRepo.save(node);
        return mapper.toDomain(node);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LlmCallRecord> findLlmCallsByJob(UUID jobId) {
        return llmCallRepo.findByJobId(jobId).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LlmCallRecord> findLlmCallsByJobAndStep(UUID jobId, String step) {
        return llmCallRepo.findByJobIdAndStep(jobId, step).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public boolean chunksExistForChapter(UUID chapterId) {
        return chunkRepo.existsForChapterViaScenes(chapterId) || chunkRepo.existsForChapter(chapterId);
    }

    @Override
    @Transactional(readOnly = true)
    public int countChunksByChapterId(UUID chapterId) {
        int via = chunkRepo.countByChapterIdViaScenes(chapterId);
        return via > 0 ? via : chunkRepo.countByChapterId(chapterId);
    }

    @Override
    public IngestionJob createJobWithChapter(IngestionJob job, UUID chapterId) {
        var jobNode = Objects.requireNonNull(mapper.toNode(job), "job must not be null");
        if (jobNode.getId() == null) jobNode.setId(UUID.randomUUID());
        ChapterNode chapter = chapterRepo.findById(chapterId).orElseThrow();
        jobNode.setChapter(chapter);
        jobNode.setChapterId(chapter.getId());
        return mapper.toDomain(jobRepo.save(jobNode));
    }

    @Override
    public Chunk updateChunk(Chunk chunk) {
        ChunkNode node = Objects.requireNonNull(mapper.toNode(chunk), "chunk must not be null");
        return mapper.toDomain(chunkRepo.save(node));
    }

    @Override
    public List<Chunk> updateChunks(List<Chunk> chunks) {
        long start = System.currentTimeMillis();
        if (chunks == null || chunks.isEmpty()) return List.of();
        List<ChunkNode> nodes = chunks.stream()
                .map(c -> Objects.requireNonNull(mapper.toNode(c), "chunk must not be null"))
                .collect(Collectors.toList());
        Iterable<ChunkNode> saved = chunkRepo.saveAll(nodes);
        List<ChunkNode> savedList = StreamSupport.stream(saved.spliterator(), false).toList();
        long ms = System.currentTimeMillis() - start;
        System.out.println("[Neo4jAdapter] updateChunks persisted=" + savedList.size() + " ms=" + ms);
        return mapper.toChunkDomainList(savedList);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Chunk> findAllChunksWithEmbeddings() {
        long start = System.currentTimeMillis();
        List<ChunkNode> chunks = chunkRepo.findAllWithEmbeddings();
        long ms = System.currentTimeMillis() - start;
        System.out.println("[Neo4jAdapter] findAllChunksWithEmbeddings size=" + chunks.size() + " ms=" + ms);
        return mapper.toChunkDomainList(chunks);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Chunk> findChunkById(UUID id) {
        return chunkRepo.findById(id).map(mapper::toDomain);
    }

    // Publication Hierarchy - Universes
    @Override
    public Universe createUniverse(Universe universe) {
        UniverseNode node = mapper.toNode(universe);
        if (node.getId() == null) {
            node.setId(UUID.randomUUID());
        }
        UniverseNode saved = universeRepo.save(node);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Universe> findUniverseById(UUID id) {
        return universeRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Universe> findUniverseByName(String name) {
        return universeRepo.findByName(name).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Universe> findAllUniverses() {
        return universeRepo.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    // Publication Hierarchy - Series
    @Override
    public Series createSeries(Series series) {
        SeriesNode node = Objects.requireNonNull(mapper.toNode(series), "series must not be null");
        if (node.getId() == null) {
            node.setId(UUID.randomUUID());
        }
        
        // Establish relationship to Universe
        UUID universeId = Objects.requireNonNull(series.getUniverseId(), "series.universeId must not be null");
        UniverseNode universeNode = universeRepo.findById(universeId)
            .orElseThrow(() -> new IllegalArgumentException("Universe not found: " + universeId));
        node.setUniverse(universeNode);
        
        SeriesNode saved = seriesRepo.save(node);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Series> findSeriesById(UUID id) {
        return seriesRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Series> findSeriesByNameAndUniverseId(String name, UUID universeId) {
        return seriesRepo.findByNameAndUniverseId(name, universeId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Series> findSeriesByUniverseId(UUID universeId) {
        if (universeId == null) {
            return List.of();
        }
        return seriesRepo.findByUniverseId(universeId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    // Publication Hierarchy - Books
    @Override
    public Book createBook(Book book) {
        BookNode node = Objects.requireNonNull(mapper.toNode(book), "book must not be null");
        if (node.getId() == null) {
            node.setId(UUID.randomUUID());
        }
        
        // Establish relationship to Universe
        UUID universeId = Objects.requireNonNull(book.getUniverseId(), "book.universeId must not be null");
        UniverseNode universeNode = universeRepo.findById(universeId)
            .orElseThrow(() -> new IllegalArgumentException("Universe not found: " + universeId));
        node.setUniverseNode(universeNode);
        
        // Establish relationship to Series if book is part of a series
        if (book.getSeriesId() != null) {
            UUID seriesId = book.getSeriesId();
            SeriesNode seriesNode = seriesRepo.findById(seriesId)
                .orElseThrow(() -> new IllegalArgumentException("Series not found: " + seriesId));
            node.setSeriesNode(seriesNode);
        }
        
        BookNode saved = bookRepo.save(node);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Book> findBookById(UUID id) {
        return bookRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Book> findBookByTitleAndSeriesId(String title, UUID seriesId) {
        return bookRepo.findByTitleAndSeriesId(title, seriesId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Book> findStandaloneBookByTitleAndUniverseId(String title, UUID universeId) {
        return bookRepo.findStandaloneByTitleAndUniverseId(title, universeId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Book> findBooksByUniverseId(UUID universeId) {
        if (universeId == null) {
            return List.of();
        }
        return bookRepo.findByUniverseId(universeId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Book> findBooksBySeriesId(UUID seriesId) {
        if (seriesId == null) {
            return List.of();
        }
        return bookRepo.findBySeriesId(seriesId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
