package com.lorevault.api.ingestion.application;

import com.lorevault.api.content.entities.Chapter;
import com.lorevault.api.content.entities.ChapterGraphRepository;
import com.lorevault.api.ingestion.domain.IngestionFailure;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.ChapterSubmissionLookupException;
import com.lorevault.api.ingestion.infrastructure.IngestionJobGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Isolated read-only lookups for chapter submission validation.
 *
 * Each method runs in its own REQUIRES_NEW read-only transaction so that a Neo4j
 * failure during a lookup does not poison the caller's submit transaction.
 * Using a separate bean ensures the declarative proxy is honoured (avoids
 * self-invocation proxy bypass).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionIsolatedLookupService {

    private final ChapterGraphRepository chapterRepo;
    private final IngestionJobGraphRepository jobRepo;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Chapter> findChapterByContentHash(String contentHash) {
        try {
            return chapterRepo.findByContentHash(contentHash);
        } catch (Exception e) {
            log.warn("Required lookup failed (findChapterByContentHash): {}", e.getMessage());
            log.debug("Required lookup failure details (findChapterByContentHash):", e);
            throw new ChapterSubmissionLookupException(
                    IngestionFailure.builder(
                                    "CHAPTER_HASH_LOOKUP_FAILED",
                                    "Chapter submission lookup failed during findChapterByContentHash: " + safeMessage(e))
                            .exceptionType(e.getClass().getSimpleName())
                            .stage("CHAPTER_SUBMISSION")
                            .detail("lookupType", "contentHash")
                            .detail("contentHash", contentHash)
                            .build(),
                    e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean existsActiveForChapter(UUID chapterId) {
        try {
            return jobRepo.existsActiveForChapter(chapterId);
        } catch (Exception e) {
            log.warn("Required lookup failed (existsActiveForChapter chapterId={}): {}", chapterId, e.getMessage());
            log.debug("Required lookup failure details (existsActiveForChapter):", e);
            throw new ChapterSubmissionLookupException(
                    IngestionFailure.builder(
                                    "CHAPTER_ACTIVE_JOB_LOOKUP_FAILED",
                                    "Chapter submission lookup failed during hasActiveJobForChapter: " + safeMessage(e))
                            .exceptionType(e.getClass().getSimpleName())
                            .stage("CHAPTER_SUBMISSION")
                            .detail("chapterId", chapterId)
                            .detail("lookupType", "activeJob")
                            .build(),
                    e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<UUID> findMostRecentJobId(UUID chapterId) {
        try {
            return jobRepo.findFirstByChapterIdOrderByCreatedAtDesc(chapterId)
                    .map(IngestionJob::getId);
        } catch (Exception e) {
            log.warn("Required lookup failed (findMostRecentJobForChapter chapterId={}): {}", chapterId, e.getMessage());
            log.debug("Required lookup failure details (findMostRecentJobForChapter):", e);
            throw new ChapterSubmissionLookupException(
                    IngestionFailure.builder(
                                    "CHAPTER_RECENT_JOB_LOOKUP_FAILED",
                                    "Chapter submission lookup failed during findMostRecentJobForChapter: " + safeMessage(e))
                            .exceptionType(e.getClass().getSimpleName())
                            .stage("CHAPTER_SUBMISSION")
                            .detail("chapterId", chapterId)
                            .detail("lookupType", "recentJob")
                            .build(),
                    e);
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }
}
