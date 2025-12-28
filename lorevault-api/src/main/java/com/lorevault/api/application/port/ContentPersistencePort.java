package com.lorevault.api.application.port;

import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.domain.content.Series;
import com.lorevault.api.domain.content.Book;

import java.util.AbstractMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentPersistencePort {

    // Chapters
    Chapter createChapter(Chapter chapter);
    Optional<Chapter> findChapterById(UUID id);
    Optional<Chapter> findChapterByContentHash(String contentHash);
    boolean chapterExistsByContentHash(String contentHash);
    Chapter updateChapter(Chapter chapter);
    
    /**
     * Return Chapter IDs for a book where chapterNumber <= uptoChapterNumber, ordered by chapterNumber.
     * Used by triad builder to find preceding chapters for cross-chapter scene triads.
     */
    List<UUID> findChapterIdsUpTo(UUID bookId, int uptoChapterNumber);

    // Scenes
    Scene addSceneToChapter(UUID chapterId, Scene scene);
    List<Scene> addScenesToChapter(UUID chapterId, List<Scene> scenes);
    List<Scene> findScenesByChapterId(UUID chapterId);
    int deleteScenesByChapterId(UUID chapterId);
    
    /**
     * Find directed precedence edges among scene events within a chapter.
     * Edges represent strict "earlier -> later" constraints.
     * Used by event ordering service for topological sort.
     */
    List<AbstractMap.SimpleEntry<UUID, UUID>> findChapterTemporalEdges(UUID chapterId);

    // Chunks
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

    /**
     * Find a single chunk by its ID.
     * Used by RAG to retrieve full chunk text for LLM context.
     */
    Optional<Chunk> findChunkById(UUID id);

    // Queries
    List<Chapter> findChaptersByUniverse(String universe);

    // Publication Hierarchy - Universes
    Universe createUniverse(Universe universe);
    Optional<Universe> findUniverseById(UUID id);
    Optional<Universe> findUniverseByName(String name);
    List<Universe> findAllUniverses();

    // Publication Hierarchy - Series
    Series createSeries(Series series);
    Optional<Series> findSeriesById(UUID id);
    Optional<Series> findSeriesByNameAndUniverseId(String name, UUID universeId);
    List<Series> findSeriesByUniverseId(UUID universeId);

    // Publication Hierarchy - Books
    Book createBook(Book book);
    Optional<Book> findBookById(UUID id);
    Optional<Book> findBookByTitleAndSeriesId(String title, UUID seriesId);
    Optional<Book> findStandaloneBookByTitleAndUniverseId(String title, UUID universeId);
    List<Book> findBooksByUniverseId(UUID universeId);
    List<Book> findBooksBySeriesId(UUID seriesId);
}
