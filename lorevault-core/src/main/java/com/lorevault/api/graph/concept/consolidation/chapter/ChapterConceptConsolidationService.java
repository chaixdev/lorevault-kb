package com.lorevault.api.graph.concept.consolidation.chapter;

import com.lorevault.api.graph.concept.persistence.ChapterConcept;
import com.lorevault.api.graph.concept.persistence.ChapterConceptGraphRepository;
import com.lorevault.api.graph.concept.persistence.ConceptMention;
import com.lorevault.api.graph.concept.persistence.ConceptMentionGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.library.chapter.ChapterGraphRepository;
import com.lorevault.api.orchestration.consolidation.ConsolidationEngine;
import com.lorevault.api.orchestration.consolidation.NameKeys;
import com.lorevault.api.orchestration.consolidation.PickFirstNonBlank;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterConceptConsolidationService {

    public static final String CHAPTER_CONSOLIDATED = "chapter-consolidated";

    private final ChapterConceptGraphRepository chapterConceptRepository;
    private final ConceptMentionGraphRepository conceptMentionRepository;
    private final ConsolidationEngine consolidationEngine;
    private final ChapterGraphRepository chapterGraphRepository;

    public ChapterConceptConsolidationService(
            ChapterConceptGraphRepository chapterConceptRepository,
            ConceptMentionGraphRepository conceptMentionRepository,
            ConsolidationEngine consolidationEngine,
            ChapterGraphRepository chapterGraphRepository
    ) {
        this.chapterConceptRepository = chapterConceptRepository;
        this.conceptMentionRepository = conceptMentionRepository;
        this.consolidationEngine = consolidationEngine;
        this.chapterGraphRepository = chapterGraphRepository;
    }

    @Transactional(readOnly = true)
    public boolean chapterExists(UUID chapterId) {
        return chapterId != null && chapterGraphRepository.findById(chapterId).isPresent();
    }

    @Transactional
    public ChapterConceptConsolidationResult consolidateChapter(StageExecutionContext ctx, UUID chapterId) {
        if (chapterId == null) {
            return new ChapterConceptConsolidationResult(null, false, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterConceptRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            chapterConceptRepository.deleteByChapterId(chapterId);
            return new ChapterConceptConsolidationResult(chapterId, true, 0, 0, "No concept mentions found for chapter");
        }

        chapterConceptRepository.deleteByChapterId(chapterId);

        List<ConceptMention> mentions = conceptMentionRepository.findByChapterId(chapterId).stream()
                .filter(this::isResolvable)
                .sorted(Comparator
                        .comparing(ConceptMention::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ConceptMention::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ConceptMention::sceneId, Comparator.nullsLast(UUID::compareTo))
                        .thenComparing(ConceptMention::extractionIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ConceptMention::id, Comparator.nullsLast(UUID::compareTo)))
                .toList();

        List<List<ConceptMention>> clusters = clusterMentions(mentions);
        if (clusters.isEmpty()) {
            return new ChapterConceptConsolidationResult(
                    chapterId,
                    false,
                    Math.toIntExact(mentionCount),
                    0,
                    "No resolvable concept mentions found for chapter"
            );
        }

        List<List<UUID>> clusterMentionIds = clusters.stream()
                .map(cluster -> cluster.stream().map(ConceptMention::id).toList())
                .toList();

        List<ChapterConcept> chapterConcepts = clusters.stream()
                .filter(cluster -> !cluster.isEmpty())
                .map(cluster -> {
                    ConceptMention first = cluster.get(0);
                    LinkedHashSet<String> aliases = new LinkedHashSet<>();
                    String conceptType = null;
                    String description = null;
                    String certainty = null;
                    String evidence = null;
                    for (ConceptMention mention : cluster) {
                        if (mention.aliases() != null) {
                            mention.aliases().stream()
                                    .filter(a -> a != null && !a.isBlank())
                                    .forEach(aliases::add);
                        }
                        conceptType = PickFirstNonBlank.pick(conceptType, mention.conceptType());
                        description = PickFirstNonBlank.pick(description, mention.description());
                        certainty = PickFirstNonBlank.pick(certainty, mention.certainty());
                        evidence = PickFirstNonBlank.pick(evidence, mention.evidence());
                    }
                    return new ChapterConcept(
                            UUID.randomUUID(),
                            chapterId,
                            ctx.stageId(),
                            first.displayName(),
                            first.normalizedName(),
                            List.copyOf(aliases),
                            conceptType,
                            description,
                            certainty,
                            evidence,
                            cluster.size(),
                            null,
                            null
                    );
                })
                .toList();

        List<ChapterConcept> savedConcepts = new ArrayList<>();
        chapterConceptRepository.saveAll(chapterConcepts).forEach(savedConcepts::add);

        for (int i = 0; i < savedConcepts.size(); i++) {
            ChapterConcept chapterConcept = savedConcepts.get(i);
            chapterConceptRepository.linkChapterToConcept(chapterId, chapterConcept.id());
            chapterConceptRepository.linkMentionsToChapterConcept(
                    clusterMentionIds.get(i),
                    chapterConcept.id(),
                    CHAPTER_CONSOLIDATED
            );
        }

        return new ChapterConceptConsolidationResult(
                chapterId,
                true,
                Math.toIntExact(mentionCount),
                Math.toIntExact(chapterConceptRepository.countChapterConceptsByChapterId(chapterId)),
                "Resolved chapter concepts"
        );
    }

    private boolean isResolvable(ConceptMention mention) {
        return !NameKeys.from(mention.normalizedName(), mention.aliases()).isEmpty();
    }

    private List<List<ConceptMention>> clusterMentions(List<ConceptMention> mentions) {
        return consolidationEngine.cluster(mentions, m -> NameKeys.from(m.normalizedName(), m.aliases()));
    }
}
