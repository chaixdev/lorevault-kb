package com.lorevault.api.graph.event.consolidation.book;

import com.lorevault.api.ai.llm.EventMergeModels;
import com.lorevault.api.graph.event.persistence.ChapterEvent;
import com.lorevault.api.library.chapter.Chapter;
import com.lorevault.api.library.chapter.ChapterGraphRepository;
import static com.lorevault.api.common.ExceptionSanitizer.sanitize;

import com.lorevault.api.graph.event.consolidation.chapter.ChapterEventEmbeddingTransactionSupport;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.ForStage;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StageResult;
import com.lorevault.api.orchestration.consolidation.BookConsolidationClaimService;
import com.lorevault.api.orchestration.consolidation.BookConsolidationClaimUnavailableException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Book-level cross-chapter event candidate generation handler.
 *
 * <p>Runs AFTER all chapters in a book have been embedded ({@code CHAPTER_EVENT_EMBEDDING}
 * completed for all chapters).  Re-runs ANN candidate generation across ALL chapters
 * in the book to catch cross-chapter similarities that per-chapter scans missed
 * (because when a chapter was processed earlier, later chapters' events weren't yet
 * in the ANN index).
 *
 * <p>Follows the same claim/guard/logging pattern as {@code BookCollectiveConsolidationHandler}.
 */
@Component
@Slf4j
@ForStage(StageKey.BOOK_EVENT_CANDIDATE_GENERATION)
public class BookEventCandidateGenerationHandler implements StageOperation {

    private static final String CLAIM_LANE = "BOOK_EVENT_CANDIDATE_GENERATION";

    private final ChapterGraphRepository chapterRepo;
    private final ChapterEventEmbeddingTransactionSupport txSupport;
    private final BookEventAnnCandidateService annCandidateService;
    private final BookEventMergeVerificationService mergeVerificationService;
    private final BookEventConsolidationService bookEventConsolidationService;
    private final BookConsolidationClaimService bookConsolidationClaimService;

    public BookEventCandidateGenerationHandler(
            ChapterGraphRepository chapterRepo,
            ChapterEventEmbeddingTransactionSupport txSupport,
            BookEventAnnCandidateService annCandidateService,
            BookEventMergeVerificationService mergeVerificationService,
            BookEventConsolidationService bookEventConsolidationService,
            BookConsolidationClaimService bookConsolidationClaimService
    ) {
        this.chapterRepo = chapterRepo;
        this.txSupport = txSupport;
        this.annCandidateService = annCandidateService;
        this.mergeVerificationService = mergeVerificationService;
        this.bookEventConsolidationService = bookEventConsolidationService;
        this.bookConsolidationClaimService = bookConsolidationClaimService;
    }

    @Override
    public StageResult execute(StageExecutionContext ctx) {
        UUID jobId = ctx.jobId();
        UUID chapterId = ctx.chapterId();
        UUID bookId = ctx.bookId();

        // Book ID is required
        if (bookId == null) {
            log.warn("[LANE:EVENT] [EVENT_CANDIDATE_GENERATION] Skipped: jobId={}, bookId={}, reason={}",
                    jobId, null, "Book ID is required");
            return StageResult.success(StageKey.BOOK_EVENT_CANDIDATE_GENERATION, "Book ID is required", 0L);
        }

        long start = System.currentTimeMillis();

        log.info("[LANE:EVENT] [EVENT_CANDIDATE_GENERATION] Started: jobId={}, bookId={}", jobId, bookId);

        if (!bookConsolidationClaimService.tryAcquireClaim(bookId, CLAIM_LANE, ctx.stageId())) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[EVENT_CANDIDATE_GENERATION] Claim contention: jobId={}, bookId={}", jobId, bookId);
            return StageResult.retryableFailure(StageKey.BOOK_EVENT_CANDIDATE_GENERATION,
                    "Claim contention — another worker holds the reduction claim for this book", elapsed);
        }

        try {
            // 1. Load all chapters for the book
            List<Chapter> chapters = chapterRepo.findByBookId(bookId);

            if (chapters == null || chapters.isEmpty()) {
                long elapsed = System.currentTimeMillis() - start;
                log.warn("[LANE:EVENT] [EVENT_CANDIDATE_GENERATION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, "No chapters found for book");
                return StageResult.success(StageKey.BOOK_EVENT_CANDIDATE_GENERATION,
                        "No chapters found for book",
                        Map.of("totalCandidatePairs", 0,
                                "mergeDecisionCount", 0,
                                "bookEventsCreated", 0,
                                "referenceLinksWritten", 0),
                        elapsed);
            }

            // 2. Cross-chapter ANN candidate generation
            Map<String, BookEventCandidatePair> dedupedPairMap = new LinkedHashMap<>();
            Map<UUID, ChapterEvent> allEventsById = new HashMap<>();
            int totalEventCount = 0;

            for (Chapter ch : chapters) {
                List<ChapterEvent> events = txSupport.loadChapterEvents(ch.getId());
                if (events == null || events.isEmpty()) {
                    continue;
                }
                totalEventCount += events.size();

                // Build global events map (keep first occurrence)
                for (ChapterEvent event : events) {
                    if (event != null && event.id() != null) {
                        allEventsById.putIfAbsent(event.id(), event);
                    }
                }

                // Generate ANN candidates for this chapter
                List<BookEventCandidatePair> pairs = annCandidateService.generateCandidates(events, ch.getId());

                // Deduplicate across chapters keeping the highest ANN score
                for (BookEventCandidatePair pair : pairs) {
                    if (pair == null) continue;
                    String key = pairKey(pair);
                    BookEventCandidatePair existing = dedupedPairMap.get(key);
                    if (existing == null || pair.annScore() > existing.annScore()) {
                        dedupedPairMap.put(key, pair);
                    }
                }
            }

            List<BookEventCandidatePair> dedupedPairs = new ArrayList<>(dedupedPairMap.values());

            if (dedupedPairs.isEmpty() || allEventsById.isEmpty()) {
                long elapsed = System.currentTimeMillis() - start;
                log.warn("[LANE:EVENT] [EVENT_CANDIDATE_GENERATION] Skipped: jobId={}, bookId={}, reason={}",
                        jobId, bookId, "No candidate pairs generated");
                return StageResult.success(StageKey.BOOK_EVENT_CANDIDATE_GENERATION,
                        "No cross-chapter ANN candidate pairs generated",
                        Map.of("totalCandidatePairs", 0,
                                "mergeDecisionCount", 0,
                                "bookEventsCreated", 0,
                                "referenceLinksWritten", 0),
                        elapsed);
            }

            // 3. Semantic merge verification
            List<EventMergeModels.EventMergeVerification> verifications =
                    mergeVerificationService.verifyCandidates(
                            jobId, chapterId, dedupedPairs, allEventsById);

            // 4. Build merge decisions
            List<EventMergeModels.EventMergeDecision> mergeDecisions = verifications.stream()
                    .filter(v -> v != null && v.decision() == EventMergeModels.MergeDecision.MERGE)
                    .map(v -> new EventMergeModels.EventMergeDecision(
                            v.eventId1(), v.eventId2(), v.confidence()))
                    .toList();

            // 5. Run reduction and persist book events
            List<ChapterEvent> allEventList = new ArrayList<>(allEventsById.values());
            BookEventConsolidationService.BookEventConsolidationResult reductionResult =
                    bookEventConsolidationService.reduceAndPersist(
                            ctx, jobId, chapterId, bookId, allEventList, mergeDecisions);

            long elapsed = System.currentTimeMillis() - start;

            log.info(
                    "[LANE:EVENT] [EVENT_CANDIDATE_GENERATION] Completed: jobId={}, bookId={}, chapterCount={}, totalEventCount={}, candidatePairCount={}, mergeDecisionCount={}, bookEventsCreated={}, referenceLinksWritten={}",
                    jobId, bookId, chapters.size(), totalEventCount,
                    dedupedPairs.size(), mergeDecisions.size(),
                    reductionResult.bookEventsCreated(),
                    reductionResult.referenceLinksWritten());

            return StageResult.success(StageKey.BOOK_EVENT_CANDIDATE_GENERATION,
                    String.format("Generated %d cross-chapter candidate pairs, %d merge decisions, %d book events created",
                            dedupedPairs.size(), mergeDecisions.size(),
                            reductionResult.bookEventsCreated()),
                    Map.of(
                            "totalCandidatePairs", dedupedPairs.size(),
                            "mergeDecisionCount", mergeDecisions.size(),
                            "bookEventsCreated", reductionResult.bookEventsCreated(),
                            "referenceLinksWritten", reductionResult.referenceLinksWritten()
                    ),
                    elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[EVENT_CANDIDATE_GENERATION] Failed for job={} bookId={}: {}", jobId, bookId, e.getMessage(), e);
            boolean retryable = isRetryableError(e);
            return retryable
                    ? StageResult.retryableFailure(StageKey.BOOK_EVENT_CANDIDATE_GENERATION,
                            sanitize(e), elapsed)
                    : StageResult.failure(StageKey.BOOK_EVENT_CANDIDATE_GENERATION,
                            sanitize(e), elapsed);
        } finally {
            bookConsolidationClaimService.releaseClaim(bookId, CLAIM_LANE);
        }
    }

    /**
     * Build a canonical, unordered pair key where the two IDs are always ordered
     * lexicographically — matching the convention in {@link BookEventCandidatePair#of}.
     */
    private static String pairKey(BookEventCandidatePair pair) {
        return pair.eventId1() + ":" + pair.eventId2();
    }

    /**
     * Determine whether an exception represents a transient error suitable for retry.
     *
     * <p>Copied from {@code BookCollectiveConsolidationHandler}.
     */
    private boolean isRetryableError(Exception e) {
        if (e instanceof org.springframework.web.client.ResourceAccessException) {
            return true;
        }
        if (e instanceof org.springframework.web.client.HttpClientErrorException.TooManyRequests) {
            return true;
        }
        if (e instanceof org.springframework.web.client.HttpServerErrorException) {
            return true;
        }
        if (e instanceof BookConsolidationClaimUnavailableException) {
            return true;
        }
        String message = e.getMessage();
        return message != null && (message.contains("API") || message.contains("timeout")
                || message.contains("rate limit") || message.contains("connection"));
    }
}
