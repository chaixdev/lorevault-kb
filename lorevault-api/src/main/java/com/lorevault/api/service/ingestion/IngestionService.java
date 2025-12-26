package com.lorevault.api.service.ingestion;

import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.dto.ingestion.JobListResponse;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Book;
import com.lorevault.api.domain.ingestion.IngestionJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static com.lorevault.api.util.HashUtils.generateSha256Hash;

/**
 * Service for chapter submission and job management.
 * 
 * Responsibilities:
 * - Chapter validation and duplicate detection
 * - Ingestion job creation and query
 * - Publishing events to trigger async processing pipeline
 * 
 * Processing is handled by event-driven handlers:
 * SceneDetectionHandler → ChunkingHandler → EmbeddingHandler → CompletionHandler
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final ContentPersistencePort contentPersistencePort;
    private final IngestionJobService ingestionJobService;
    private final ApplicationEventPublisher eventPublisher;

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
     * Submit a chapter for processing with integrated validation and duplicate detection.
     * Publishes ChapterIngestionEvent which triggers the async processing pipeline.
     */
    @Transactional
    public SubmitChapterResponse submitChapter(SubmitChapterRequest request) {
        log.info("Processing chapter submission: bookId={}, chapterNumber={}, title={}",
            request.getBookId(), request.getChapterNumber(), request.getChapterTitle());

        // Validate chapter and handle duplicates
        ChapterValidationResult validationResult = validateAndProcessChapter(request);

        UUID chapterId = validationResult.getChapterId();

        // Handle existing chapter case
        if (validationResult.isExistingChapter()) {
            if (validationResult.hasActiveJob()) {
                Optional<UUID> activeJobId = findMostRecentJobId(chapterId);
                if (activeJobId.isPresent()) {
                    return SubmitChapterResponse.success(activeJobId.get(), chapterId);
                }
            }
            
            // Create new job for existing chapter
            IngestionJob job = ingestionJobService.createIngestionJob(chapterId);
            eventPublisher.publishEvent(new ChapterIngestionEvent(this, job.getId(), chapterId));
            return SubmitChapterResponse.success(job.getId(), chapterId);
        }

        // Create job for new chapter
        IngestionJob job = ingestionJobService.createIngestionJob(chapterId);
        eventPublisher.publishEvent(new ChapterIngestionEvent(this, job.getId(), chapterId));
        return SubmitChapterResponse.success(job.getId(), chapterId);
    }

    /**
     * Get the status of an ingestion job
     */
    public Optional<JobStatusResponse> getJobStatus(UUID jobId) {
        return ingestionJobService.getJobStatus(jobId);
    }

    /**
     * List jobs with pagination and filtering
     */
    public JobListResponse listJobs(String universe, String status, int limit, int offset) {
        return ingestionJobService.listJobs(universe, status, limit, offset);
    }

    // ========== Private Chapter Validation Methods ==========

    /**
     * Validate chapter submission and handle duplicate detection
     */
    @Transactional
    private ChapterValidationResult validateAndProcessChapter(SubmitChapterRequest request) {
        log.info("Validating chapter submission: bookId={}, chapterNumber={}, title={}",
            request.getBookId(), request.getChapterNumber(), request.getChapterTitle());

        String contentHash = generateSha256Hash(request.getChapterText());

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
    private boolean checkForActiveJob(UUID chapterId) {
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
    private Optional<UUID> findMostRecentJobId(UUID chapterId) {
        try {
            return contentPersistencePort.findMostRecentJobForChapter(chapterId)
                    .map(IngestionJob::getId);
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
        // Lookup book and derive hierarchy info
        Book book = contentPersistencePort.findBookById(request.getBookId())
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + request.getBookId()));

        PublicationCoordinates coords = new PublicationCoordinates();
        coords.setUniverse(book.getUniverse());
        coords.setSeries(book.getSeries());
        coords.setBookTitle(book.getTitle());
        coords.setChapterTitle(request.getChapterTitle());
        coords.setBookNumber(book.getBookNumber() != null ? book.getBookNumber() : 0);
        coords.setChapterNumber(request.getChapterNumber());

        // Build Chapter with stable references
        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setBookId(book.getId());
        chapter.setUniverseId(book.getUniverseId());
        chapter.setSeriesId(book.getSeriesId());
        chapter.setCoordinates(coords);
        chapter.setChapterTitle(request.getChapterTitle());
        chapter.setRawText(request.getChapterText());
        chapter.setContentHash(contentHash);
        return chapter;
    }
}
