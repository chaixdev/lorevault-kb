package com.lorevault.api.ingestion.resolution.object;

import com.lorevault.api.content.association.ChapterObject;
import com.lorevault.api.content.association.ChapterObjectGraphRepository;
import com.lorevault.api.content.mention.ObjectMention;
import com.lorevault.api.content.mention.ObjectMentionGraphRepository;
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
public class ChapterObjectResolutionService {

    public static final String CHAPTER_RESOLVED = "chapter-resolved";

    private final ChapterObjectGraphRepository chapterObjectRepository;
    private final ObjectMentionGraphRepository objectMentionRepository;
    private final ChapterEntityGuardService chapterEntityGuardService;

    public ChapterObjectResolutionService(
            ChapterObjectGraphRepository chapterObjectRepository,
            ObjectMentionGraphRepository objectMentionRepository,
            ChapterEntityGuardService chapterEntityGuardService
    ) {
        this.chapterObjectRepository = chapterObjectRepository;
        this.objectMentionRepository = objectMentionRepository;
        this.chapterEntityGuardService = chapterEntityGuardService;
    }

    @Transactional(readOnly = true)
    public boolean chapterExists(UUID chapterId) {
        return chapterEntityGuardService.chapterExists(chapterId);
    }

    @Transactional
    public ChapterObjectResolutionResult resolveChapter(UUID chapterId) {
        if (chapterId == null) {
            return new ChapterObjectResolutionResult(null, false, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterObjectRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            return new ChapterObjectResolutionResult(chapterId, true, 0, 0, "No object mentions found for chapter");
        }

        chapterObjectRepository.deleteByChapterId(chapterId);

        List<ObjectMention> mentions = objectMentionRepository.findByChapterId(chapterId).stream()
                .sorted(Comparator
                        .comparing(ObjectMention::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ObjectMention::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ObjectMention::sceneId, Comparator.nullsLast(UUID::compareTo))
                        .thenComparing(ObjectMention::extractionIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ObjectMention::id, Comparator.nullsLast(UUID::compareTo)))
                .toList();

        List<List<ObjectMention>> clusters = ConsolidationEngine.cluster(
                mentions,
                mention -> NameKeys.from(mention.normalizedName(), mention.aliases())
        );

        if (clusters.isEmpty()) {
            return new ChapterObjectResolutionResult(
                    chapterId,
                    false,
                    Math.toIntExact(mentionCount),
                    0,
                    "No resolvable object mentions found for chapter"
            );
        }

        List<ChapterObject> chapterObjects = new ArrayList<>();
        List<List<UUID>> mentionIdsByCluster = new ArrayList<>();

        for (List<ObjectMention> cluster : clusters) {
            ObjectMention representative = cluster.get(0);
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            List<UUID> mentionIds = new ArrayList<>();
            String type = null;
            String material = null;
            String purpose = null;
            String description = null;
            for (ObjectMention m : cluster) {
                mentionIds.add(m.id());
                if (m.aliases() != null) {
                    m.aliases().stream()
                            .filter(a -> a != null && !a.isBlank())
                            .forEach(aliases::add);
                }
                type = PickFirstNonBlank.pick(type, m.type());
                material = PickFirstNonBlank.pick(material, m.material());
                purpose = PickFirstNonBlank.pick(purpose, m.purpose());
                description = PickFirstNonBlank.pick(description, m.description());
            }
            mentionIdsByCluster.add(mentionIds);
            chapterObjects.add(new ChapterObject(
                    UUID.randomUUID(),
                    chapterId,
                    representative.displayName(),
                    representative.normalizedName(),
                    List.copyOf(aliases),
                    type,
                    material,
                    purpose,
                    description,
                    cluster.size(),
                    null,
                    null
            ));
        }

        List<ChapterObject> savedObjects = new ArrayList<>();
        chapterObjectRepository.saveAll(chapterObjects).forEach(savedObjects::add);

        for (int i = 0; i < savedObjects.size(); i++) {
            ChapterObject chapterObject = savedObjects.get(i);
            List<UUID> mentionIds = mentionIdsByCluster.get(i);
            chapterObjectRepository.linkChapterToObject(chapterId, chapterObject.id());
            chapterObjectRepository.linkMentionsToChapterObject(mentionIds, chapterObject.id(), CHAPTER_RESOLVED);
        }

        return new ChapterObjectResolutionResult(
                chapterId,
                true,
                Math.toIntExact(mentionCount),
                Math.toIntExact(chapterObjectRepository.countChapterObjectsByChapterId(chapterId)),
                "Resolved chapter objects"
        );
    }
}
