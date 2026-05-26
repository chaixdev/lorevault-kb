package com.lorevault.api.ingestion.submission;

import com.lorevault.api.ingestion.job.JobStatusDetails;
import com.lorevault.api.ingestion.job.PaginatedJobSummaries;
import com.lorevault.api.ingestion.job.IngestionFailure;
import com.lorevault.api.ingestion.job.ChapterIngestionJob;
import com.lorevault.api.ingestion.job.ChapterIngestionJobGraphRepository;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.ingestion.job.IngestionJobService;
import com.lorevault.api.library.book.Book;
import com.lorevault.api.library.book.PublicationCoordinates;
import com.lorevault.api.library.book.BookGraphRepository;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.lorevault.api.common.error.ExceptionSanitizer.safeMessage;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final ChapterGraphRepository chapterRepo;
    private final BookGraphRepository bookRepo;
    private final IngestionJobService ingestionJobService;
    private final ApplicationEventPublisher eventPublisher;
    private final ChapterIngestionJobGraphRepository jobRepo;

    /**
     * Context object for chapter validation results.
     */
    public record ChapterValidationResult(
        boolean isExistingChapter,
        UUID chapterId,
        String contentHash,
        boolean hasActiveJob
    ) {
        public static ChapterValidationResult existingChapter(UUID chapterId, String contentHash, boolean hasActiveJob) {
            return new ChapterValidationResult(true, chapterId, contentHash, hasActiveJob);
        }

        public static ChapterValidationResult newChapter(UUID chapterId, String contentHash) {
            return new ChapterValidationResult(false, chapterId, contentHash, false);
        }
    }

    /**
     * Submit a chapter for processing with integrated validation and duplicate detection.
     * Publishes ChapterIngestionEvent which triggers the async processing pipeline.
     */
    @Transactional
    public IngestionSubmissionResult submitChapter(UUID bookId, Integer chapterNumber, String chapterTitle, String chapterText) {
        log.info("Processing chapter submission: bookId={}, chapterNumber={}, title={}",
            bookId, chapterNumber, chapterTitle);
        return doSubmitChapter(bookId, chapterNumber, chapterTitle, chapterText);
    }

    /**
     * Prepare a chapter for step-by-step pipeline execution.
     *
     * <p>Creates the chapter (if new) and an ingestion job, but does <em>not</em>
     * publish {@link ChapterIngestionEvent}. The caller (step-by-step execution controller) is responsible for
     * invoking individual pipeline steps via their Operation interfaces.
     *
     * @return the job and chapter IDs
     */
    @Transactional
    public IngestionSubmissionResult prepareChapter(UUID bookId, Integer chapterNumber, String chapterTitle, String chapterText) {
        log.info("Preparing chapter for step-by-step ingestion: bookId={}, chapterNumber={}, title={}",
            bookId, chapterNumber, chapterTitle);
        IngestionSubmissionResult result = doSubmitChapter(bookId, chapterNumber, chapterTitle, chapterText);
        log.info("Prepared chapter {} for step-by-step processing, jobId={}", result.chapterId(), result.jobId());
        return result;
    }

    /**
     * Shared submission logic with duplicate detection.
     *
     * @return the submission result with job and chapter IDs
     */
    private IngestionSubmissionResult doSubmitChapter(UUID bookId, Integer chapterNumber, String chapterTitle, String chapterText) {
        ChapterValidationResult result = validateAndProcessChapter(bookId, chapterNumber, chapterTitle, chapterText);
        UUID chapterId = result.chapterId();

        // Handle existing chapter with active job — return existing job ID (dedup)
        if (result.isExistingChapter() && result.hasActiveJob()) {
            Optional<UUID> activeJobId = isolatedLookup(
                () -> jobRepo.findFirstByChapterIdOrderByCreatedAtDesc(chapterId)
                    .map(ChapterIngestionJob::getId),
                "CHAPTER_RECENT_JOB_LOOKUP_FAILED",
                "Chapter submission lookup failed during findMostRecentJobForChapter",
                b -> { b.detail("chapterId", chapterId); b.detail("lookupType", "recentJob"); }
            );
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

        // Create new job
        ChapterIngestionJob job = ingestionJobService.createIngestionJob(chapterId);
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
     */
    private ChapterValidationResult validateAndProcessChapter(UUID bookId, Integer chapterNumber, String chapterTitle, String chapterText) {
        log.info("Validating chapter submission: bookId={}, chapterNumber={}, title={}",
            bookId, chapterNumber, chapterTitle);

        String contentHash = generateSha256Hash(chapterText);

        // Check for existing chapter with same content
        Optional<Chapter> existingChapter = isolatedLookup(
            () -> chapterRepo.findByContentHash(contentHash),
            "CHAPTER_HASH_LOOKUP_FAILED",
            "Chapter submission lookup failed during findChapterByContentHash",
            b -> { b.detail("lookupType", "contentHash"); b.detail("contentHash", contentHash); }
        );
        if (existingChapter.isPresent()) {
            UUID chapterId = existingChapter.get().getId();
            // Isolated lookup for active job check
            boolean hasActiveJob = isolatedLookup(
                () -> jobRepo.existsActiveForChapter(chapterId),
                "CHAPTER_ACTIVE_JOB_LOOKUP_FAILED",
                "Chapter submission lookup failed during existsActiveForChapter",
                b -> { b.detail("chapterId", chapterId); b.detail("lookupType", "activeJob"); }
            );
            return ChapterValidationResult.existingChapter(chapterId, contentHash, hasActiveJob);
        }

        // Create new chapter
        UUID newChapterId = createNewChapter(bookId, chapterNumber, chapterTitle, chapterText, contentHash);
        return ChapterValidationResult.newChapter(newChapterId, contentHash);
    }

    private UUID createNewChapter(UUID bookId, Integer chapterNumber, String chapterTitle, String chapterText, String contentHash) {
        try {
            Chapter chapter = buildChapter(bookId, chapterNumber, chapterTitle, chapterText, contentHash);
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

    private <T> T isolatedLookup(Supplier<T> query, String code, String message,
                                  Consumer<IngestionFailure.Builder> details) {
        try {
            return query.get();
        } catch (Exception e) {
            log.warn("Required lookup failed: {}", e.getMessage());
            log.debug("Required lookup failure details:", e);
            var builder = IngestionFailure.builder(code, message + ": " + safeMessage(e))
                    .exceptionType(e.getClass().getSimpleName())
                    .stage("CHAPTER_SUBMISSION");
            if (details != null) {
                details.accept(builder);
            }
            throw new ChapterSubmissionLookupException(builder.build(), e);
        }
    }

    @FunctionalInterface
    private interface ThrowableDetailsAppender {
        void append(IngestionFailure.Builder builder);
    }
}
