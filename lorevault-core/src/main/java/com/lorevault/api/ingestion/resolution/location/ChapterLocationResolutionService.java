package com.lorevault.api.ingestion.resolution.location;

import com.lorevault.api.content.association.ChapterLocation;
import com.lorevault.api.content.association.ChapterLocationGraphRepository;
import com.lorevault.api.content.mention.LocationMention;
import com.lorevault.api.content.mention.LocationMentionGraphRepository;
import com.lorevault.api.ingestion.resolution.consolidation.ChapterEntityGuardService;
import com.lorevault.api.ingestion.resolution.consolidation.ConsolidationEngine;
import com.lorevault.api.ingestion.resolution.consolidation.NameKeys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterLocationResolutionService {

    public static final String CHAPTER_RESOLVED = "chapter-resolved";

    private final ChapterLocationGraphRepository chapterLocationRepository;
    private final LocationMentionGraphRepository locationMentionRepository;
    private final ChapterEntityGuardService chapterEntityGuardService;

    public ChapterLocationResolutionService(
            ChapterLocationGraphRepository chapterLocationRepository,
            LocationMentionGraphRepository locationMentionRepository,
            ChapterEntityGuardService chapterEntityGuardService
    ) {
        this.chapterLocationRepository = chapterLocationRepository;
        this.locationMentionRepository = locationMentionRepository;
        this.chapterEntityGuardService = chapterEntityGuardService;
    }

    @Transactional(readOnly = true)
    public boolean chapterExists(UUID chapterId) {
        return chapterEntityGuardService.chapterExists(chapterId);
    }

    @Transactional
    public ChapterLocationResolutionResult resolveChapter(UUID chapterId) {
        if (chapterId == null) {
            return new ChapterLocationResolutionResult(null, false, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterLocationRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            return new ChapterLocationResolutionResult(chapterId, false, 0, 0, "No location mentions found for chapter");
        }

        chapterLocationRepository.deleteByChapterId(chapterId);

        List<LocationMention> mentions = locationMentionRepository.findByChapterId(chapterId).stream()
                .sorted(Comparator
                        .comparing(LocationMention::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LocationMention::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LocationMention::extractionIndex, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        List<List<LocationMention>> clusters = ConsolidationEngine.cluster(
                mentions,
                mention -> NameKeys.from(mention.normalizedName(), mention.aliases())
        );

        if (clusters.isEmpty()) {
            return new ChapterLocationResolutionResult(
                    chapterId,
                    false,
                    Math.toIntExact(mentionCount),
                    0,
                    "No resolvable location mentions found for chapter"
            );
        }

        List<ChapterLocation> chapterLocations = new ArrayList<>();
        List<List<UUID>> mentionIdsByCluster = new ArrayList<>();

        for (List<LocationMention> cluster : clusters) {
            LocationMention representative = cluster.get(0);
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            List<UUID> mentionIds = new ArrayList<>();
            for (LocationMention m : cluster) {
                mentionIds.add(m.id());
                if (m.aliases() != null) {
                    m.aliases().stream()
                            .filter(a -> a != null && !a.isBlank())
                            .forEach(aliases::add);
                }
            }
            mentionIdsByCluster.add(mentionIds);
            chapterLocations.add(new ChapterLocation(
                    UUID.randomUUID(),
                    chapterId,
                    representative.displayName(),
                    representative.normalizedName(),
                    List.copyOf(aliases),
                    cluster.size(),
                    null,
                    null
            ));
        }

        List<ChapterLocation> savedLocations = new ArrayList<>();
        chapterLocationRepository.saveAll(chapterLocations).forEach(savedLocations::add);

        for (int i = 0; i < savedLocations.size(); i++) {
            ChapterLocation chapterLocation = savedLocations.get(i);
            List<UUID> mentionIds = mentionIdsByCluster.get(i);
            chapterLocationRepository.linkChapterToLocation(chapterId, chapterLocation.id());
            chapterLocationRepository.linkMentionsToChapterLocation(mentionIds, chapterLocation.id(), CHAPTER_RESOLVED);
        }

        return new ChapterLocationResolutionResult(
                chapterId,
                true,
                Math.toIntExact(mentionCount),
                Math.toIntExact(chapterLocationRepository.countChapterLocationsByChapterId(chapterId)),
                "Resolved chapter locations"
        );
    }
}
