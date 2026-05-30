package com.lorevault.api.graph.individual.consolidation.chapter;

import com.lorevault.api.graph.individual.persistence.ChapterIndividual;
import com.lorevault.api.graph.individual.persistence.ChapterIndividualGraphRepository;
import com.lorevault.api.library.chapter.ChapterGraphRepository;
import com.lorevault.api.graph.individual.persistence.IndividualMention;
import com.lorevault.api.graph.individual.persistence.IndividualMentionGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.consolidation.ConsolidationEngine;
import com.lorevault.api.orchestration.consolidation.NameKeys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterIndividualConsolidationService {

    public static final String CHAPTER_CONSOLIDATED = "chapter-consolidated";

    private final ChapterIndividualGraphRepository chapterIndividualRepository;
    private final IndividualMentionGraphRepository individualMentionRepository;
    private final ConsolidationEngine consolidationEngine;
    private final ChapterGraphRepository chapterGraphRepository;

    public ChapterIndividualConsolidationService(
            ChapterIndividualGraphRepository chapterIndividualRepository,
            IndividualMentionGraphRepository individualMentionRepository,
            ConsolidationEngine consolidationEngine,
            ChapterGraphRepository chapterGraphRepository
    ) {
        this.chapterIndividualRepository = chapterIndividualRepository;
        this.individualMentionRepository = individualMentionRepository;
        this.consolidationEngine = consolidationEngine;
        this.chapterGraphRepository = chapterGraphRepository;
    }

    @Transactional(readOnly = true)
    public boolean chapterExists(UUID chapterId) {
        return chapterId != null && chapterGraphRepository.findById(chapterId).isPresent();
    }

    @Transactional
    public ChapterIndividualConsolidationResult consolidateChapter(StageExecutionContext ctx, UUID chapterId) {
        if (chapterId == null) {
            return new ChapterIndividualConsolidationResult(null, false, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterIndividualRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            return new ChapterIndividualConsolidationResult(chapterId, false, 0, 0, "No individual mentions found for chapter");
        }

        chapterIndividualRepository.deleteByChapterId(chapterId);

        List<IndividualMention> mentions = individualMentionRepository.findByChapterId(chapterId).stream()
                .filter(mention -> !NameKeys.from(mention.normalizedName(), mention.aliases()).isEmpty())
                .sorted(Comparator
                        .comparing(IndividualMention::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(IndividualMention::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(IndividualMention::extractionIndex, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        List<List<IndividualMention>> clusters = consolidationEngine.cluster(mentions,
                mention -> NameKeys.from(mention.normalizedName(), mention.aliases()));

        if (clusters.isEmpty()) {
            return new ChapterIndividualConsolidationResult(
                    chapterId, false, Math.toIntExact(mentionCount), 0,
                    "No resolvable individual mentions found for chapter");
        }

        List<ChapterIndividual> chapterIndividuals = new ArrayList<>();
        List<List<UUID>> mentionIdsByCluster = new ArrayList<>();
        for (List<IndividualMention> cluster : clusters) {
            IndividualMention first = cluster.get(0);
            LinkedHashSet<String> mergedAliases = new LinkedHashSet<>();
            List<UUID> mentionIds = new ArrayList<>();
            for (IndividualMention mention : cluster) {
                if (mention.aliases() != null) {
                    for (String alias : mention.aliases()) {
                        if (alias != null && !alias.isBlank()) {
                            mergedAliases.add(alias);
                        }
                    }
                }
                mentionIds.add(mention.id());
            }
            chapterIndividuals.add(new ChapterIndividual(
                    UUID.randomUUID(), chapterId, ctx.stageId(),
                    first.displayName(), first.normalizedName(),
                    List.copyOf(mergedAliases), mentionIds.size(), null, null));
            mentionIdsByCluster.add(mentionIds);
        }

        List<ChapterIndividual> savedIndividuals = new ArrayList<>();
        chapterIndividualRepository.saveAll(chapterIndividuals).forEach(savedIndividuals::add);

        for (int i = 0; i < savedIndividuals.size(); i++) {
            ChapterIndividual chapterIndividual = savedIndividuals.get(i);
            chapterIndividualRepository.linkChapterToIndividual(chapterId, chapterIndividual.id());
            for (UUID mentionId : mentionIdsByCluster.get(i)) {
                chapterIndividualRepository.linkMentionToChapterIndividual(mentionId, chapterIndividual.id(), CHAPTER_CONSOLIDATED);
            }
        }

        return new ChapterIndividualConsolidationResult(
                chapterId, true, Math.toIntExact(mentionCount),
                Math.toIntExact(chapterIndividualRepository.countChapterIndividualsByChapterId(chapterId)),
                "Resolved chapter individual mentions");
    }
}
