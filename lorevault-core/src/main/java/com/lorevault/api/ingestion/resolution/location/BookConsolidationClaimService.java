package com.lorevault.api.ingestion.resolution.location;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Manages persisted work-claims for book reduction operations.
 *
 * <p>Each book reduction lane must acquire a claim via
 * {@link #tryAcquireClaim(UUID, String)} before running.  The claim is a
 * {@link BookConsolidationClaim} node in Neo4j whose key includes book ID and lane —
 * meaning only one worker can hold the same lane claim at a time across all JVM
 * instances, while different lanes for the same book can run concurrently.
 *
 * <p>Workers must call {@link #releaseClaim(UUID, String)} in a {@code finally} block to ensure
 * the claim is cleared even on failure.  Stale claims (from crashed workers) are
 * released by {@link #releaseStaleClaimsOlderThan(Duration)}, which is called on
 * application startup and can be scheduled periodically.
 */
@Service
@Slf4j
public class BookConsolidationClaimService {

    private static final String WORKER_PREFIX = resolveHostname();
    private static final Duration STALE_CLAIM_MAX_AGE = Duration.ofMinutes(30);

    private final BookConsolidationClaimRepository claimRepository;
    private final TransactionTemplate requiredTransactionTemplate;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final Map<String, String> heldClaimTokens = new ConcurrentHashMap<>();

    public BookConsolidationClaimService(
            BookConsolidationClaimRepository claimRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.claimRepository = claimRepository;
        this.requiredTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * On startup, release any stale claims left by a previous crashed process.
     * Runs in an explicit transaction without requiring a self-proxy, which would
     * introduce a bean cycle during context startup.
     */
    @PostConstruct
    public void releaseStaleClaimsOnStartup() {
        releaseStaleClaimsOlderThan(STALE_CLAIM_MAX_AGE);
    }

    /**
     * Attempt to acquire a claim, retrying up to {@code maxAttempts} times with
     * {@code retryDelayMs} milliseconds between attempts.  Use this instead of
     * {@link #tryAcquireClaim(UUID, String)} when callers must not silently skip work
     * due to momentary contention (e.g. two chapters of the same book completing
     * at the same time).
     *
     * @param bookId       the book to claim
     * @param maxAttempts  maximum number of tries (including the first attempt)
     * @param retryDelayMs milliseconds to sleep between retries
     * @return {@code true} if the claim was acquired within the attempt budget
     */
    public boolean tryAcquireClaimWithRetry(UUID bookId, String lane, int maxAttempts, long retryDelayMs) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (tryAcquireClaim(bookId, lane)) {
                return true;
            }
            if (attempt < maxAttempts) {
                log.debug("[BookClaim] Claim contention bookId={}, lane={}, attempt {}/{}, retrying in {}ms",
                        bookId, lane, attempt, maxAttempts, retryDelayMs);
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[BookClaim] Interrupted while waiting for claim bookId={}, lane={}", bookId, lane);
                    return false;
                }
            }
        }
        log.warn("[BookClaim] Could not acquire claim bookId={}, lane={} after {} attempts", bookId, lane, maxAttempts);
        return false;
    }

    public boolean tryAcquireClaimWithRetry(UUID bookId, int maxAttempts, long retryDelayMs) {
        return tryAcquireClaimWithRetry(bookId, "BOOK_REDUCTION", maxAttempts, retryDelayMs);
    }

    /**
     * Attempt to acquire a persisted claim for the given book.
     *
     * <p>Uses {@code REQUIRES_NEW} so the MERGE commits immediately and is visible
     * to concurrent callers before this method returns.
     *
     * @param bookId the book to claim
     * @return {@code true} if this call acquired the claim; {@code false} if another
     *         worker already holds it
     */
    public boolean tryAcquireClaim(UUID bookId, String lane) {
        return Boolean.TRUE.equals(requiresNewTransactionTemplate.execute(status -> {
            String workerId = workerId();
            String acquiredToken = UUID.randomUUID().toString();
            boolean acquired = claimRepository.tryAcquireClaim(
                    claimId(bookId, lane),
                    bookId != null ? bookId.toString() : null,
                    lane,
                    LocalDateTime.now(),
                    workerId,
                    acquiredToken
            );
            if (acquired) {
                heldClaimTokens.put(claimId(bookId, lane), acquiredToken);
                log.debug("[BookClaim] Acquired claim bookId={}, lane={}, worker={}", bookId, lane, workerId);
            } else {
                log.debug("[BookClaim] Claim already held bookId={}, lane={}", bookId, lane);
            }
            return acquired;
        }));
    }

    public boolean tryAcquireClaim(UUID bookId) {
        return tryAcquireClaim(bookId, "BOOK_REDUCTION");
    }

    /**
     * Release the claim held by this worker for the given book.
     * Safe to call even if no claim exists.
     *
     * <p>Uses {@code REQUIRES_NEW} so the deletion commits even if the calling
     * transaction is being rolled back.
     *
     * @param bookId the book whose claim should be released
     */
    public void releaseClaim(UUID bookId, String lane) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> {
            String claimId = claimId(bookId, lane);
            Optional.ofNullable(heldClaimTokens.get(claimId)).ifPresentOrElse(
                    acquiredToken -> {
                        claimRepository.releaseClaim(claimId, acquiredToken);
                        heldClaimTokens.remove(claimId, acquiredToken);
                        log.debug("[BookClaim] Released claim bookId={}, lane={}, worker={}", bookId, lane, workerId());
                    },
                    () -> log.debug("[BookClaim] No held token for release bookId={}, lane={}", bookId, lane)
            );
        });
    }

    public void releaseClaim(UUID bookId) {
        releaseClaim(bookId, "BOOK_REDUCTION");
    }

    /**
     * Force-release all claims older than {@code maxAge}.
     * Call on startup and optionally on a scheduled basis to recover from worker crashes.
     *
     * @param maxAge maximum acceptable claim age; claims older than this are deleted
     */
    public void releaseStaleClaimsOlderThan(Duration maxAge) {
        requiredTransactionTemplate.executeWithoutResult(status -> {
            claimRepository.deleteClaimsWithMissingKeyOrTimestamp();
            LocalDateTime threshold = LocalDateTime.now().minus(maxAge);
            claimRepository.deleteStaleClaimsOlderThan(threshold);
            log.info("[BookClaim] Released stale claims older than {}", maxAge);
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String workerId() {
        return WORKER_PREFIX + ":" + Thread.currentThread().getName();
    }

    private String claimId(UUID bookId, String lane) {
        String safeLane = lane != null && !lane.isBlank() ? lane : "BOOK_REDUCTION";
        return bookId + ":" + safeLane;
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }
}
