package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.content.association.ChapterCollective;
import com.lorevault.api.content.association.ChapterCollectiveGraphRepository;
import com.lorevault.api.content.mention.CollectiveMention;
import com.lorevault.api.content.mention.CollectiveMentionGraphRepository;
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
public class ChapterCollectiveResolutionService {

    public static final String CHAPTER_RESOLVED = "chapter-resolved";

    private final ChapterCollectiveGraphRepository chapterCollectiveRepository;
    private final ChapterEntityGuardService chapterEntityGuardService;
    private final CollectiveMentionGraphRepository collectiveMentionRepository;

    public ChapterCollectiveResolutionService(
            ChapterCollectiveGraphRepository chapterCollectiveRepository,
            ChapterEntityGuardService chapterEntityGuardService,
            CollectiveMentionGraphRepository collectiveMentionRepository
    ) {
        this.chapterCollectiveRepository = chapterCollectiveRepository;
        this.chapterEntityGuardService = chapterEntityGuardService;
        this.collectiveMentionRepository = collectiveMentionRepository;
    }

    @Transactional(readOnly = true)
    public boolean chapterExists(UUID chapterId) {
        return chapterEntityGuardService.chapterExists(chapterId);
    }

    @Transactional
    public ChapterCollectiveResolutionResult resolveChapter(UUID chapterId) {
        if (chapterId == null) {
            return new ChapterCollectiveResolutionResult(null, false, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterCollectiveRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            return new ChapterCollectiveResolutionResult(chapterId, true, 0, 0, "No collective mentions found for chapter");
        }

        chapterCollectiveRepository.deleteByChapterId(chapterId);

        List<CollectiveMention> mentions = collectiveMentionRepository.findByChapterId(chapterId).stream()
                .sorted(Comparator
                        .comparing(CollectiveMention::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(CollectiveMention::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(CollectiveMention::sceneId, Comparator.nullsLast(UUID::compareTo))
                        .thenComparing(CollectiveMention::extractionIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CollectiveMention::id, Comparator.nullsLast(UUID::compareTo)))
                .toList();

        List<List<CollectiveMention>> clusters = ConsolidationEngine.cluster(
                mentions,
                mention -> NameKeys.from(mention.normalizedName(), mention.aliases())
        );
        if (clusters.isEmpty()) {
            return new ChapterCollectiveResolutionResult(
                    chapterId,
                    false,
                    Math.toIntExact(mentionCount),
                    0,
                    "No resolvable collective mentions found for chapter"
            );
        }

        List<List<UUID>> clusterMentionIds = new ArrayList<>();
        List<ChapterCollective> chapterCollectives = new ArrayList<>();
        for (List<CollectiveMention> cluster : clusters) {
            CollectiveMention representative = cluster.get(0);
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            String collectiveType = null;
            String certainty = null;
            String evidence = null;
            List<UUID> mentionIds = new ArrayList<>();
            for (CollectiveMention mention : cluster) {
                if (mention.aliases() != null) {
                    for (String alias : mention.aliases()) {
                        if (alias != null && !alias.isBlank()) {
                            aliases.add(alias);
                        }
                    }
                }
                collectiveType = PickFirstNonBlank.pick(collectiveType, mention.collectiveType());
                certainty = PickFirstNonBlank.pick(certainty, mention.certainty());
                evidence = PickFirstNonBlank.pick(evidence, mention.evidence());
                mentionIds.add(mention.id());
            }
            clusterMentionIds.add(mentionIds);

            chapterCollectives.add(new ChapterCollective(
                    UUID.randomUUID(),
                    chapterId,
                    representative.displayName(),
                    representative.normalizedName(),
                    List.copyOf(aliases),
                    collectiveType,
                    certainty,
                    evidence,
                    cluster.size(),
                    null,
                    null
            ));
        }

        List<ChapterCollective> savedCollectives = new ArrayList<>();
        chapterCollectiveRepository.saveAll(chapterCollectives).forEach(savedCollectives::add);

        for (int i = 0; i < savedCollectives.size(); i++) {
            ChapterCollective chapterCollective = savedCollectives.get(i);
            chapterCollectiveRepository.linkChapterToCollective(chapterId, chapterCollective.id());
            chapterCollectiveRepository.linkMentionsToChapterCollective(
                    clusterMentionIds.get(i),
                    chapterCollective.id(),
                    CHAPTER_RESOLVED
            );
        }

        return new ChapterCollectiveResolutionResult(
                chapterId,
                true,
                Math.toIntExact(mentionCount),
                Math.toIntExact(chapterCollectiveRepository.countChapterCollectivesByChapterId(chapterId)),
                "Resolved chapter collectives"
        );
    }
}
