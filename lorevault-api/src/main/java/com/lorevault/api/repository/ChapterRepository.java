package com.lorevault.api.repository;

import com.lorevault.api.model.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
