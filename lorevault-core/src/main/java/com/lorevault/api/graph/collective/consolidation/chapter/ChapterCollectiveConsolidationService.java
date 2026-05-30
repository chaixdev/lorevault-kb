package com.lorevault.api.graph.collective.consolidation.chapter;

import com.lorevault.api.graph.collective.persistence.ChapterCollective;
import com.lorevault.api.graph.collective.persistence.ChapterCollectiveGraphRepository;
import com.lorevault.api.graph.collective.persistence.CollectiveMention;
import com.lorevault.api.graph.collective.persistence.CollectiveMentionGraphRepository;
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
public class ChapterCollectiveConsolidationService {

    public static final String CHAPTER_CONSOLIDATED = "chapter-consolidated";

    private final ChapterCollectiveGraphRepository chapterCollectiveRepository;
    private final CollectiveMentionGraphRepository collectiveMentionRepository;
    private final ConsolidationEngine consolidationEngine;
    private final ChapterGraphRepository chapterGraphRepository;

    public ChapterCollectiveConsolidationService(
            ChapterCollectiveGraphRepository chapterCollectiveRepository,
            CollectiveMentionGraphRepository collectiveMentionRepository,
            ConsolidationEngine consolidationEngine,
            ChapterGraphRepository chapterGraphRepository
    ) {
        this.chapterCollectiveRepository = chapterCollectiveRepository;
        this.collectiveMentionRepository = collectiveMentionRepository;
        this.consolidationEngine = consolidationEngine;
        this.chapterGraphRepository = chapterGraphRepository;
    }

    @Transactional(readOnly = true)
    public boolean chapterExists(UUID chapterId) {
        return chapterId != null && chapterGraphRepository.findById(chapterId).isPresent();
    }

    @Transactional
    public ChapterCollectiveConsolidationResult consolidateChapter(StageExecutionContext ctx, UUID chapterId) {
        if (chapterId == null) {
            return new ChapterCollectiveConsolidationResult(null, false, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterCollectiveRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            chapterCollectiveRepository.deleteByChapterId(chapterId);
            return new ChapterCollectiveConsolidationResult(chapterId, true, 0, 0, "No collective mentions found for chapter");
        }

        chapterCollectiveRepository.deleteByChapterId(chapterId);

        List<CollectiveMention> mentions = collectiveMentionRepository.findByChapterId(chapterId).stream()
                .filter(this::isResolvable)
                .sorted(Comparator
                        .comparing(CollectiveMention::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(CollectiveMention::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(CollectiveMention::sceneId, Comparator.nullsLast(UUID::compareTo))
                        .thenComparing(CollectiveMention::extractionIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CollectiveMention::id, Comparator.nullsLast(UUID::compareTo)))
                .toList();

        List<List<CollectiveMention>> clusters = clusterMentions(mentions);
        if (clusters.isEmpty()) {
            return new ChapterCollectiveConsolidationResult(
                    chapterId,
                    false,
                    Math.toIntExact(mentionCount),
                    0,
                    "No resolvable collective mentions found for chapter"
            );
        }

        List<List<UUID>> clusterMentionIds = clusters.stream()
                .map(cluster -> cluster.stream().map(CollectiveMention::id).toList())
                .toList();

        List<ChapterCollective> chapterCollectives = clusters.stream()
                .map(cluster -> {
                    CollectiveMention first = cluster.get(0);
                    LinkedHashSet<String> aliases = new LinkedHashSet<>();
                    String collectiveType = null;
                    String certainty = null;
                    String evidence = null;
                    for (CollectiveMention mention : cluster) {
                        if (mention.aliases() != null) {
                            mention.aliases().stream()
                                    .filter(a -> a != null && !a.isBlank())
                                    .forEach(aliases::add);
                        }
                        collectiveType = PickFirstNonBlank.pick(collectiveType, mention.collectiveType());
                        certainty = PickFirstNonBlank.pick(certainty, mention.certainty());
                        evidence = PickFirstNonBlank.pick(evidence, mention.evidence());
                    }
                    return new ChapterCollective(
                            UUID.randomUUID(),
                            chapterId,
                            ctx.stageId(),
                            first.displayName(),
                            first.normalizedName(),
                            List.copyOf(aliases),
                            collectiveType,
                            certainty,
                            evidence,
                            cluster.size(),
                            null,
                            null
                    );
                })
                .toList();

        List<ChapterCollective> savedCollectives = new ArrayList<>();
        chapterCollectiveRepository.saveAll(chapterCollectives).forEach(savedCollectives::add);

        for (int i = 0; i < savedCollectives.size(); i++) {
            ChapterCollective chapterCollective = savedCollectives.get(i);
            chapterCollectiveRepository.linkChapterToCollective(chapterId, chapterCollective.id());
            chapterCollectiveRepository.linkMentionsToChapterCollective(
                    clusterMentionIds.get(i),
                    chapterCollective.id(),
                    CHAPTER_CONSOLIDATED
            );
        }

        return new ChapterCollectiveConsolidationResult(
                chapterId,
                true,
                Math.toIntExact(mentionCount),
                Math.toIntExact(chapterCollectiveRepository.countChapterCollectivesByChapterId(chapterId)),
                "Resolved chapter collectives"
        );
    }

    private boolean isResolvable(CollectiveMention mention) {
        return !NameKeys.from(mention.normalizedName(), mention.aliases()).isEmpty();
    }

    private List<List<CollectiveMention>> clusterMentions(List<CollectiveMention> mentions) {
        return consolidationEngine.cluster(mentions, m -> NameKeys.from(m.normalizedName(), m.aliases()));
    }
}
