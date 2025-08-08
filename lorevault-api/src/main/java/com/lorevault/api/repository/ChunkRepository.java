package com.lorevault.api.repository;

import com.lorevault.api.domain.content.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing Chunk entities
 */
@Repository
public interface ChunkRepository extends JpaRepository<Chunk, UUID> {

    /**
     * Find all chunks belonging to a specific chapter, ordered by chunk number
     */
    @Query("SELECT c FROM Chunk c WHERE c.chapter.id = :chapterId ORDER BY c.chunkNumberInChapter")
    List<Chunk> findByChapterIdOrderByChunkNumber(@Param("chapterId") UUID chapterId);

    /**
     * Find a chunk by its content hash
     */
    Optional<Chunk> findByContentHash(String contentHash);

    /**
     * Check if any chunks exist for a given chapter
     */
    @Query("SELECT COUNT(c) > 0 FROM Chunk c WHERE c.chapter.id = :chapterId")
    boolean existsByChapterId(@Param("chapterId") UUID chapterId);

    /**
     * Get the count of chunks for a specific chapter
     */
    @Query("SELECT COUNT(c) FROM Chunk c WHERE c.chapter.id = :chapterId")
    int countByChapterId(@Param("chapterId") UUID chapterId);

    /**
     * Find the maximum chunk number for a chapter
     */
    @Query("SELECT MAX(c.chunkNumberInChapter) FROM Chunk c WHERE c.chapter.id = :chapterId")
    Optional<Integer> findMaxChunkNumberByChapterId(@Param("chapterId") UUID chapterId);

    /**
     * Delete all chunks for a specific chapter (via relationship)
     */
    @Modifying
    @Query("DELETE FROM Chunk c WHERE c.chapter.id = :chapterId")
    void deleteByChapterId(@Param("chapterId") UUID chapterId);
    
    /**
     * Delete all chunks for a specific chapter and return the count of deleted items
     */
    @Modifying
    @Query("DELETE FROM Chunk c WHERE c.chapter.id = :chapterId")
    int deleteAllByChapterId(@Param("chapterId") UUID chapterId);
}
