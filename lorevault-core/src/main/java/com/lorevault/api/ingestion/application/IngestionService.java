package com.lorevault.api.ingestion.application;

import com.lorevault.api.ingestion.application.result.IngestionSubmissionResult;
import com.lorevault.api.ingestion.application.result.JobStatusDetails;
import com.lorevault.api.ingestion.application.result.PaginatedJobSummaries;

import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.IngestionFailure;

import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.infrastructure.IngestionJobGraphRepository;
import com.lorevault.api.content.PublicationCoordinates;
import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.Book;
import com.lorevault.api.content.BookGraphRepository;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.ingestion.events.ChapterIngestionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;

import java.util.function.Supplier;
import java.util.Optional;
import java.util.UUID;

import static com.lorevault.api.ingestion.infrastructure.HashUtils.generateSha256Hash;

/**
 * Service for chapter submission and job management.
 * 
 * Responsibilities:
 * - Chapter validation and duplicate detection
 * - Ingestion job creation and query
 * - Publishing events to trigger async processing pipeline
 * 
 * Processing is handled by event-driven handlers:
 * SceneDetectionHandler → ChunkingHandler → EmbeddingHandler
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final ChapterGraphRepository chapterRepo;
    private final BookGraphRepository bookRepo;
    private final IngestionJobService ingestionJobService;
    private final IngestionJobGraphRepository jobRepo;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Optional transaction manager used to isolate best-effort lookup queries.
     *
     * Why: some Neo4j/SDN failures terminate the *current* transaction; if we swallow that exception and
     * keep executing more queries in the same transaction, we can hit "Cannot run more queries in this transaction".
     * Running these lookups in a REQUIRES_NEW read-only transaction prevents poisoning the submit transaction.
     */
    @Autowired(required = false)
    @Nullable
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate requiresNewReadOnlyTx;

    @PostConstruct
    void initTransactionTemplates() {
        PlatformTransactionManager tm = this.transactionManager;
        if (tm == null) {
            return;
        }
        TransactionTemplate template = new TransactionTemplate(tm);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setReadOnly(true);
        this.requiresNewReadOnlyTx = template;
    }

    private <T> T bestEffortLookup(String operation, Supplier<T> supplier, T fallback) {
        try {
            if (requiresNewReadOnlyTx != null) {
                return requiresNewReadOnlyTx.execute(status -> supplier.get());
            }
            return supplier.get();
        } catch (Exception e) {
            log.warn("Best-effort lookup failed ({}): {}", operation, e.getMessage());
            log.debug("Best-effort lookup failure details ({}):", operation, e);
            return fallback;
        }
    }

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
    public IngestionSubmissionResult submitChapter(UUID bookId, Integer chapterNumber, String chapterTitle, String chapterText) {
        log.info("Processing chapter submission: bookId={}, chapterNumber={}, title={}",
            bookId, chapterNumber, chapterTitle);

        // Validate chapter and handle duplicates
        ChapterValidationResult validationResult = validateAndProcessChapter(bookId, chapterNumber, chapterTitle, chapterText);

        UUID chapterId = validationResult.getChapterId();

        // Handle existing chapter case
        if (validationResult.isExistingChapter()) {
            if (validationResult.hasActiveJob()) {
                Optional<UUID> activeJobId = findMostRecentJobId(chapterId);
                if (activeJobId.isPresent()) {
                    return new IngestionSubmissionResult(activeJobId.get(), chapterId);
                }
            }
            
            // Create new job for existing chapter
            IngestionJob job = ingestionJobService.createIngestionJob(chapterId);
            eventPublisher.publishEvent(new ChapterIngestionEvent(this, job.getId(), chapterId));
            return new IngestionSubmissionResult(job.getId(), chapterId);
        }

        // Create job for new chapter
        IngestionJob job = ingestionJobService.createIngestionJob(chapterId);
        eventPublisher.publishEvent(new ChapterIngestionEvent(this, job.getId(), chapterId));
        return new IngestionSubmissionResult(job.getId(), chapterId);
    }

    /**
     * Get the status of an ingestion job
     */
    public Optional<JobStatusDetails> getJobStatus(UUID jobId) {
        return ingestionJobService.getJobStatus(jobId);
    }

    /**
     * List jobs with pagination and filtering
     */
    public PaginatedJobSummaries listJobs(String universe, String status, int limit, int offset) {
        return ingestionJobService.listJobs(universe, status, limit, offset);
    }

    // ========== Private Chapter Validation Methods ==========

    /**
     * Validate chapter submission and handle duplicate detection
     */
    @Transactional
    private ChapterValidationResult validateAndProcessChapter(UUID bookId, Integer chapterNumber, String chapterTitle, String chapterText) {
        log.info("Validating chapter submission: bookId={}, chapterNumber={}, title={}",
            bookId, chapterNumber, chapterTitle);

        String contentHash = generateSha256Hash(chapterText);

        // Check for existing chapter with same content
        Optional<Chapter> existingChapter = findExistingChapterByHash(contentHash);
        if (existingChapter.isPresent()) {
            UUID chapterId = existingChapter.get().getId();
            boolean hasActiveJob = checkForActiveJob(chapterId);
            return ChapterValidationResult.existingChapter(chapterId, contentHash, hasActiveJob);
        }

        // Create new chapter
        UUID newChapterId = createNewChapter(bookId, chapterNumber, chapterTitle, chapterText, contentHash);
        return ChapterValidationResult.newChapter(newChapterId, contentHash);
    }

    /**
     * Check if a chapter has an active processing job
     */
    private boolean checkForActiveJob(UUID chapterId) {
        return bestEffortLookup(
            "hasActiveJobForChapter chapterId=" + chapterId,
            () -> jobRepo.existsActiveForChapter(chapterId),
            false
        );
    }

    /**
     * Find the most recent job for a chapter
     */
    private Optional<UUID> findMostRecentJobId(UUID chapterId) {
        return bestEffortLookup(
            "findMostRecentJobForChapter chapterId=" + chapterId,
            () -> jobRepo.findFirstByChapterIdOrderByCreatedAtDesc(chapterId)
                .map(IngestionJob::getId),
            Optional.empty()
        );
    }

    private Optional<Chapter> findExistingChapterByHash(String contentHash) {
        return bestEffortLookup(
            "findChapterByContentHash hash=" + contentHash,
            () -> chapterRepo.findByContentHash(contentHash),
            Optional.empty()
        );
    }

    private UUID createNewChapter(UUID bookId, Integer chapterNumber, String chapterTitle, String chapterText, String contentHash) {
        try {
            Chapter chapter = buildChapter(bookId, chapterNumber, chapterTitle, chapterText, contentHash);
            if (chapter.getId() == null) {
                chapter.setId(UUID.randomUUID());
            }
            if (chapter.getBook() == null && chapter.getBookId() != null) {
                bookRepo.findById(chapter.getBookId()).ifPresent(chapter::setBook);
            }
            Chapter persisted = chapterRepo.save(chapter);
            
            // Handle mock scenarios where createChapter might return null
            UUID chapterId = (persisted != null) ? persisted.getId() : chapter.getId();
            
            log.debug("Created new chapter with ID: {}", chapterId);
            return chapterId;
            
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create chapter in graph: " + e.getMessage(), e);
        }
    }

    private Chapter buildChapter(UUID bookId, Integer chapterNumber, String chapterTitle, String chapterText, String contentHash) {
        Book book = bookRepo.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        PublicationCoordinates coords = new PublicationCoordinates();
        coords.setUniverse(book.getUniverse());
        coords.setSeries(book.getSeries());
        coords.setBookTitle(book.getTitle());
        coords.setChapterTitle(chapterTitle);
        coords.setBookNumber(book.getBookNumber() != null ? book.getBookNumber() : 0);
        coords.setChapterNumber(chapterNumber);

        return Chapter.createWithReferences(
                book.getId(), book.getUniverseId(), book.getSeriesId(),
                coords, chapterTitle, chapterText, contentHash);
    }
}
