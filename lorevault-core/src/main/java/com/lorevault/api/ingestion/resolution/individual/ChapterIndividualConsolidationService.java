package com.lorevault.api.ingestion.resolution.individual;

import com.lorevault.api.content.association.ChapterIndividual;
import com.lorevault.api.content.association.ChapterIndividualCandidate;
import com.lorevault.api.content.association.ChapterIndividualGraphRepository;
import com.lorevault.api.content.chapter.ChapterGraphRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChapterIndividualConsolidationService {

    public static final String CHAPTER_CONSOLIDATED = "chapter-consolidated";

    private final ChapterIndividualGraphRepository chapterIndividualRepository;
    private final ChapterGraphRepository chapterGraphRepository;

    @Transactional(readOnly = true)
    public boolean chapterExists(UUID chapterId) {
        return chapterId != null && chapterGraphRepository.findById(chapterId).isPresent();
    }

    @Transactional
    public ChapterIndividualConsolidationResult consolidateChapter(UUID chapterId) {
        if (chapterId == null) {
            return new ChapterIndividualConsolidationResult(null, false, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterIndividualRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            return new ChapterIndividualConsolidationResult(
                    chapterId,
                    false,
                    0,
                    0,
                    "No individual mentions found for chapter"
            );
        }

        chapterIndividualRepository.deleteByChapterId(chapterId);

        List<ChapterIndividualCandidate> candidates =
                chapterIndividualRepository.findResolutionCandidates(chapterId);
        if (candidates.isEmpty()) {
            return new ChapterIndividualConsolidationResult(
                    chapterId,
                    false,
                    Math.toIntExact(mentionCount),
                    0,
                    "No resolvable mentions found for chapter"
            );
        }

        List<ChapterIndividual> chapterIndividuals = new ArrayList<>();
        for (ChapterIndividualCandidate candidate : candidates) {
            if (candidate.getNormalizedName() == null || candidate.getNormalizedName().isBlank()) {
                continue;
            }
            chapterIndividuals.add(new ChapterIndividual(
                    UUID.randomUUID(),
                    chapterId,
                    candidate.getDisplayName(),
                    candidate.getNormalizedName(),
                    safeMentionCount(candidate.getMentionCount()),
                    null,
                    null
            ));
        }

        if (chapterIndividuals.isEmpty()) {
            return new ChapterIndividualConsolidationResult(
                    chapterId,
                    false,
                    Math.toIntExact(mentionCount),
                    0,
                    "No resolvable mentions found for chapter"
            );
        }

        List<ChapterIndividual> savedIndividuals = new ArrayList<>();
        chapterIndividualRepository.saveAll(chapterIndividuals).forEach(savedIndividuals::add);

        for (ChapterIndividual chapterIndividual : savedIndividuals) {
            chapterIndividualRepository.linkChapterToIndividual(chapterId, chapterIndividual.id());
            chapterIndividualRepository.linkMentionsToChapterIndividual(
                    chapterId,
                    chapterIndividual.normalizedName(),
                    chapterIndividual.id(),
                    CHAPTER_CONSOLIDATED
            );
        }

        return new ChapterIndividualConsolidationResult(
                chapterId,
                true,
                Math.toIntExact(mentionCount),
                Math.toIntExact(chapterIndividualRepository.countChapterIndividualsByChapterId(chapterId)),
                "Resolved chapter individual mentions"
        );
    }

    private int safeMentionCount(Long mentionCount) {
        if (mentionCount == null) {
            return 0;
        }
        return Math.toIntExact(mentionCount);
    }
}
