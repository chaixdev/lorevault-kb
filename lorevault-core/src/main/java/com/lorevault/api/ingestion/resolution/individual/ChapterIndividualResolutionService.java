package com.lorevault.api.ingestion.resolution.individual;

import com.lorevault.api.content.association.ChapterIndividual;
import com.lorevault.api.content.association.ChapterIndividualGraphRepository;
import com.lorevault.api.content.mention.IndividualMention;
import com.lorevault.api.content.mention.IndividualMentionGraphRepository;
import com.lorevault.api.ingestion.resolution.consolidation.ChapterEntityGuardService;
import com.lorevault.api.ingestion.resolution.consolidation.ConsolidationEngine;
import com.lorevault.api.ingestion.resolution.consolidation.NameKeys;
import com.lorevault.api.ingestion.resolution.consolidation.PickFirstNonBlank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterIndividualResolutionService {

    public static final String CHAPTER_RESOLVED = "chapter-resolved";

    private final ChapterIndividualGraphRepository chapterIndividualRepository;
    private final ChapterEntityGuardService chapterEntityGuardService;
    private final IndividualMentionGraphRepository individualMentionRepository;

    public ChapterIndividualResolutionService(
            ChapterIndividualGraphRepository chapterIndividualRepository,
            ChapterEntityGuardService chapterEntityGuardService,
            IndividualMentionGraphRepository individualMentionRepository
    ) {
        this.chapterIndividualRepository = chapterIndividualRepository;
        this.chapterEntityGuardService = chapterEntityGuardService;
        this.individualMentionRepository = individualMentionRepository;
    }

    @Transactional(readOnly = true)
    public boolean chapterExists(UUID chapterId) {
        return chapterEntityGuardService.chapterExists(chapterId);
    }

    @Transactional
    public ChapterIndividualResolutionResult resolveChapter(UUID chapterId) {
        if (chapterId == null) {
            return new ChapterIndividualResolutionResult(null, false, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterIndividualRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            return new ChapterIndividualResolutionResult(
                    chapterId,
                    false,
                    0,
                    0,
                    "No individual mentions found for chapter"
            );
        }

        chapterIndividualRepository.deleteByChapterId(chapterId);

        List<IndividualMention> mentions = individualMentionRepository.findByChapterId(chapterId).stream()
                .sorted(Comparator
                        .comparing(IndividualMention::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(IndividualMention::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(IndividualMention::extractionIndex, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        List<List<IndividualMention>> clusters =
                ConsolidationEngine.cluster(mentions, mention -> NameKeys.from(mention.normalizedName(), mention.aliases()));

        if (clusters.isEmpty()) {
            return new ChapterIndividualResolutionResult(
                    chapterId,
                    false,
                    Math.toIntExact(mentionCount),
                    0,
                    "No resolvable individual mentions found for chapter"
            );
        }

        List<ChapterIndividual> chapterIndividuals = new ArrayList<>();
        List<List<UUID>> mentionIdsByIndividual = new ArrayList<>();

        for (List<IndividualMention> cluster : clusters) {
            IndividualMention representative = cluster.get(0);
            List<UUID> mentionIds = cluster.stream().map(IndividualMention::id).toList();
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            for (IndividualMention mention : cluster) {
                if (mention.aliases() != null) {
                    aliases.addAll(mention.aliases().stream().filter(a -> a != null && !a.isBlank()).toList());
                }
            }
            chapterIndividuals.add(new ChapterIndividual(
                    UUID.randomUUID(),
                    chapterId,
                    representative.displayName(),
                    representative.normalizedName(),
                    List.copyOf(aliases),
                    mentionIds.size(),
                    null,
                    null
            ));
            mentionIdsByIndividual.add(mentionIds);
        }

        List<ChapterIndividual> savedIndividuals = new ArrayList<>();
        chapterIndividualRepository.saveAll(chapterIndividuals).forEach(savedIndividuals::add);

        for (int i = 0; i < savedIndividuals.size(); i++) {
            ChapterIndividual chapterIndividual = savedIndividuals.get(i);
            chapterIndividualRepository.linkChapterToIndividual(chapterId, chapterIndividual.id());
            chapterIndividualRepository.linkMentionsToChapterIndividual(
                    mentionIdsByIndividual.get(i),
                    chapterIndividual.id(),
                    CHAPTER_RESOLVED
            );
        }

        return new ChapterIndividualResolutionResult(
                chapterId,
                true,
                Math.toIntExact(mentionCount),
                Math.toIntExact(chapterIndividualRepository.countChapterIndividualsByChapterId(chapterId)),
                "Resolved chapter individual mentions"
        );
    }
}
