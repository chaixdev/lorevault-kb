package com.lorevault.api.ingestion.submission;

import com.lorevault.api.ingestion.job.JobStatusDetails;
import com.lorevault.api.ingestion.job.PaginatedJobSummaries;
import com.lorevault.api.ingestion.job.IngestionFailure;
import com.lorevault.api.ingestion.job.ChapterIngestionJob;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.library.book.Book;
import com.lorevault.api.library.book.PublicationCoordinates;
import com.lorevault.api.library.book.BookGraphRepository;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static com.lorevault.api.ingestion.infrastructure.HashUtils.generateSha256Hash;

/**
 * Service for chapter submission and job management.
 * <p>
 * Responsibilities:
 * - Chapter validation and duplicate detection
 * - Ingestion job creation and query
 * - Publishing events to trigger async processing pipeline
 * <p>
 * Processing is handled by event-driven handlers:
 * SceneDetectionHandler → ChunkingHandler → EmbeddingHandler
 * <p>
 * Best-effort lookups (content-hash, active-job, recent-job) are delegated to
 * {@link IngestionIsolatedLookupService}, which runs each query in its own
 * REQUIRES_NEW read-only transaction.  This prevents a Neo4j session failure
 * during a lookup from poisoning the caller's submit transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final ChapterGraphRepository chapterRepo;
    private final BookGraphRepository bookRepo;
    private final IngestionJobService ingestionJobService;
    private final ApplicationEventPublisher eventPublisher;
    private final IngestionIsolatedLookupService isolatedLookup;

    /**
     * Context object for chapter validation results
     */
    public static class ChapterValidationResult {
        @Getter private final boolean isExistingChapter;
        @Getter private final UUID chapterId;
        @Getter private final String contentHash;
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
                Optional<UUID> activeJobId = isolatedLookup.findMostRecentJobId(chapterId);
                if (activeJobId.isPresent()) {
                    return new IngestionSubmissionResult(activeJobId.get(), chapterId);
                }
                throw buildSubmissionLookupFailure(
                        "CHAPTER_ACTIVE_JOB_ID_MISSING",
                        "Active chapter ingestion job could not be resolved for chapter: " + chapterId,
                        chapterId,
                        builder -> builder.detail("lookupType", "recentJob")
                );
            }

            // Create new job for existing chapter
            ChapterIngestionJob job = ingestionJobService.createIngestionJob(chapterId);
            return new IngestionSubmissionResult(job.getId(), chapterId);
        }

        // Create job for new chapter
        ChapterIngestionJob job = ingestionJobService.createIngestionJob(chapterId);
        return new IngestionSubmissionResult(job.getId(), chapterId);
    }

    /**
     * Prepare a chapter for step-by-step pipeline execution.
     *
     * <p>Creates the chapter (if new) and an ingestion job, but does <em>not</em>
     * publish {@link ChapterIngestionEvent}. The caller (CLI) is responsible for
     * invoking individual pipeline steps via their Operation interfaces.
     *
     * @return the job and chapter IDs
     */
    @Transactional
    public IngestionSubmissionResult prepareChapter(UUID bookId, Integer chapterNumber, String chapterTitle, String chapterText) {
        log.info("Preparing chapter for step-by-step ingestion: bookId={}, chapterNumber={}, title={}",
            bookId, chapterNumber, chapterTitle);

        ChapterValidationResult validationResult = validateAndProcessChapter(bookId, chapterNumber, chapterTitle, chapterText);
        UUID chapterId = validationResult.getChapterId();

        // Create job but do NOT bootstrap stages — CLI will drive steps manually
        ChapterIngestionJob job = ingestionJobService.createIngestionJob(chapterId);
        log.info("Prepared chapter {} for step-by-step processing, jobId={}", chapterId, job.getId());

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
     * Validate chapter submission and handle duplicate detection.
     * Lookup queries are delegated to {@link IngestionIsolatedLookupService}
     * so that they run in isolated read-only transactions.
     */
    private ChapterValidationResult validateAndProcessChapter(UUID bookId, Integer chapterNumber, String chapterTitle, String chapterText) {
        log.info("Validating chapter submission: bookId={}, chapterNumber={}, title={}",
            bookId, chapterNumber, chapterTitle);

        String contentHash = generateSha256Hash(chapterText);

        // Check for existing chapter with same content — isolated REQUIRES_NEW read-only tx
        Optional<Chapter> existingChapter = isolatedLookup.findChapterByContentHash(contentHash);
        if (existingChapter.isPresent()) {
            UUID chapterId = existingChapter.get().getId();
            // Isolated REQUIRES_NEW read-only tx
            boolean hasActiveJob = isolatedLookup.existsActiveForChapter(chapterId);
            return ChapterValidationResult.existingChapter(chapterId, contentHash, hasActiveJob);
        }

        // Create new chapter
        UUID newChapterId = createNewChapter(bookId, chapterNumber, chapterTitle, chapterText, contentHash);
        return ChapterValidationResult.newChapter(newChapterId, contentHash);
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

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            IngestionFailure failure = IngestionFailure.builder(
                            "CHAPTER_PERSISTENCE_FAILED",
                            "Failed to create chapter in graph: " + safeMessage(e)
                    )
                    .exceptionType(e.getClass().getSimpleName())
                    .stage("CHAPTER_SUBMISSION")
                    .detail("bookId", bookId)
                    .detail("chapterNumber", chapterNumber)
                    .detail("chapterTitle", chapterTitle)
                    .build();
            throw new ChapterPersistenceException(failure, e);
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message != null ? message : exception.getClass().getSimpleName();
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

    private ChapterSubmissionLookupException buildSubmissionLookupFailure(String code,
                                                                         String message,
                                                                         UUID chapterId,
                                                                         ThrowableDetailsAppender detailsAppender) {
        IngestionFailure.Builder failureBuilder = IngestionFailure.builder(code, message)
                .stage("CHAPTER_SUBMISSION");
        if (chapterId != null) {
            failureBuilder.detail("chapterId", chapterId);
        }
        if (detailsAppender != null) {
            detailsAppender.append(failureBuilder);
        }
        return new ChapterSubmissionLookupException(failureBuilder.build());
    }

    @FunctionalInterface
    private interface ThrowableDetailsAppender {
        void append(IngestionFailure.Builder builder);
    }
}
