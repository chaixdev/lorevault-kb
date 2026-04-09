package com.lorevault.api.testutil.fakes;

import com.lorevault.api.content.Book;
import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.Chunk;
import com.lorevault.api.content.Scene;
import com.lorevault.api.content.Series;
import com.lorevault.api.content.Universe;
import com.lorevault.api.content.BookGraphRepository;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.ChunkGraphRepository;
import com.lorevault.api.content.SceneGraphRepository;
import com.lorevault.api.content.SeriesGraphRepository;
import com.lorevault.api.content.UniverseGraphRepository;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory fake for all 6 repository interfaces used in service unit tests.
 *
 * Uses the delegation pattern: each interface is implemented by a separate inner class
 * that shares the same backing maps, avoiding Java type-erasure conflicts.
 *
 * Usage in tests:
 * <pre>
 *   FakeContentRepositories fake = new FakeContentRepositories();
 *
 *   // Pass the right typed view to each constructor arg:
 *   new EmbeddingService(fake.asChapterRepo(), fake.asChunkRepo(), embeddingModel)
 *   new SceneProcessingService(fake.asChapterRepo(), fake.asSceneRepo())
 *   new LibraryService(fake.asUniverseRepo(), fake.asSeriesRepo(), fake.asBookRepo())
 *
 *   // Direct data setup via helper methods on the outer class:
 *   fake.createChapter(chapter);
 *   fake.findScenesByChapterId(chapterId);
 * </pre>
 */
public class FakeContentRepositories {

    // -------------------------------------------------------------------------
    // Shared backing stores (accessible by all inner delegates)
    // -------------------------------------------------------------------------

    final Map<UUID, Chapter>     chapters        = new ConcurrentHashMap<>();
    final Map<UUID, List<Scene>> scenesByChapter = new ConcurrentHashMap<>();
    final Map<UUID, Scene>       scenesById      = new ConcurrentHashMap<>();
    final Map<UUID, List<Chunk>> chunksByChapter = new ConcurrentHashMap<>();
    final Map<UUID, Chunk>       chunksById      = new ConcurrentHashMap<>();
    final Map<UUID, Universe>    universes       = new ConcurrentHashMap<>();
    final Map<UUID, Series>      series          = new ConcurrentHashMap<>();
    final Map<UUID, Book>        books           = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Typed views — lazily created and cached
    // -------------------------------------------------------------------------

    private final ChapterDelegate  chapterDelegate  = new ChapterDelegate();
    private final ChunkDelegate    chunkDelegate    = new ChunkDelegate();
    private final SceneDelegate    sceneDelegate    = new SceneDelegate();
    private final UniverseDelegate universeDelegate = new UniverseDelegate();
    private final SeriesDelegate   seriesDelegate   = new SeriesDelegate();
    private final BookDelegate     bookDelegate     = new BookDelegate();

    public ChapterGraphRepository  asChapterRepo()  { return chapterDelegate; }
    public ChunkGraphRepository    asChunkRepo()    { return chunkDelegate; }
    public SceneGraphRepository    asSceneRepo()    { return sceneDelegate; }
    public UniverseGraphRepository asUniverseRepo() { return universeDelegate; }
    public SeriesGraphRepository   asSeriesRepo()   { return seriesDelegate; }
    public BookGraphRepository     asBookRepo()     { return bookDelegate; }

    // =========================================================================
    // Test-helper methods shared across repository views
    // =========================================================================

    public Chapter createChapter(Chapter chapter) {
        return chapterDelegate.save(chapter);
    }

    public Chapter updateChapter(Chapter chapter) {
        return chapterDelegate.save(chapter);
    }

    public Universe createUniverse(Universe universe) {
        return universeDelegate.save(universe);
    }

    public Series createSeries(Series seriesEntity) {
        return seriesDelegate.save(seriesEntity);
    }

    public Book createBook(Book book) {
        return bookDelegate.save(book);
    }

    public Optional<Universe> findUniverseById(UUID id) { return universeDelegate.findById(id); }
    public Optional<Series>   findSeriesById(UUID id)   { return seriesDelegate.findById(id); }
    public Optional<Book>     findBookById(UUID id)      { return bookDelegate.findById(id); }
    public Optional<Chapter>  findChapterById(UUID id)   { return chapterDelegate.findById(id); }

    public List<Scene> findScenesByChapterId(UUID chapterId) {
        return sceneDelegate.findByChapterId(chapterId);
    }

    public List<Chunk> findChunksByChapterId(UUID chapterId) {
        return chunkDelegate.findByChapterId(chapterId);
    }

    public List<Scene> addScenesToChapter(UUID chapterId, List<Scene> scenes) {
        scenesByChapter.computeIfAbsent(chapterId, k -> new ArrayList<>()).addAll(scenes);
        scenes.forEach(s -> { if (s.getId() != null) scenesById.put(s.getId(), s); });
        return scenes;
    }

    public void addChunksToChapter(UUID chapterId, List<Chunk> chunks) {
        chunksByChapter.computeIfAbsent(chapterId, k -> new ArrayList<>()).addAll(chunks);
        chunks.forEach(c -> { if (c.getId() != null) chunksById.put(c.getId(), c); });
    }

    public void addChunkToScene(UUID sceneId, Chunk chunk) {
        if (chunk.getId() == null) chunk.setId(UUID.randomUUID());
        chunksById.put(chunk.getId(), chunk);
    }

    // =========================================================================
    // Inner delegate: ChapterGraphRepository
    // =========================================================================

    final class ChapterDelegate implements ChapterGraphRepository {

        @Override
        public Optional<Chapter> findByContentHash(String contentHash) {
            return chapters.values().stream()
                    .filter(c -> Objects.equals(c.getContentHash(), contentHash))
                    .findFirst();
        }

        @Override
        public boolean existsByContentHash(String contentHash) {
            return findByContentHash(contentHash).isPresent();
        }

        @Override
        public List<Chapter> findByBookId(UUID bookId) {
            return chapters.values().stream()
                    .filter(c -> Objects.equals(c.getBookId(), bookId))
                    .sorted(Comparator.comparingInt(c -> c.getChapterNumber() == null ? 0 : c.getChapterNumber()))
                    .collect(Collectors.toList());
        }

        @Override
        public <S extends Chapter> S save(S chapter) {
            if (chapter.getId() == null) chapter.setId(UUID.randomUUID());
            chapters.put(chapter.getId(), chapter);
            return chapter;
        }

        @Override
        public <S extends Chapter> List<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S e : entities) result.add(save(e));
            return result;
        }

        @Override
        public Optional<Chapter> findById(UUID id) {
            return Optional.ofNullable(chapters.get(id));
        }

        @Override public boolean existsById(UUID id) { return chapters.containsKey(id); }

        @Override public List<Chapter> findAll() { return new ArrayList<>(chapters.values()); }

        @Override
        public List<Chapter> findAllById(Iterable<UUID> ids) {
            List<Chapter> result = new ArrayList<>();
            for (UUID id : ids) findById(id).ifPresent(result::add);
            return result;
        }

        @Override public long count() { return chapters.size(); }
        @Override public void deleteById(UUID id) { chapters.remove(id); }
        @Override public void delete(Chapter entity) { chapters.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { ids.forEach(chapters::remove); }
        @Override public void deleteAll(Iterable<? extends Chapter> entities) { entities.forEach(e -> chapters.remove(e.getId())); }
        @Override public void deleteAll() { chapters.clear(); }

        @Override public List<Chapter> findAll(Sort sort) { return findAll(); }
        @Override public Page<Chapter> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chapter> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chapter> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chapter> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chapter> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chapter> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chapter> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chapter, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }

    }

    // =========================================================================
    // Inner delegate: ChunkGraphRepository
    // =========================================================================

    final class ChunkDelegate implements ChunkGraphRepository {

        @Override
        public List<Chunk> findByChapterId(UUID chapterId) {
            return chunksByChapter.getOrDefault(chapterId, List.of());
        }

        @Override
        public boolean existsForChapter(UUID chapterId) {
            return !findByChapterId(chapterId).isEmpty();
        }

        @Override
        public int countByChapterId(UUID chapterId) {
            return findByChapterId(chapterId).size();
        }

        @Override
        public void deleteByChapterId(UUID chapterId) {
            List<Chunk> removed = chunksByChapter.remove(chapterId);
            if (removed != null) removed.forEach(c -> chunksById.remove(c.getId()));
        }

        @Override
        public List<Chunk> findByChapterIdViaScenes(UUID chapterId) {
            return findByChapterId(chapterId);
        }

        @Override
        public boolean existsForChapterViaScenes(UUID chapterId) {
            return existsForChapter(chapterId);
        }

        @Override
        public int countByChapterIdViaScenes(UUID chapterId) {
            return countByChapterId(chapterId);
        }

        @Override
        public void deleteByChapterIdViaScenes(UUID chapterId) {
            deleteByChapterId(chapterId);
        }

        @Override
        public List<Chunk> findUnembeddedByChapterId(UUID chapterId) {
            return findByChapterId(chapterId).stream()
                    .filter(c -> c.getEmbedding() == null || c.getEmbeddingHash() == null)
                    .collect(Collectors.toList());
        }

        @Override
        public List<Chunk> findStaleEmbeddingsByChapterId(UUID chapterId, String expectedHash) {
            return findByChapterId(chapterId).stream()
                    .filter(c -> !Objects.equals(c.getEmbeddingHash(), expectedHash))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Chunk> findAllWithEmbeddings() {
            return chunksById.values().stream()
                    .filter(c -> c.getEmbedding() != null)
                    .collect(Collectors.toList());
        }

        @Override
        public <S extends Chunk> S save(S chunk) {
            if (chunk.getId() == null) chunk.setId(UUID.randomUUID());
            chunksById.put(chunk.getId(), chunk);
            return chunk;
        }

        @Override
        public <S extends Chunk> List<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S e : entities) result.add(save(e));
            return result;
        }

        @Override
        public Optional<Chunk> findById(UUID id) {
            if (chunksById.containsKey(id)) return Optional.of(chunksById.get(id));
            return chunksByChapter.values().stream()
                    .flatMap(List::stream)
                    .filter(c -> Objects.equals(c.getId(), id))
                    .findFirst();
        }

        @Override
        public boolean existsById(UUID id) {
            return chunksById.containsKey(id) ||
                   chunksByChapter.values().stream().flatMap(List::stream).anyMatch(c -> Objects.equals(c.getId(), id));
        }

        @Override
        public List<Chunk> findAll() {
            Set<Chunk> all = new LinkedHashSet<>(chunksById.values());
            chunksByChapter.values().forEach(all::addAll);
            return new ArrayList<>(all);
        }

        @Override
        public List<Chunk> findAllById(Iterable<UUID> ids) {
            List<Chunk> result = new ArrayList<>();
            for (UUID id : ids) findById(id).ifPresent(result::add);
            return result;
        }

        @Override public long count() { return findAll().size(); }

        @Override
        public void deleteById(UUID id) {
            chunksById.remove(id);
            chunksByChapter.values().forEach(list -> list.removeIf(c -> Objects.equals(c.getId(), id)));
        }

        @Override public void delete(Chunk entity) { deleteById(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { ids.forEach(this::deleteById); }
        @Override public void deleteAll(Iterable<? extends Chunk> entities) { entities.forEach(e -> deleteById(e.getId())); }
        @Override public void deleteAll() { chunksById.clear(); chunksByChapter.clear(); }

        @Override public List<Chunk> findAll(Sort sort) { return findAll(); }
        @Override public Page<Chunk> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chunk> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chunk> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chunk> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chunk> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chunk> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chunk> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Chunk, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }

    }

    // =========================================================================
    // Inner delegate: SceneGraphRepository
    // =========================================================================

    final class SceneDelegate implements SceneGraphRepository {

        @Override
        public List<Scene> findByChapterId(UUID chapterId) {
            return scenesByChapter.getOrDefault(chapterId, List.of());
        }

        @Override
        public void deleteByChapterId(UUID chapterId) {
            List<Scene> removed = scenesByChapter.remove(chapterId);
            if (removed != null) removed.forEach(s -> scenesById.remove(s.getId()));
        }

        @Override public long countMeetsBetween(UUID fromId, UUID toId) { return 0; }
        @Override public void createMeetsBetween(UUID fromId, UUID toId) { }
        @Override public void linkChunkToScene(UUID sceneId, UUID chunkId) { }
        @Override public void linkSceneToChapter(UUID chapterId, UUID sceneId) { }

        @Override
        public <S extends Scene> S save(S scene) {
            if (scene.getId() == null) scene.setId(UUID.randomUUID());
            scenesById.put(scene.getId(), scene);
            UUID chapterId = scene.getChapterId();
            if (chapterId != null) {
                List<Scene> list = scenesByChapter.computeIfAbsent(chapterId, k -> new ArrayList<>());
                list.removeIf(s -> Objects.equals(s.getId(), scene.getId()));
                list.add(scene);
            }
            return scene;
        }

        @Override
        public <S extends Scene> List<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S e : entities) result.add(save(e));
            return result;
        }

        @Override public Optional<Scene> findById(UUID id) { return Optional.ofNullable(scenesById.get(id)); }
        @Override public boolean existsById(UUID id) { return scenesById.containsKey(id); }
        @Override public List<Scene> findAll() { return new ArrayList<>(scenesById.values()); }

        @Override
        public List<Scene> findAllById(Iterable<UUID> ids) {
            List<Scene> result = new ArrayList<>();
            for (UUID id : ids) findById(id).ifPresent(result::add);
            return result;
        }

        @Override public long count() { return scenesById.size(); }

        @Override
        public void deleteById(UUID id) {
            Scene removed = scenesById.remove(id);
            if (removed != null) scenesByChapter.values().forEach(list -> list.remove(removed));
        }

        @Override public void delete(Scene entity) { deleteById(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { ids.forEach(this::deleteById); }
        @Override public void deleteAll(Iterable<? extends Scene> entities) { entities.forEach(e -> deleteById(e.getId())); }
        @Override public void deleteAll() { scenesById.clear(); scenesByChapter.clear(); }

        @Override public List<Scene> findAll(Sort sort) { return findAll(); }
        @Override public Page<Scene> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Scene> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Scene> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Scene> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends Scene> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Scene> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Scene> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Scene, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }

    }

    // =========================================================================
    // Inner delegate: UniverseGraphRepository
    // =========================================================================

    final class UniverseDelegate implements UniverseGraphRepository {

        @Override
        public Optional<Universe> findByName(String name) {
            return universes.values().stream()
                    .filter(u -> Objects.equals(u.getName(), name))
                    .findFirst();
        }

        @Override
        public <S extends Universe> S save(S universe) {
            if (universe.getId() == null) universe.setId(UUID.randomUUID());
            universes.put(universe.getId(), universe);
            return universe;
        }

        @Override
        public <S extends Universe> List<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S e : entities) result.add(save(e));
            return result;
        }

        @Override public Optional<Universe> findById(UUID id) { return Optional.ofNullable(universes.get(id)); }
        @Override public boolean existsById(UUID id) { return universes.containsKey(id); }
        @Override public List<Universe> findAll() { return new ArrayList<>(universes.values()); }

        @Override
        public List<Universe> findAllById(Iterable<UUID> ids) {
            List<Universe> result = new ArrayList<>();
            for (UUID id : ids) findById(id).ifPresent(result::add);
            return result;
        }

        @Override public long count() { return universes.size(); }
        @Override public void deleteById(UUID id) { universes.remove(id); }
        @Override public void delete(Universe entity) { universes.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { ids.forEach(universes::remove); }
        @Override public void deleteAll(Iterable<? extends Universe> entities) { entities.forEach(e -> universes.remove(e.getId())); }
        @Override public void deleteAll() { universes.clear(); }

        @Override public List<Universe> findAll(Sort sort) { return findAll(); }
        @Override public Page<Universe> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Universe> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Universe> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Universe> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends Universe> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Universe> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Universe> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Universe, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }

    }

    // =========================================================================
    // Inner delegate: SeriesGraphRepository
    // =========================================================================

    final class SeriesDelegate implements SeriesGraphRepository {

        @Override
        public Optional<Series> findByNameAndUniverseId(String name, UUID universeId) {
            return series.values().stream()
                    .filter(s -> Objects.equals(s.getName(), name) && Objects.equals(s.getUniverseId(), universeId))
                    .findFirst();
        }

        @Override
        public List<Series> findByUniverseId(UUID universeId) {
            return series.values().stream()
                    .filter(s -> Objects.equals(s.getUniverseId(), universeId))
                    .collect(Collectors.toList());
        }

        @Override
        public <S extends Series> S save(S seriesEntity) {
            if (seriesEntity.getId() == null) seriesEntity.setId(UUID.randomUUID());
            series.put(seriesEntity.getId(), seriesEntity);
            return seriesEntity;
        }

        @Override
        public <S extends Series> List<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S e : entities) result.add(save(e));
            return result;
        }

        @Override public Optional<Series> findById(UUID id) { return Optional.ofNullable(series.get(id)); }
        @Override public boolean existsById(UUID id) { return series.containsKey(id); }
        @Override public List<Series> findAll() { return new ArrayList<>(series.values()); }

        @Override
        public List<Series> findAllById(Iterable<UUID> ids) {
            List<Series> result = new ArrayList<>();
            for (UUID id : ids) findById(id).ifPresent(result::add);
            return result;
        }

        @Override public long count() { return series.size(); }
        @Override public void deleteById(UUID id) { series.remove(id); }
        @Override public void delete(Series entity) { series.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { ids.forEach(series::remove); }
        @Override public void deleteAll(Iterable<? extends Series> entities) { entities.forEach(e -> series.remove(e.getId())); }
        @Override public void deleteAll() { series.clear(); }

        @Override public List<Series> findAll(Sort sort) { return findAll(); }
        @Override public Page<Series> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Series> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Series> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Series> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends Series> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Series> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Series> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Series, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }

    }

    // =========================================================================
    // Inner delegate: BookGraphRepository
    // =========================================================================

    final class BookDelegate implements BookGraphRepository {

        @Override
        public Optional<Book> findByTitleAndSeriesId(String title, UUID seriesId) {
            return books.values().stream()
                    .filter(b -> Objects.equals(b.getTitle(), title) && Objects.equals(b.getSeriesId(), seriesId))
                    .findFirst();
        }

        @Override
        public Optional<Book> findStandaloneByTitleAndUniverseId(String title, UUID universeId) {
            return books.values().stream()
                    .filter(b -> Objects.equals(b.getTitle(), title)
                              && Objects.equals(b.getUniverseId(), universeId)
                              && b.getSeriesId() == null)
                    .findFirst();
        }

        @Override
        public List<Book> findByUniverseId(UUID universeId) {
            return books.values().stream()
                    .filter(b -> Objects.equals(b.getUniverseId(), universeId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Book> findBySeriesId(UUID seriesId) {
            return books.values().stream()
                    .filter(b -> Objects.equals(b.getSeriesId(), seriesId))
                    .collect(Collectors.toList());
        }

        @Override
        public <S extends Book> S save(S book) {
            if (book.getId() == null) book.setId(UUID.randomUUID());
            books.put(book.getId(), book);
            return book;
        }

        @Override
        public <S extends Book> List<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S e : entities) result.add(save(e));
            return result;
        }

        @Override public Optional<Book> findById(UUID id) { return Optional.ofNullable(books.get(id)); }
        @Override public boolean existsById(UUID id) { return books.containsKey(id); }
        @Override public List<Book> findAll() { return new ArrayList<>(books.values()); }

        @Override
        public List<Book> findAllById(Iterable<UUID> ids) {
            List<Book> result = new ArrayList<>();
            for (UUID id : ids) findById(id).ifPresent(result::add);
            return result;
        }

        @Override public long count() { return books.size(); }
        @Override public void deleteById(UUID id) { books.remove(id); }
        @Override public void delete(Book entity) { books.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { ids.forEach(books::remove); }
        @Override public void deleteAll(Iterable<? extends Book> entities) { entities.forEach(e -> books.remove(e.getId())); }
        @Override public void deleteAll() { books.clear(); }

        @Override public List<Book> findAll(Sort sort) { return findAll(); }
        @Override public Page<Book> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Book> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Book> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Book> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends Book> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Book> long count(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Book> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends Book, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }

    }
}
