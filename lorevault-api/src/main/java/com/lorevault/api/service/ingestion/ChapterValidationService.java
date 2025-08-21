package com.lorevault.api.service.ingestion;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.service.shared.HashService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for chapter validation and duplicate detection.
 * Handles content hash generation, duplicate checking, and new chapter creation.
 * Extracted from IngestionService to improve single responsibility and testability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterValidationService {

    private final HashService hashService;
    private final ContentPersistencePort contentPersistencePort;

    /**
     * Context object for chapter validation results
     */
    public static class ChapterValidationResult {
        private final boolean isExistingChapter;
        private final UUID chapterId;
        private final String contentHash;
        private final boolean hasActiveJob;

        private ChapterValidationResult(boolean isExistingChapter, UUID chapterId, String contentHash, boolean hasActiveJob) {
            this.isExistingChapter = isExistingChapter;
            this.chapterId = chapterId;
            this.contentHash = contentHash;
            this.hasActiveJob = hasActiveJob;
        }

        public static ChapterValidationResult existingChapter(UUID chapterId, String contentHash, boolean hasActiveJob) {
            return new ChapterValidationResult(true, chapterId, contentHash, hasActiveJob);
        }

        public static ChapterValidationResult newChapter(UUID chapterId, String contentHash) {
            return new ChapterValidationResult(false, chapterId, contentHash, false);
        }

        public boolean isExistingChapter() { return isExistingChapter; }
        public UUID getChapterId() { return chapterId; }
        public String getContentHash() { return contentHash; }
        public boolean hasActiveJob() { return hasActiveJob; }
    }

    /**
     * Validate chapter submission and handle duplicate detection
     * @param request The chapter submission request
     * @return Validation result containing chapter ID and duplicate status
     */
    @Transactional
    public ChapterValidationResult validateAndProcessChapter(SubmitChapterRequest request) {
        log.info("Validating chapter submission: {} - {}", 
                request.getCoordinates(), request.getChapterTitle());

        String contentHash = hashService.generateSha256Hash(request.getChapterText());

        // Check for existing chapter with same content
    Optional<Chapter> existingChapter = findExistingChapterByHash(contentHash);
        if (existingChapter.isPresent()) {
            UUID chapterId = existingChapter.get().getId();
            boolean hasActiveJob = checkForActiveJob(chapterId);
            return ChapterValidationResult.existingChapter(chapterId, contentHash, hasActiveJob);
        }

        // Create new chapter
        UUID newChapterId = createNewChapter(request, contentHash);
        return ChapterValidationResult.newChapter(newChapterId, contentHash);
    }

    /**
     * Check if a chapter has an active processing job
     */
    public boolean checkForActiveJob(UUID chapterId) {
        try {
            return contentPersistencePort.hasActiveJobForChapter(chapterId);
        } catch (Exception e) {
            log.warn("Failed to check for active job for chapter {}: {}", chapterId, e.getMessage());
            return false;
        }
    }

    /**
     * Find the most recent job for a chapter
     */
    public Optional<UUID> findMostRecentJobId(UUID chapterId) {
        try {
            return contentPersistencePort.findMostRecentJobForChapter(chapterId)
                    .map(job -> job.getId());
        } catch (Exception e) {
            log.warn("Failed to find most recent job for chapter {}: {}", chapterId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Chapter> findExistingChapterByHash(String contentHash) {
        try {
            return contentPersistencePort.findChapterByContentHash(contentHash);
        } catch (Exception e) {
            log.warn("Graph lookup failed for content hash {}: {}", contentHash, e.getMessage());
            return Optional.empty();
        }
    }

    private UUID createNewChapter(SubmitChapterRequest request, String contentHash) {
        try {
            Chapter chapter = buildChapter(request, contentHash);
            Chapter persisted = contentPersistencePort.createChapter(chapter);
            
            // Handle mock scenarios where createChapter might return null
            UUID chapterId = (persisted != null) ? persisted.getId() : chapter.getId();
            
            log.debug("Created new chapter with ID: {}", chapterId);
            return chapterId;
            
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create chapter in graph: " + e.getMessage(), e);
        }
    }

    private Chapter buildChapter(SubmitChapterRequest request, String contentHash) {
        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        
        PublicationCoordinates coords = request.getCoordinates();
        chapter.setCoordinates(coords);
        chapter.setChapterTitle(request.getChapterTitle());
        chapter.setRawText(request.getChapterText());
        chapter.setContentHash(contentHash);
        
        return chapter;
    }
}
