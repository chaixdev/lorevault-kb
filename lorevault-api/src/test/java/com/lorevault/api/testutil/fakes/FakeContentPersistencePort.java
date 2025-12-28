package com.lorevault.api.testutil.fakes;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.domain.content.Series;
import com.lorevault.api.domain.content.Book;

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
    
    // Hierarchy entities
    public final Map<UUID, Universe> universes = new ConcurrentHashMap<>();
    public final Map<UUID, Series> series = new ConcurrentHashMap<>();
    public final Map<UUID, Book> books = new ConcurrentHashMap<>();

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
    public List<UUID> findChapterIdsUpTo(UUID bookId, int uptoChapterNumber) {
        // Return chapter IDs for this book up to the specified chapter number, ordered
        return chapters.values().stream()
                .filter(c -> Objects.equals(c.getBookId(), bookId))
                .filter(c -> c.getChapterNumber() != null && c.getChapterNumber() <= uptoChapterNumber)
                .sorted(Comparator.comparingInt(Chapter::getChapterNumber))
                .map(Chapter::getId)
                .collect(Collectors.toList());
    }

    @Override
    public List<AbstractMap.SimpleEntry<UUID, UUID>> findChapterTemporalEdges(UUID chapterId) {
        // Return empty list - temporal edges not tracked in fake
        return List.of();
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
    public Optional<Chunk> findChunkById(UUID id) {
        return chunksByChapter.values().stream()
                .flatMap(List::stream)
                .filter(c -> Objects.equals(c.getId(), id))
                .findFirst();
    }

    @Override
    public List<Chapter> findChaptersByUniverse(String universe) {
        return chapters.values().stream().filter(c -> Objects.equals(c.getUniverse(), universe)).toList();
    }

    // Publication Hierarchy - Universes
    @Override
    public Universe createUniverse(Universe universe) {
        if (universe.getId() == null) universe.setId(UUID.randomUUID());
        universes.put(universe.getId(), universe);
        return universe;
    }

    @Override
    public Optional<Universe> findUniverseById(UUID id) {
        return Optional.ofNullable(universes.get(id));
    }

    @Override
    public Optional<Universe> findUniverseByName(String name) {
        return universes.values().stream()
                .filter(u -> Objects.equals(u.getName(), name))
                .findFirst();
    }

    @Override
    public List<Universe> findAllUniverses() {
        return new ArrayList<>(universes.values());
    }

    // Publication Hierarchy - Series
    @Override
    public Series createSeries(Series series) {
        if (series.getId() == null) series.setId(UUID.randomUUID());
        this.series.put(series.getId(), series);
        return series;
    }

    @Override
    public Optional<Series> findSeriesById(UUID id) {
        return Optional.ofNullable(series.get(id));
    }

    @Override
    public Optional<Series> findSeriesByNameAndUniverseId(String name, UUID universeId) {
        return series.values().stream()
                .filter(s -> Objects.equals(s.getName(), name) && Objects.equals(s.getUniverseId(), universeId))
                .findFirst();
    }

    @Override
    public List<Series> findSeriesByUniverseId(UUID universeId) {
        return series.values().stream()
                .filter(s -> Objects.equals(s.getUniverseId(), universeId))
                .collect(Collectors.toList());
    }

    // Publication Hierarchy - Books
    @Override
    public Book createBook(Book book) {
        if (book.getId() == null) book.setId(UUID.randomUUID());
        books.put(book.getId(), book);
        return book;
    }

    @Override
    public Optional<Book> findBookById(UUID id) {
        return Optional.ofNullable(books.get(id));
    }

    @Override
    public Optional<Book> findBookByTitleAndSeriesId(String title, UUID seriesId) {
        return books.values().stream()
                .filter(b -> Objects.equals(b.getTitle(), title) && Objects.equals(b.getSeriesId(), seriesId))
                .findFirst();
    }

    @Override
    public Optional<Book> findStandaloneBookByTitleAndUniverseId(String title, UUID universeId) {
        return books.values().stream()
                .filter(b -> Objects.equals(b.getTitle(), title) 
                          && Objects.equals(b.getUniverseId(), universeId) 
                          && b.getSeriesId() == null)
                .findFirst();
    }

    @Override
    public List<Book> findBooksByUniverseId(UUID universeId) {
        return books.values().stream()
                .filter(b -> Objects.equals(b.getUniverseId(), universeId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Book> findBooksBySeriesId(UUID seriesId) {
        return books.values().stream()
                .filter(b -> Objects.equals(b.getSeriesId(), seriesId))
                .collect(Collectors.toList());
    }
}
