package com.lorevault.api.ingestion.application.resolution;

import com.lorevault.api.content.entities.ChapterLocation;
import com.lorevault.api.content.entities.ChapterLocationGraphRepository;
import com.lorevault.api.content.entities.ChapterGraphRepository;
import com.lorevault.api.content.entities.LocationMention;
import com.lorevault.api.content.entities.LocationMentionGraphRepository;
import com.lorevault.api.ingestion.application.result.ChapterLocationResolutionResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterLocationResolutionService {

    public static final String CHAPTER_RESOLVED = "chapter-resolved";

    private final ChapterLocationGraphRepository chapterLocationRepository;
    private final ChapterGraphRepository chapterGraphRepository;
    private final LocationMentionGraphRepository locationMentionRepository;

    public ChapterLocationResolutionService(
            ChapterLocationGraphRepository chapterLocationRepository,
            ChapterGraphRepository chapterGraphRepository,
            LocationMentionGraphRepository locationMentionRepository
    ) {
        this.chapterLocationRepository = chapterLocationRepository;
        this.chapterGraphRepository = chapterGraphRepository;
        this.locationMentionRepository = locationMentionRepository;
    }

    @Transactional(readOnly = true)
    public boolean chapterExists(UUID chapterId) {
        return chapterId != null && chapterGraphRepository.findById(chapterId).isPresent();
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
                .filter(this::isResolvable)
                .sorted(Comparator
                        .comparing(LocationMention::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LocationMention::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LocationMention::extractionIndex, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        List<LocationCluster> clusters = clusterMentions(mentions);
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
        for (LocationCluster cluster : clusters) {
            chapterLocations.add(new ChapterLocation(
                    UUID.randomUUID(),
                    chapterId,
                    cluster.displayName(),
                    cluster.normalizedName(),
                    List.copyOf(cluster.aliases()),
                    cluster.mentionIds().size(),
                    null,
                    null
            ));
        }

        List<ChapterLocation> savedLocations = new ArrayList<>();
        chapterLocationRepository.saveAll(chapterLocations).forEach(savedLocations::add);

        for (int i = 0; i < savedLocations.size(); i++) {
            ChapterLocation chapterLocation = savedLocations.get(i);
            LocationCluster cluster = clusters.get(i);
            chapterLocationRepository.linkChapterToLocation(chapterId, chapterLocation.id());
            chapterLocationRepository.linkMentionsToChapterLocation(cluster.mentionIds(), chapterLocation.id(), CHAPTER_RESOLVED);
        }

        return new ChapterLocationResolutionResult(
                chapterId,
                true,
                Math.toIntExact(mentionCount),
                Math.toIntExact(chapterLocationRepository.countChapterLocationsByChapterId(chapterId)),
                "Resolved chapter locations"
        );
    }

    private boolean isResolvable(LocationMention mention) {
        return !keysFor(mention).isEmpty();
    }

    private List<LocationCluster> clusterMentions(List<LocationMention> mentions) {
        List<LocationCluster> clusters = new ArrayList<>();
        Map<String, Integer> clusterIndexByKey = new LinkedHashMap<>();

        for (LocationMention mention : mentions) {
            Set<String> keys = keysFor(mention);
            if (keys.isEmpty()) {
                continue;
            }

            Set<Integer> matchingIndexes = new LinkedHashSet<>();
            for (String key : keys) {
                Integer index = clusterIndexByKey.get(key);
                if (index != null) {
                    matchingIndexes.add(index);
                }
            }

            if (matchingIndexes.isEmpty()) {
                LocationCluster cluster = LocationCluster.from(mention, keys);
                clusters.add(cluster);
                int newIndex = clusters.size() - 1;
                for (String key : cluster.keys()) {
                    clusterIndexByKey.put(key, newIndex);
                }
                continue;
            }

            int baseIndex = matchingIndexes.iterator().next();
            LocationCluster merged = clusters.get(baseIndex).add(mention, keys);
            clusters.set(baseIndex, merged);

            List<Integer> otherIndexes = matchingIndexes.stream().skip(1).sorted(Comparator.reverseOrder()).toList();
            for (Integer otherIndex : otherIndexes) {
                merged = merged.merge(clusters.get(otherIndex));
                clusters.set(baseIndex, merged);
                clusters.remove((int) otherIndex);
            }

            clusterIndexByKey.clear();
            for (int i = 0; i < clusters.size(); i++) {
                for (String key : clusters.get(i).keys()) {
                    clusterIndexByKey.put(key, i);
                }
            }
        }

        return clusters;
    }

    private Set<String> keysFor(LocationMention mention) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addKey(keys, mention.normalizedName());
        if (mention.aliases() != null) {
            for (String alias : mention.aliases()) {
                addKey(keys, normalizeName(alias));
            }
        }
        return keys;
    }

    private void addKey(Set<String> keys, String key) {
        if (key != null && !key.isBlank()) {
            keys.add(key);
        }
    }

    private String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ").toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    private record LocationCluster(
            String displayName,
            String normalizedName,
            LinkedHashSet<String> aliases,
            LinkedHashSet<String> keys,
            List<UUID> mentionIds
    ) {
        static LocationCluster from(LocationMention mention, Set<String> keys) {
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            if (mention.aliases() != null) {
                aliases.addAll(mention.aliases().stream().filter(alias -> alias != null && !alias.isBlank()).toList());
            }
            return new LocationCluster(
                    mention.displayName(),
                    mention.normalizedName(),
                    aliases,
                    new LinkedHashSet<>(keys),
                    new ArrayList<>(List.of(mention.id()))
            );
        }

        LocationCluster add(LocationMention mention, Set<String> additionalKeys) {
            LinkedHashSet<String> nextAliases = new LinkedHashSet<>(aliases);
            if (mention.aliases() != null) {
                nextAliases.addAll(mention.aliases().stream().filter(alias -> alias != null && !alias.isBlank()).toList());
            }
            LinkedHashSet<String> nextKeys = new LinkedHashSet<>(keys);
            nextKeys.addAll(additionalKeys);
            List<UUID> nextMentionIds = new ArrayList<>(mentionIds);
            nextMentionIds.add(mention.id());
            return new LocationCluster(displayName, normalizedName, nextAliases, nextKeys, nextMentionIds);
        }

        LocationCluster merge(LocationCluster other) {
            LinkedHashSet<String> nextAliases = new LinkedHashSet<>(aliases);
            nextAliases.addAll(other.aliases);
            LinkedHashSet<String> nextKeys = new LinkedHashSet<>(keys);
            nextKeys.addAll(other.keys);
            List<UUID> nextMentionIds = new ArrayList<>(mentionIds);
            nextMentionIds.addAll(other.mentionIds);
            return new LocationCluster(displayName, normalizedName, nextAliases, nextKeys, nextMentionIds);
        }
    }
}
