package com.lorevault.api.graph.location.consolidation.chapter;

import com.lorevault.api.graph.location.persistence.ChapterLocation;
import com.lorevault.api.graph.location.persistence.ChapterLocationGraphRepository;
import com.lorevault.api.graph.location.persistence.LocationMention;
import com.lorevault.api.graph.location.persistence.LocationMentionGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.consolidation.ConsolidationEngine;
import com.lorevault.api.orchestration.consolidation.NameKeys;
import com.lorevault.api.library.chapter.ChapterGraphRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterLocationConsolidationService {

    public static final String CHAPTER_CONSOLIDATED = "chapter-consolidated";

    private final ChapterLocationGraphRepository chapterLocationRepository;
    private final LocationMentionGraphRepository locationMentionRepository;
    private final ConsolidationEngine consolidationEngine;
    private final ChapterGraphRepository chapterGraphRepository;

    public ChapterLocationConsolidationService(
            ChapterLocationGraphRepository chapterLocationRepository,
            LocationMentionGraphRepository locationMentionRepository,
            ConsolidationEngine consolidationEngine,
            ChapterGraphRepository chapterGraphRepository
    ) {
        this.chapterLocationRepository = chapterLocationRepository;
        this.locationMentionRepository = locationMentionRepository;
        this.consolidationEngine = consolidationEngine;
        this.chapterGraphRepository = chapterGraphRepository;
    }

    @Transactional(readOnly = true)
    public boolean chapterExists(UUID chapterId) {
        return chapterId != null && chapterGraphRepository.findById(chapterId).isPresent();
    }

    @Transactional
    public ChapterLocationConsolidationResult consolidateChapter(StageExecutionContext ctx, UUID chapterId) {
        if (chapterId == null) {
            return new ChapterLocationConsolidationResult(null, false, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterLocationRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            chapterLocationRepository.deleteByChapterId(chapterId);
            return new ChapterLocationConsolidationResult(chapterId, true, 0, 0, "No location mentions found for chapter");
        }

        chapterLocationRepository.deleteByChapterId(chapterId);

        List<LocationMention> mentions = locationMentionRepository.findByChapterId(chapterId).stream()
                .filter(mention -> !NameKeys.from(mention.normalizedName(), mention.aliases()).isEmpty())
                .sorted(Comparator
                        .comparing(LocationMention::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LocationMention::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LocationMention::extractionIndex, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        List<List<LocationMention>> clusters = consolidationEngine.cluster(mentions,
                mention -> NameKeys.from(mention.normalizedName(), mention.aliases()));

        if (clusters.isEmpty()) {
            return new ChapterLocationConsolidationResult(
                    chapterId, false, Math.toIntExact(mentionCount), 0,
                    "No resolvable location mentions found for chapter");
        }

        List<ChapterLocation> chapterLocations = new ArrayList<>();
        List<List<UUID>> mentionIdsByCluster = new ArrayList<>();
        for (List<LocationMention> cluster : clusters) {
            LocationMention first = cluster.get(0);
            LinkedHashSet<String> mergedAliases = new LinkedHashSet<>();
            List<UUID> mentionIds = new ArrayList<>();
            for (LocationMention mention : cluster) {
                if (mention.aliases() != null) {
                    for (String alias : mention.aliases()) {
                        if (alias != null && !alias.isBlank()) {
                            mergedAliases.add(alias);
                        }
                    }
                }
                mentionIds.add(mention.id());
            }
            chapterLocations.add(new ChapterLocation(
                    UUID.randomUUID(), chapterId, ctx.stageId(),
                    first.displayName(), first.normalizedName(),
                    List.copyOf(mergedAliases), mentionIds.size(), null, null));
            mentionIdsByCluster.add(mentionIds);
        }

        List<ChapterLocation> savedLocations = new ArrayList<>();
        chapterLocationRepository.saveAll(chapterLocations).forEach(savedLocations::add);

        for (int i = 0; i < savedLocations.size(); i++) {
            ChapterLocation chapterLocation = savedLocations.get(i);
            chapterLocationRepository.linkChapterToLocation(chapterId, chapterLocation.id());
            chapterLocationRepository.linkMentionsToChapterLocation(
                    mentionIdsByCluster.get(i), chapterLocation.id(), CHAPTER_CONSOLIDATED);
        }

        return new ChapterLocationConsolidationResult(
                chapterId, true, Math.toIntExact(mentionCount),
                Math.toIntExact(chapterLocationRepository.countChapterLocationsByChapterId(chapterId)),
                "Resolved chapter locations");
    }
}
