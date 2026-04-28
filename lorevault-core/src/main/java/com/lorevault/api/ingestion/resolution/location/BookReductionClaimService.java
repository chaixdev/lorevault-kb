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
import java.util.UUID;

/**
 * Manages persisted work-claims for book reduction operations.
 *
 * <p>Each book reduction (location or individual) must acquire a claim via
 * {@link #tryAcquireClaim(UUID)} before running.  The claim is a
 * {@link BookReductionClaim} node in Neo4j whose
 * uniqueness is enforced by the {@code bookId} property — meaning only one worker
 * can hold the claim at a time, across all JVM instances.
 *
 * <p>Workers must call {@link #releaseClaim(UUID)} in a {@code finally} block to ensure
 * the claim is cleared even on failure.  Stale claims (from crashed workers) are
 * released by {@link #releaseStaleClaimsOlderThan(Duration)}, which is called on
 * application startup and can be scheduled periodically.
 */
@Service
@Slf4j
public class BookReductionClaimService {

    private static final String WORKER_PREFIX = resolveHostname();
    private static final Duration STALE_CLAIM_MAX_AGE = Duration.ofMinutes(30);

    private final BookReductionClaimRepository claimRepository;
    private final TransactionTemplate requiredTransactionTemplate;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public BookReductionClaimService(
            BookReductionClaimRepository claimRepository,
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
     * {@link #tryAcquireClaim(UUID)} when callers must not silently skip work
     * due to momentary contention (e.g. two chapters of the same book completing
     * at the same time).
     *
     * @param bookId       the book to claim
     * @param maxAttempts  maximum number of tries (including the first attempt)
     * @param retryDelayMs milliseconds to sleep between retries
     * @return {@code true} if the claim was acquired within the attempt budget
     */
    public boolean tryAcquireClaimWithRetry(UUID bookId, int maxAttempts, long retryDelayMs) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (tryAcquireClaim(bookId)) {
                return true;
            }
            if (attempt < maxAttempts) {
                log.debug("[BookClaim] Claim contention bookId={}, attempt {}/{}, retrying in {}ms",
                        bookId, attempt, maxAttempts, retryDelayMs);
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[BookClaim] Interrupted while waiting for claim bookId={}", bookId);
                    return false;
                }
            }
        }
        log.warn("[BookClaim] Could not acquire claim bookId={} after {} attempts", bookId, maxAttempts);
        return false;
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
    public boolean tryAcquireClaim(UUID bookId) {
        return Boolean.TRUE.equals(requiresNewTransactionTemplate.execute(status -> {
            String workerId = workerId();
            boolean acquired = claimRepository.tryAcquireClaim(
                    bookId.toString(),
                    LocalDateTime.now(),
                    workerId
            );
            if (acquired) {
                log.debug("[BookClaim] Acquired claim bookId={} worker={}", bookId, workerId);
            } else {
                log.debug("[BookClaim] Claim already held bookId={}", bookId);
            }
            return acquired;
        }));
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
    public void releaseClaim(UUID bookId) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> {
            String workerId = workerId();
            claimRepository.releaseClaim(bookId.toString(), workerId);
            log.debug("[BookClaim] Released claim bookId={} worker={}", bookId, workerId);
        });
    }

    /**
     * Force-release all claims older than {@code maxAge}.
     * Call on startup and optionally on a scheduled basis to recover from worker crashes.
     *
     * @param maxAge maximum acceptable claim age; claims older than this are deleted
     */
    public void releaseStaleClaimsOlderThan(Duration maxAge) {
        requiredTransactionTemplate.executeWithoutResult(status -> {
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

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }
}
