package com.lorevault.api.graph.object.consolidation.chapter;

import com.lorevault.api.graph.object.persistence.ChapterObject;
import com.lorevault.api.graph.object.persistence.ChapterObjectGraphRepository;
import com.lorevault.api.graph.object.persistence.ObjectMention;
import com.lorevault.api.graph.object.persistence.ObjectMentionGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.library.chapter.ChapterGraphRepository;
import com.lorevault.api.orchestration.consolidation.ConsolidationEngine;
import com.lorevault.api.orchestration.consolidation.NameKeys;
import com.lorevault.api.orchestration.consolidation.PickFirstNonBlank;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterObjectConsolidationService {

    public static final String CHAPTER_CONSOLIDATED = "chapter-consolidated";

    private final ChapterObjectGraphRepository chapterObjectRepository;
    private final ObjectMentionGraphRepository objectMentionRepository;
    private final ConsolidationEngine consolidationEngine;
    private final ChapterGraphRepository chapterGraphRepository;

    public ChapterObjectConsolidationService(
            ChapterObjectGraphRepository chapterObjectRepository,
            ObjectMentionGraphRepository objectMentionRepository,
            ConsolidationEngine consolidationEngine,
            ChapterGraphRepository chapterGraphRepository
    ) {
        this.chapterObjectRepository = chapterObjectRepository;
        this.objectMentionRepository = objectMentionRepository;
        this.consolidationEngine = consolidationEngine;
        this.chapterGraphRepository = chapterGraphRepository;
    }

    @Transactional(readOnly = true)
    public boolean chapterExists(UUID chapterId) {
        return chapterId != null && chapterGraphRepository.findById(chapterId).isPresent();
    }

    @Transactional
    public ChapterObjectConsolidationResult consolidateChapter(StageExecutionContext ctx, UUID chapterId) {
        if (chapterId == null) {
            return new ChapterObjectConsolidationResult(null, false, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterObjectRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            chapterObjectRepository.deleteByChapterId(chapterId);
            return new ChapterObjectConsolidationResult(chapterId, true, 0, 0, "No object mentions found for chapter");
        }

        chapterObjectRepository.deleteByChapterId(chapterId);

        List<ObjectMention> mentions = objectMentionRepository.findByChapterId(chapterId).stream()
                .filter(this::isResolvable)
                .sorted(Comparator
                        .comparing(ObjectMention::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ObjectMention::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ObjectMention::sceneId, Comparator.nullsLast(UUID::compareTo))
                        .thenComparing(ObjectMention::extractionIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ObjectMention::id, Comparator.nullsLast(UUID::compareTo)))
                .toList();

        List<List<ObjectMention>> clusters = clusterMentions(mentions);
        if (clusters.isEmpty()) {
            return new ChapterObjectConsolidationResult(
                    chapterId,
                    false,
                    Math.toIntExact(mentionCount),
                    0,
                    "No resolvable object mentions found for chapter"
            );
        }

        List<ChapterObject> chapterObjects = clusters.stream()
                .map(cluster -> {
                    ObjectMention first = cluster.get(0);
                    List<String> aliases = cluster.stream()
                            .filter(m -> m.aliases() != null)
                            .flatMap(m -> m.aliases().stream())
                            .filter(a -> a != null && !a.isBlank())
                            .distinct()
                            .toList();
                    String type = cluster.stream().map(ObjectMention::type).reduce(null, PickFirstNonBlank::pick);
                    String material = cluster.stream().map(ObjectMention::material).reduce(null, PickFirstNonBlank::pick);
                    String purpose = cluster.stream().map(ObjectMention::purpose).reduce(null, PickFirstNonBlank::pick);
                    String description = cluster.stream().map(ObjectMention::description).reduce(null, PickFirstNonBlank::pick);
                    List<UUID> mentionIds = cluster.stream().map(ObjectMention::id).toList();
                    return new ChapterObject(
                            UUID.randomUUID(),
                            chapterId,
                            ctx.stageId(),
                            first.displayName(),
                            first.normalizedName(),
                            aliases,
                            type,
                            material,
                            purpose,
                            description,
                            mentionIds.size(),
                            null,
                            null
                    );
                })
                .toList();

        List<List<UUID>> clusterMentionIds = clusters.stream()
                .map(cluster -> cluster.stream().map(ObjectMention::id).toList())
                .toList();

        List<ChapterObject> savedObjects = new ArrayList<>();
        chapterObjectRepository.saveAll(chapterObjects).forEach(savedObjects::add);

        for (int i = 0; i < savedObjects.size(); i++) {
            ChapterObject chapterObject = savedObjects.get(i);
            chapterObjectRepository.linkChapterToObject(chapterId, chapterObject.id());
            chapterObjectRepository.linkMentionsToChapterObject(clusterMentionIds.get(i), chapterObject.id(), CHAPTER_CONSOLIDATED);
        }

        return new ChapterObjectConsolidationResult(
                chapterId,
                true,
                Math.toIntExact(mentionCount),
                Math.toIntExact(chapterObjectRepository.countChapterObjectsByChapterId(chapterId)),
                "Resolved chapter objects"
        );
    }

    private boolean isResolvable(ObjectMention mention) {
        return !NameKeys.from(mention.normalizedName(), mention.aliases()).isEmpty();
    }

    private List<List<ObjectMention>> clusterMentions(List<ObjectMention> mentions) {
        return consolidationEngine.cluster(mentions, mention -> NameKeys.from(mention.normalizedName(), mention.aliases()));
    }
}
