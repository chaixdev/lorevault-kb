package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.content.association.ChapterCollective;
import com.lorevault.api.content.association.ChapterCollectiveGraphRepository;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.mention.CollectiveMention;
import com.lorevault.api.content.mention.CollectiveMentionGraphRepository;
import com.lorevault.api.ingestion.pipeline.StageExecutionContext;
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
    private final ChapterGraphRepository chapterGraphRepository;
    private final CollectiveMentionGraphRepository collectiveMentionRepository;

    public ChapterCollectiveConsolidationService(
            ChapterCollectiveGraphRepository chapterCollectiveRepository,
            ChapterGraphRepository chapterGraphRepository,
            CollectiveMentionGraphRepository collectiveMentionRepository
    ) {
        this.chapterCollectiveRepository = chapterCollectiveRepository;
        this.chapterGraphRepository = chapterGraphRepository;
        this.collectiveMentionRepository = collectiveMentionRepository;
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

        List<CollectiveCluster> clusters = clusterMentions(mentions);
        if (clusters.isEmpty()) {
            return new ChapterCollectiveConsolidationResult(
                    chapterId,
                    false,
                    Math.toIntExact(mentionCount),
                    0,
                    "No resolvable collective mentions found for chapter"
            );
        }

        List<ChapterCollective> chapterCollectives = clusters.stream()
                .map(cluster -> new ChapterCollective(
                        UUID.randomUUID(),
                        chapterId,
                        ctx.stageId(),
                        cluster.displayName(),
                        cluster.normalizedName(),
                        List.copyOf(cluster.aliases()),
                        cluster.collectiveType(),
                        cluster.certainty(),
                        cluster.evidence(),
                        cluster.mentionIds().size(),
                        null,
                        null
                ))
                .toList();

        List<ChapterCollective> savedCollectives = new ArrayList<>();
        chapterCollectiveRepository.saveAll(chapterCollectives).forEach(savedCollectives::add);

        for (int i = 0; i < savedCollectives.size(); i++) {
            ChapterCollective chapterCollective = savedCollectives.get(i);
            CollectiveCluster cluster = clusters.get(i);
            chapterCollectiveRepository.linkChapterToCollective(chapterId, chapterCollective.id());
            chapterCollectiveRepository.linkMentionsToChapterCollective(
                    cluster.mentionIds(),
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
        return mention.normalizedName() != null && !mention.normalizedName().isBlank();
    }

    private List<CollectiveCluster> clusterMentions(List<CollectiveMention> mentions) {
        List<CollectiveCluster> clusters = new ArrayList<>();
        for (CollectiveMention mention : mentions) {
            int clusterIndex = findClusterByNormalizedName(clusters, mention.normalizedName());
            if (clusterIndex < 0) {
                clusters.add(CollectiveCluster.from(mention));
                continue;
            }
            clusters.set(clusterIndex, clusters.get(clusterIndex).add(mention));
        }
        return clusters;
    }

    private int findClusterByNormalizedName(List<CollectiveCluster> clusters, String normalizedName) {
        for (int i = 0; i < clusters.size(); i++) {
            if (clusters.get(i).normalizedName().equals(normalizedName)) {
                return i;
            }
        }
        return -1;
    }

    private static String pickFirstNonBlank(String current, String candidate) {
        if (current != null && !current.isBlank()) {
            return current;
        }
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return null;
    }

    private record CollectiveCluster(
            String displayName,
            String normalizedName,
            LinkedHashSet<String> aliases,
            String collectiveType,
            String certainty,
            String evidence,
            List<UUID> mentionIds
    ) {
        static CollectiveCluster from(CollectiveMention mention) {
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            if (mention.aliases() != null) {
                aliases.addAll(mention.aliases().stream().filter(alias -> alias != null && !alias.isBlank()).toList());
            }
            return new CollectiveCluster(
                    mention.displayName(),
                    mention.normalizedName(),
                    aliases,
                    mention.collectiveType(),
                    mention.certainty(),
                    mention.evidence(),
                    new ArrayList<>(List.of(mention.id()))
            );
        }

        CollectiveCluster add(CollectiveMention mention) {
            LinkedHashSet<String> nextAliases = new LinkedHashSet<>(aliases);
            if (mention.aliases() != null) {
                nextAliases.addAll(mention.aliases().stream().filter(alias -> alias != null && !alias.isBlank()).toList());
            }
            List<UUID> nextMentionIds = new ArrayList<>(mentionIds);
            nextMentionIds.add(mention.id());
            return new CollectiveCluster(
                    displayName,
                    normalizedName,
                    nextAliases,
                    pickFirstNonBlank(collectiveType, mention.collectiveType()),
                    pickFirstNonBlank(certainty, mention.certainty()),
                    pickFirstNonBlank(evidence, mention.evidence()),
                    nextMentionIds
            );
        }
    }
}
