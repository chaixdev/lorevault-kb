package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.ai.llm.EventMergeModels;
import com.lorevault.api.content.association.BookEvent;
import com.lorevault.api.content.association.ChapterEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BookEventReductionService {

    private static final Logger log = LoggerFactory.getLogger(BookEventReductionService.class);

    private final BookEventPersistenceService persistenceService;

    public BookEventReductionService(BookEventPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public BookEventReductionResult reduceAndPersist(
            UUID jobId,
            UUID chapterId,
            UUID bookId,
            List<ChapterEvent> chapterEvents,
            List<EventMergeModels.EventMergeDecision> mergeDecisions
    ) {
        if (chapterEvents == null || chapterEvents.isEmpty()) {
            log.info("[BOOK_EVENT] No ChapterEvents to reduce: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);
            return new BookEventReductionResult(0, 0);
        }

        Map<UUID, ChapterEvent> eventsById = new HashMap<>();
        for (ChapterEvent chapterEvent : chapterEvents) {
            if (chapterEvent == null || chapterEvent.id() == null) {
                continue;
            }
            eventsById.put(chapterEvent.id(), chapterEvent);
        }

        DisjointSet disjointSet = new DisjointSet(eventsById.keySet());
        if (mergeDecisions != null) {
            for (EventMergeModels.EventMergeDecision decision : mergeDecisions) {
                if (decision == null || decision.eventId1() == null || decision.eventId2() == null) {
                    continue;
                }
                if (!eventsById.containsKey(decision.eventId1()) || !eventsById.containsKey(decision.eventId2())) {
                    continue;
                }
                disjointSet.union(decision.eventId1(), decision.eventId2());
            }
        }

        Map<UUID, List<ChapterEvent>> clustersByRoot = new HashMap<>();
        for (ChapterEvent chapterEvent : chapterEvents) {
            if (chapterEvent == null || chapterEvent.id() == null) {
                continue;
            }
            UUID root = disjointSet.find(chapterEvent.id());
            clustersByRoot.computeIfAbsent(root, ignored -> new ArrayList<>()).add(chapterEvent);
        }

        List<BookEvent> bookEventsToCreate = new ArrayList<>();
        List<List<UUID>> chapterEventIdsByBookEvent = new ArrayList<>();
        for (List<ChapterEvent> cluster : clustersByRoot.values()) {
            if (cluster == null || cluster.isEmpty()) {
                continue;
            }

            ChapterEvent representative = chooseRepresentative(cluster);
            bookEventsToCreate.add(new BookEvent(
                    UUID.randomUUID(),
                    bookId,
                    representative.displayName(),
                    representative.normalizedName(),
                    representative.representativeEventType(),
                    null,
                    null
            ));

            List<UUID> chapterEventIds = cluster.stream()
                    .map(ChapterEvent::id)
                    .filter(id -> id != null)
                    .toList();
            chapterEventIdsByBookEvent.add(chapterEventIds);
        }

        List<UUID> scopedChapterEventIds = chapterEvents.stream()
                .map(ChapterEvent::id)
                .filter(id -> id != null)
                .toList();

        BookEventPersistenceService.BookEventWriteSummary summary = persistenceService.saveAndLinkBookEvents(
                chapterId,
                jobId,
                bookEventsToCreate,
                chapterEventIdsByBookEvent,
                scopedChapterEventIds
        );

        log.info(
                "[BOOK_EVENT] Reduction completed: jobId={}, chapterId={}, bookId={}, chapterEventCount={}, mergeDecisionCount={}, clusters={}, bookEventsCreated={}, referenceLinksWritten={}",
                jobId,
                chapterId,
                bookId,
                chapterEvents.size(),
                mergeDecisions == null ? 0 : mergeDecisions.size(),
                clustersByRoot.size(),
                summary.bookEventsCreated(),
                summary.referenceLinksWritten()
        );

        return new BookEventReductionResult(summary.bookEventsCreated(), summary.referenceLinksWritten());
    }

    private ChapterEvent chooseRepresentative(List<ChapterEvent> cluster) {
        return cluster.stream()
                .sorted(
                        Comparator
                                .comparing((ChapterEvent event) -> mentionCountOrZero(event.mentionCount()), Comparator.reverseOrder())
                                .thenComparing(event -> safeLower(event.normalizedName()))
                                .thenComparing(event -> safeLower(event.displayName()))
                )
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cluster must contain at least one ChapterEvent"));
    }

    private int mentionCountOrZero(Integer mentionCount) {
        return mentionCount == null ? 0 : mentionCount;
    }

    private String safeLower(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase();
    }

    public record BookEventReductionResult(
            int bookEventsCreated,
            int referenceLinksWritten
    ) {}

    private static final class DisjointSet {

        private final Map<UUID, UUID> parent;

        private DisjointSet(Set<UUID> ids) {
            this.parent = new HashMap<>();
            for (UUID id : ids) {
                this.parent.put(id, id);
            }
        }

        private UUID find(UUID id) {
            UUID current = parent.get(id);
            if (current == null) {
                parent.put(id, id);
                return id;
            }
            if (current.equals(id)) {
                return id;
            }
            UUID root = find(current);
            parent.put(id, root);
            return root;
        }

        private void union(UUID first, UUID second) {
            UUID rootFirst = find(first);
            UUID rootSecond = find(second);
            if (rootFirst.equals(rootSecond)) {
                return;
            }

            UUID lower = rootFirst.toString().compareTo(rootSecond.toString()) <= 0 ? rootFirst : rootSecond;
            UUID higher = lower.equals(rootFirst) ? rootSecond : rootFirst;
            parent.put(higher, lower);
        }
    }
}
