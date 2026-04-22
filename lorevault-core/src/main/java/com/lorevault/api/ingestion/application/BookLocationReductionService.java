package com.lorevault.api.ingestion.application;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.IngestionFailure;

import com.lorevault.api.content.BookLocation;
import com.lorevault.api.content.BookLocationGraphRepository;
import com.lorevault.api.content.BookGraphRepository;
import com.lorevault.api.content.ChapterLocation;
import com.lorevault.api.content.ChapterLocationGraphRepository;
import com.lorevault.api.ingestion.application.BookLocationResolutionResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookLocationReductionService {

    private final BookLocationGraphRepository bookLocationRepository;
    private final BookGraphRepository bookGraphRepository;
    private final ChapterLocationGraphRepository chapterLocationRepository;
    private final ConcurrentHashMap<UUID, ReentrantLock> bookLocks = new ConcurrentHashMap<>();

    public BookLocationReductionService(
            BookLocationGraphRepository bookLocationRepository,
            BookGraphRepository bookGraphRepository,
            ChapterLocationGraphRepository chapterLocationRepository
    ) {
        this.bookLocationRepository = bookLocationRepository;
        this.bookGraphRepository = bookGraphRepository;
        this.chapterLocationRepository = chapterLocationRepository;
    }

    @Transactional(readOnly = true)
    public boolean bookExists(UUID bookId) {
        return bookId != null && bookGraphRepository.findById(bookId).isPresent();
    }

    @Transactional
    public BookLocationResolutionResult resolveBook(UUID bookId) {
        if (bookId == null) {
            return new BookLocationResolutionResult(null, false, 0, 0, "Book ID is required");
        }

        ReentrantLock lock = bookLocks.computeIfAbsent(bookId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return resolveBookLocked(bookId);
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                bookLocks.remove(bookId, lock);
            }
        }
    }

    private BookLocationResolutionResult resolveBookLocked(UUID bookId) {
        List<ChapterLocation> chapterLocations = chapterLocationRepository.findByBookId(bookId).stream()
                .filter(this::isResolvable)
                .sorted(Comparator
                        .comparing(ChapterLocation::normalizedName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ChapterLocation::displayName, Comparator.nullsLast(String::compareTo)))
                .toList();

        if (chapterLocations.isEmpty()) {
            return new BookLocationResolutionResult(bookId, false, 0, 0, "No chapter locations found for book");
        }

        bookLocationRepository.deleteByBookId(bookId);

        List<LocationCluster> clusters = clusterLocations(chapterLocations);
        if (clusters.isEmpty()) {
            return new BookLocationResolutionResult(bookId, false, chapterLocations.size(), 0, "No resolvable chapter locations found for book");
        }

        List<BookLocation> bookLocations = new ArrayList<>();
        for (LocationCluster cluster : clusters) {
            bookLocations.add(new BookLocation(
                    UUID.randomUUID(),
                    bookId,
                    cluster.displayName(),
                    cluster.normalizedName(),
                    List.copyOf(cluster.aliases()),
                    cluster.chapterLocationIds().size(),
                    cluster.representativeChapterLocationId(),
                    cluster.firstSeenChapterId(),
                    null,
                    null
            ));
        }

        List<BookLocation> savedLocations = new ArrayList<>();
        bookLocationRepository.saveAll(bookLocations).forEach(savedLocations::add);

        for (int i = 0; i < savedLocations.size(); i++) {
            BookLocation bookLocation = savedLocations.get(i);
            LocationCluster cluster = clusters.get(i);
            bookLocationRepository.linkBookToLocation(bookId, bookLocation.id());
            bookLocationRepository.linkChapterLocationsToBookLocation(cluster.chapterLocationIds(), bookLocation.id());
        }

        return new BookLocationResolutionResult(
                bookId,
                true,
                chapterLocations.size(),
                Math.toIntExact(bookLocationRepository.countBookLocationsByBookId(bookId)),
                "Resolved book-level locations"
        );
    }

    private boolean isResolvable(ChapterLocation chapterLocation) {
        return !keysFor(chapterLocation).isEmpty();
    }

    private List<LocationCluster> clusterLocations(List<ChapterLocation> chapterLocations) {
        List<LocationCluster> clusters = new ArrayList<>();
        Map<String, Integer> clusterIndexByKey = new LinkedHashMap<>();

        for (ChapterLocation chapterLocation : chapterLocations) {
            Set<String> keys = keysFor(chapterLocation);
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
                LocationCluster cluster = LocationCluster.from(chapterLocation, keys);
                clusters.add(cluster);
                int newIndex = clusters.size() - 1;
                for (String key : cluster.keys()) {
                    clusterIndexByKey.put(key, newIndex);
                }
                continue;
            }

            int baseIndex = matchingIndexes.iterator().next();
            LocationCluster merged = clusters.get(baseIndex).add(chapterLocation, keys);
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

    private Set<String> keysFor(ChapterLocation location) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addKey(keys, location.normalizedName());
        if (location.aliases() != null) {
            for (String alias : location.aliases()) {
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
            List<UUID> chapterLocationIds,
            UUID representativeChapterLocationId,
            UUID firstSeenChapterId
    ) {
        static LocationCluster from(ChapterLocation location, Set<String> keys) {
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            if (location.aliases() != null) {
                aliases.addAll(location.aliases().stream().filter(alias -> alias != null && !alias.isBlank()).toList());
            }
            return new LocationCluster(
                    location.displayName(),
                    location.normalizedName(),
                    aliases,
                    new LinkedHashSet<>(keys),
                    new ArrayList<>(List.of(location.id())),
                    location.id(),
                    location.chapterId()
            );
        }

        LocationCluster add(ChapterLocation location, Set<String> additionalKeys) {
            LinkedHashSet<String> nextAliases = new LinkedHashSet<>(aliases);
            if (location.aliases() != null) {
                nextAliases.addAll(location.aliases().stream().filter(alias -> alias != null && !alias.isBlank()).toList());
            }
            LinkedHashSet<String> nextKeys = new LinkedHashSet<>(keys);
            nextKeys.addAll(additionalKeys);
            List<UUID> nextIds = new ArrayList<>(chapterLocationIds);
            nextIds.add(location.id());
            return new LocationCluster(displayName, normalizedName, nextAliases, nextKeys, nextIds, representativeChapterLocationId, firstSeenChapterId);
        }

        LocationCluster merge(LocationCluster other) {
            LinkedHashSet<String> nextAliases = new LinkedHashSet<>(aliases);
            nextAliases.addAll(other.aliases);
            LinkedHashSet<String> nextKeys = new LinkedHashSet<>(keys);
            nextKeys.addAll(other.keys);
            List<UUID> nextIds = new ArrayList<>(chapterLocationIds);
            nextIds.addAll(other.chapterLocationIds);
            return new LocationCluster(displayName, normalizedName, nextAliases, nextKeys, nextIds, representativeChapterLocationId, firstSeenChapterId);
        }
    }
}
