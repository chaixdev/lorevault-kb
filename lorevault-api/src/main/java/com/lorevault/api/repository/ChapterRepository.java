package com.lorevault.api.repository;

import com.lorevault.api.model.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Chapter entities
 */
@Repository
public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    /**
     * Find a chapter by its content hash to prevent duplicate processing
     */
    Optional<Chapter> findByContentHash(String contentHash);

    /**
     * Check if a chapter with the given content hash already exists
     */
    boolean existsByContentHash(String contentHash);

    /**
     * Find a chapter by ID with scenes eagerly loaded to avoid LazyInitializationException
     * in async processing context
     */
    @Query("SELECT c FROM Chapter c LEFT JOIN FETCH c.scenes WHERE c.id = :id")
    Optional<Chapter> findByIdWithScenes(@Param("id") UUID id);
}
