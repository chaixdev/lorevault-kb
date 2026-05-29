package com.lorevault.api.ingestion.resolution.object;

import com.lorevault.api.content.association.ChapterObject;
import com.lorevault.api.content.association.ChapterObjectGraphRepository;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.content.mention.ObjectMention;
import com.lorevault.api.content.mention.ObjectMentionGraphRepository;
import com.lorevault.api.ingestion.pipeline.StageExecutionContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterObjectConsolidationService {

    public static final String CHAPTER_CONSOLIDATED = "chapter-consolidated";

    private final ChapterObjectGraphRepository chapterObjectRepository;
    private final ChapterGraphRepository chapterGraphRepository;
    private final ObjectMentionGraphRepository objectMentionRepository;

    public ChapterObjectConsolidationService(
            ChapterObjectGraphRepository chapterObjectRepository,
            ChapterGraphRepository chapterGraphRepository,
            ObjectMentionGraphRepository objectMentionRepository
    ) {
        this.chapterObjectRepository = chapterObjectRepository;
        this.chapterGraphRepository = chapterGraphRepository;
        this.objectMentionRepository = objectMentionRepository;
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

        List<ObjectCluster> clusters = clusterMentions(mentions);
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
                .map(cluster -> new ChapterObject(
                        UUID.randomUUID(),
                        chapterId,
                        ctx.stageId(),
                        cluster.displayName(),
                        cluster.normalizedName(),
                        List.copyOf(cluster.aliases()),
                        cluster.type(),
                        cluster.material(),
                        cluster.purpose(),
                        cluster.description(),
                        cluster.mentionIds().size(),
                        null,
                        null
                ))
                .toList();

        List<ChapterObject> savedObjects = new ArrayList<>();
        chapterObjectRepository.saveAll(chapterObjects).forEach(savedObjects::add);

        for (int i = 0; i < savedObjects.size(); i++) {
            ChapterObject chapterObject = savedObjects.get(i);
            ObjectCluster cluster = clusters.get(i);
            chapterObjectRepository.linkChapterToObject(chapterId, chapterObject.id());
            chapterObjectRepository.linkMentionsToChapterObject(cluster.mentionIds(), chapterObject.id(), CHAPTER_CONSOLIDATED);
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
        return mention.normalizedName() != null && !mention.normalizedName().isBlank();
    }

    private List<ObjectCluster> clusterMentions(List<ObjectMention> mentions) {
        List<ObjectCluster> clusters = new ArrayList<>();
        for (ObjectMention mention : mentions) {
            int clusterIndex = findClusterByNormalizedName(clusters, mention.normalizedName());
            if (clusterIndex < 0) {
                clusters.add(ObjectCluster.from(mention));
                continue;
            }
            clusters.set(clusterIndex, clusters.get(clusterIndex).add(mention));
        }
        return clusters;
    }

    private int findClusterByNormalizedName(List<ObjectCluster> clusters, String normalizedName) {
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

    private record ObjectCluster(
            String displayName,
            String normalizedName,
            LinkedHashSet<String> aliases,
            String type,
            String material,
            String purpose,
            String description,
            List<UUID> mentionIds
    ) {
        static ObjectCluster from(ObjectMention mention) {
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            if (mention.aliases() != null) {
                aliases.addAll(mention.aliases().stream().filter(alias -> alias != null && !alias.isBlank()).toList());
            }
            return new ObjectCluster(
                    mention.displayName(),
                    mention.normalizedName(),
                    aliases,
                    mention.type(),
                    mention.material(),
                    mention.purpose(),
                    mention.description(),
                    new ArrayList<>(List.of(mention.id()))
            );
        }

        ObjectCluster add(ObjectMention mention) {
            LinkedHashSet<String> nextAliases = new LinkedHashSet<>(aliases);
            if (mention.aliases() != null) {
                nextAliases.addAll(mention.aliases().stream().filter(alias -> alias != null && !alias.isBlank()).toList());
            }
            List<UUID> nextMentionIds = new ArrayList<>(mentionIds);
            nextMentionIds.add(mention.id());
            return new ObjectCluster(
                    displayName,
                    normalizedName,
                    nextAliases,
                    pickFirstNonBlank(type, mention.type()),
                    pickFirstNonBlank(material, mention.material()),
                    pickFirstNonBlank(purpose, mention.purpose()),
                    pickFirstNonBlank(description, mention.description()),
                    nextMentionIds
            );
        }
    }
}
