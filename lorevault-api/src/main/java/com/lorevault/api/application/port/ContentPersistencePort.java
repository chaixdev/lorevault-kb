package com.lorevault.api.application.port;

import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.content.Series;
import com.lorevault.api.domain.content.Universe;

import java.util.AbstractMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentPersistencePort {

    Chapter createChapter(Chapter chapter);
    Optional<Chapter> findChapterById(UUID id);
    Optional<Chapter> findChapterByContentHash(String contentHash);
    boolean chapterExistsByContentHash(String contentHash);
    Chapter updateChapter(Chapter chapter);
    List<UUID> findChapterIdsUpTo(UUID bookId, int uptoChapterNumber);

    Scene addSceneToChapter(UUID chapterId, Scene scene);
    List<Scene> addScenesToChapter(UUID chapterId, List<Scene> scenes);
    List<Scene> findScenesByChapterId(UUID chapterId);
    int deleteScenesByChapterId(UUID chapterId);
    List<AbstractMap.SimpleEntry<UUID, UUID>> findChapterTemporalEdges(UUID chapterId);

    List<Chunk> addChunksToChapter(UUID chapterId, List<Chunk> chunks);
    Chunk addChunkToScene(UUID sceneId, Chunk chunk);
    List<Chunk> addChunksToScene(UUID sceneId, List<Chunk> chunks);
    List<Chunk> findChunksByChapterId(UUID chapterId);
    int deleteChunksByChapterId(UUID chapterId);
    boolean chunksExistForChapter(UUID chapterId);
    int countChunksByChapterId(UUID chapterId);
    Chunk updateChunk(Chunk chunk);
    List<Chunk> updateChunks(List<Chunk> chunks);
    List<Chunk> findAllChunksWithEmbeddings();
    Optional<Chunk> findChunkById(UUID id);

    List<Chapter> findChaptersByUniverse(String universe);

    Universe createUniverse(Universe universe);
    Optional<Universe> findUniverseById(UUID id);
    Optional<Universe> findUniverseByName(String name);
    List<Universe> findAllUniverses();

    Series createSeries(Series series);
    Optional<Series> findSeriesById(UUID id);
    Optional<Series> findSeriesByNameAndUniverseId(String name, UUID universeId);
    List<Series> findSeriesByUniverseId(UUID universeId);

    Book createBook(Book book);
    Optional<Book> findBookById(UUID id);
    Optional<Book> findBookByTitleAndSeriesId(String title, UUID seriesId);
    Optional<Book> findStandaloneBookByTitleAndUniverseId(String title, UUID universeId);
    List<Book> findBooksByUniverseId(UUID universeId);
    List<Book> findBooksBySeriesId(UUID seriesId);
}
