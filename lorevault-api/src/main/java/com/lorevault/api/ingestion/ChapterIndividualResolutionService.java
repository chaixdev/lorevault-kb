package com.lorevault.api.ingestion;

import com.lorevault.api.content.ChapterIndividual;
import com.lorevault.api.content.ChapterIndividualGraphRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterIndividualResolutionService {

    static final String CHAPTER_RESOLVED = "chapter-resolved";

    private final ChapterIndividualGraphRepository chapterIndividualRepository;

    public ChapterIndividualResolutionService(ChapterIndividualGraphRepository chapterIndividualRepository) {
        this.chapterIndividualRepository = chapterIndividualRepository;
    }

    @Transactional
    public void resolveChapter(UUID chapterId) {
        if (chapterId == null) {
            return;
        }

        chapterIndividualRepository.deleteByChapterId(chapterId);

        List<ChapterIndividualGraphRepository.ChapterIndividualCandidateView> candidates =
                chapterIndividualRepository.findResolutionCandidates(chapterId);
        if (candidates.isEmpty()) {
            return;
        }

        List<ChapterIndividual> chapterIndividuals = new ArrayList<>();
        for (ChapterIndividualGraphRepository.ChapterIndividualCandidateView candidate : candidates) {
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
            return;
        }

        List<ChapterIndividual> savedIndividuals = new ArrayList<>();
        chapterIndividualRepository.saveAll(chapterIndividuals).forEach(savedIndividuals::add);

        for (ChapterIndividual chapterIndividual : savedIndividuals) {
            chapterIndividualRepository.linkChapterToIndividual(chapterId, chapterIndividual.id());
            chapterIndividualRepository.linkMentionsToChapterIndividual(
                    chapterId,
                    chapterIndividual.normalizedName(),
                    chapterIndividual.id(),
                    CHAPTER_RESOLVED
            );
        }
    }

    private int safeMentionCount(Long mentionCount) {
        if (mentionCount == null) {
            return 0;
        }
        return Math.toIntExact(mentionCount);
    }
}
