package com.lorevault.api.infrastructure.persistence.neo4j.adapter;
import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.content.Series;
import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.BookGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.ChapterGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.ChapterReadRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.ChunkGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.SceneGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.SeriesGraphRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.TemporalReadRepository;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.UniverseGraphRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Transactional
@SuppressWarnings("null")
public class Neo4jContentPersistenceAdapter {

    private final ChapterGraphRepository chapterRepo;
    private final SceneGraphRepository sceneRepo;
    private final ChunkGraphRepository chunkRepo;
    private final UniverseGraphRepository universeRepo;
    private final SeriesGraphRepository seriesRepo;
    private final BookGraphRepository bookRepo;
    private final ChapterReadRepository chapterReadRepo;
    private final TemporalReadRepository temporalReadRepo;

    protected Neo4jContentPersistenceAdapter() {
        this.chapterRepo = null;
        this.sceneRepo = null;
        this.chunkRepo = null;
        this.universeRepo = null;
        this.seriesRepo = null;
        this.bookRepo = null;
        this.chapterReadRepo = null;
        this.temporalReadRepo = null;
    }

    public Chapter createChapter(Chapter chapter) {
        Objects.requireNonNull(chapter, "chapter must not be null");
        if (chapter.getId() == null) {
            chapter.setId(UUID.randomUUID());
        }
        if (chapter.getBook() == null && chapter.getBookId() != null) {
            bookRepo.findById(chapter.getBookId()).ifPresent(chapter::setBook);
        }
        return chapterRepo.save(chapter);
    }

    @Transactional(readOnly = true)
    public Optional<Chapter> findChapterById(UUID id) {
        return chapterRepo.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Chapter> findChapterByContentHash(String contentHash) {
        return chapterRepo.findByContentHash(contentHash);
    }

    @Transactional(readOnly = true)
    public boolean chapterExistsByContentHash(String contentHash) {
        return chapterRepo.existsByContentHash(contentHash);
    }

    public Chapter updateChapter(Chapter chapter) {
        Objects.requireNonNull(chapter, "chapter must not be null");
        return chapterRepo.save(chapter);
    }

    public Scene addSceneToChapter(UUID chapterId, Scene scene) {
        Chapter chapter = chapterRepo.findById(chapterId).orElseThrow();
        Objects.requireNonNull(scene, "scene must not be null");
        if (scene.getId() == null) {
            scene.setId(UUID.randomUUID());
        }
        if (scene.getChapterId() == null) {
            scene.setChapterId(chapterId);
        }
        scene.setChapter(chapter);
        Scene savedScene = sceneRepo.save(scene);

        List<Scene> chapterScenes = chapter.getScenes();
        if (chapterScenes == null) {
            chapterScenes = new java.util.ArrayList<>();
            chapter.setScenes(chapterScenes);
        }
        UUID sceneId = savedScene.getId();
        boolean alreadyLinked = chapterScenes.stream()
                .map(Scene::getId)
                .anyMatch(existingId -> Objects.equals(existingId, sceneId));
        if (!alreadyLinked) {
            chapterScenes.add(savedScene);
        }
        chapterRepo.save(chapter);
        return savedScene;
    }

    public List<Scene> addScenesToChapter(UUID chapterId, List<Scene> scenes) {
        Chapter chapter = chapterRepo.findById(chapterId).orElseThrow();
        List<Scene> toSave = scenes.stream()
                .peek(s -> {
                    if (s.getId() == null) {
                        s.setId(UUID.randomUUID());
                    }
                    if (s.getChapterId() == null) {
                        s.setChapterId(chapterId);
                    }
                    s.setChapter(chapter);
                })
                .collect(Collectors.toList());

        List<Scene> savedScenes = sceneRepo.saveAll(toSave);
        List<Scene> chapterScenes = chapter.getScenes();
        if (chapterScenes == null) {
            chapterScenes = new java.util.ArrayList<>();
            chapter.setScenes(chapterScenes);
        }

        Set<UUID> existingSceneIds = chapterScenes.stream()
                .map(Scene::getId)
                .collect(Collectors.toSet());
        for (Scene savedScene : savedScenes) {
            UUID sceneId = savedScene.getId();
            if (sceneId == null || existingSceneIds.add(sceneId)) {
                chapterScenes.add(savedScene);
            }
        }
        chapterRepo.save(chapter);
        return savedScenes;
    }

    @Transactional(readOnly = true)
    public List<Scene> findScenesByChapterId(UUID chapterId) {
        return sceneRepo.findByChapterId(chapterId);
    }

    public int deleteScenesByChapterId(UUID chapterId) {
        List<Scene> existing = sceneRepo.findByChapterId(chapterId);
        sceneRepo.deleteByChapterId(chapterId);
        return existing.size();
    }

    @Transactional(readOnly = true)
    public List<UUID> findChapterIdsUpTo(UUID bookId, int uptoChapterNumber) {
        return chapterReadRepo.findChapterIdsUpTo(bookId, uptoChapterNumber);
    }

    @Transactional(readOnly = true)
    public List<AbstractMap.SimpleEntry<UUID, UUID>> findChapterTemporalEdges(UUID chapterId) {
        return temporalReadRepo.findChapterEventEdges(chapterId).stream()
                .map(p -> new AbstractMap.SimpleEntry<>(p.getFromId(), p.getToId()))
                .collect(Collectors.toList());
    }

    public List<Chunk> addChunksToChapter(UUID chapterId, List<Chunk> chunks) {
        Chapter chapter = chapterRepo.findById(chapterId).orElseThrow();
        List<Chunk> toSave = chunks.stream()
                .peek(c -> {
                    if (c.getId() == null) {
                        c.setId(UUID.randomUUID());
                    }
                    c.setChapter(chapter);
                })
                .toList();

        List<Chunk> savedChunks = chunkRepo.saveAll(toSave);
        List<Chunk> existing = chapter.getChunks();
        if (existing == null) {
            existing = new java.util.ArrayList<>();
            chapter.setChunks(existing);
        }

        Set<UUID> existingChunkIds = existing.stream()
                .map(Chunk::getId)
                .collect(Collectors.toSet());
        for (Chunk savedChunk : savedChunks) {
            UUID chunkId = savedChunk.getId();
            if (chunkId == null || existingChunkIds.add(chunkId)) {
                existing.add(savedChunk);
            }
        }

        existing.sort(Comparator.comparing(
                Chunk::getChunkNumberInChapter,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));
        chapterRepo.save(chapter);
        return savedChunks;
    }

    public Chunk addChunkToScene(UUID sceneId, Chunk chunk) {
        Objects.requireNonNull(chunk, "chunk must not be null");
        if (chunk.getId() == null) {
            chunk.setId(UUID.randomUUID());
        }
        Chunk saved = chunkRepo.save(chunk);
        sceneRepo.linkChunkToScene(sceneId, saved.getId());
        return saved;
    }

    public List<Chunk> addChunksToScene(UUID sceneId, List<Chunk> chunks) {
        return chunks.stream().map(c -> addChunkToScene(sceneId, c)).toList();
    }

    @Transactional(readOnly = true)
    public List<Chapter> findChaptersByUniverse(String universe) {
        if (universe == null || universe.isBlank()) {
            return List.of();
        }
        return chapterRepo.findAll().stream()
                .filter(c -> universe.equals(c.getUniverse()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Chunk> findChunksByChapterId(UUID chapterId) {
        List<Chunk> viaScenes = chunkRepo.findByChapterIdViaScenes(chapterId);
        if (!viaScenes.isEmpty()) {
            return viaScenes;
        }
        return chunkRepo.findByChapterId(chapterId);
    }

    public int deleteChunksByChapterId(UUID chapterId) {
        int count = chunkRepo.countByChapterIdViaScenes(chapterId);
        if (count > 0) {
            chunkRepo.deleteByChapterIdViaScenes(chapterId);
            return count;
        }
        int legacy = chunkRepo.countByChapterId(chapterId);
        if (legacy > 0) {
            chunkRepo.deleteByChapterId(chapterId);
        }
        return legacy;
    }

    @Transactional(readOnly = true)
    public boolean chunksExistForChapter(UUID chapterId) {
        return chunkRepo.existsForChapterViaScenes(chapterId) || chunkRepo.existsForChapter(chapterId);
    }

    @Transactional(readOnly = true)
    public int countChunksByChapterId(UUID chapterId) {
        int via = chunkRepo.countByChapterIdViaScenes(chapterId);
        return via > 0 ? via : chunkRepo.countByChapterId(chapterId);
    }

    public Chunk updateChunk(Chunk chunk) {
        Objects.requireNonNull(chunk, "chunk must not be null");
        return chunkRepo.save(chunk);
    }

    public List<Chunk> updateChunks(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        Iterable<Chunk> saved = chunkRepo.saveAll(chunks);
        return StreamSupport.stream(saved.spliterator(), false).toList();
    }

    @Transactional(readOnly = true)
    public List<Chunk> findAllChunksWithEmbeddings() {
        return chunkRepo.findAllWithEmbeddings();
    }

    @Transactional(readOnly = true)
    public Optional<Chunk> findChunkById(UUID id) {
        return chunkRepo.findById(id);
    }

    public Universe createUniverse(Universe universe) {
        Objects.requireNonNull(universe, "universe must not be null");
        if (universe.getId() == null) {
            universe.setId(UUID.randomUUID());
        }
        return universeRepo.save(universe);
    }

    @Transactional(readOnly = true)
    public Optional<Universe> findUniverseById(UUID id) {
        return universeRepo.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Universe> findUniverseByName(String name) {
        return universeRepo.findByName(name);
    }

    @Transactional(readOnly = true)
    public List<Universe> findAllUniverses() {
        return universeRepo.findAll().stream().toList();
    }

    public Series createSeries(Series series) {
        Objects.requireNonNull(series, "series must not be null");
        if (series.getId() == null) {
            series.setId(UUID.randomUUID());
        }
        return seriesRepo.save(series);
    }

    @Transactional(readOnly = true)
    public Optional<Series> findSeriesById(UUID id) {
        return seriesRepo.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Series> findSeriesByNameAndUniverseId(String name, UUID universeId) {
        return seriesRepo.findByNameAndUniverseId(name, universeId);
    }

    @Transactional(readOnly = true)
    public List<Series> findSeriesByUniverseId(UUID universeId) {
        if (universeId == null) {
            return List.of();
        }
        return seriesRepo.findByUniverseId(universeId);
    }

    public Book createBook(Book book) {
        Objects.requireNonNull(book, "book must not be null");
        if (book.getId() == null) {
            book.setId(UUID.randomUUID());
        }
        return bookRepo.save(book);
    }

    @Transactional(readOnly = true)
    public Optional<Book> findBookById(UUID id) {
        return bookRepo.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Book> findBookByTitleAndSeriesId(String title, UUID seriesId) {
        return bookRepo.findByTitleAndSeriesId(title, seriesId);
    }

    @Transactional(readOnly = true)
    public Optional<Book> findStandaloneBookByTitleAndUniverseId(String title, UUID universeId) {
        return bookRepo.findStandaloneByTitleAndUniverseId(title, universeId);
    }

    @Transactional(readOnly = true)
    public List<Book> findBooksByUniverseId(UUID universeId) {
        if (universeId == null) {
            return List.of();
        }
        return bookRepo.findByUniverseId(universeId);
    }

    @Transactional(readOnly = true)
    public List<Book> findBooksBySeriesId(UUID seriesId) {
        if (seriesId == null) {
            return List.of();
        }
        return bookRepo.findBySeriesId(seriesId);
    }
}
