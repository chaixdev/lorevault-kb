package com.lorevault.api.graph.event.consolidation.book;

import com.lorevault.api.ai.llm.EventMergeModels;
import com.lorevault.api.graph.event.persistence.BookEvent;
import com.lorevault.api.graph.event.persistence.ChapterEvent;
import com.lorevault.api.graph.event.persistence.ChapterEventGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BookEventConsolidationService {

    private final BookEventPersistenceService persistenceService;
    private final ChapterEventGraphRepository chapterEventRepository;

    public BookEventConsolidationService(
            BookEventPersistenceService persistenceService,
            ChapterEventGraphRepository chapterEventRepository
    ) {
        this.persistenceService = persistenceService;
        this.chapterEventRepository = chapterEventRepository;
    }

    public BookEventConsolidationResult reduceAndPersist(
            StageExecutionContext ctx,
            UUID jobId,
            UUID chapterId,
            UUID bookId,
            List<ChapterEvent> chapterEvents,
            List<EventMergeModels.EventMergeDecision> mergeDecisions
    ) {
        if (chapterEvents == null || chapterEvents.isEmpty()) {
            log.info("[BOOK_EVENT] No ChapterEvents to reduce: jobId={}, chapterId={}, bookId={}", jobId, chapterId, bookId);
            return new BookEventConsolidationResult(0, 0);
        }

        Map<UUID, ChapterEvent> eventsById = new HashMap<>();
        for (ChapterEvent chapterEvent : chapterEvents) {
            if (chapterEvent == null || chapterEvent.id() == null) {
                continue;
            }
            eventsById.put(chapterEvent.id(), chapterEvent);
        }

        List<UUID> currentChapterEventIds = chapterEvents.stream()
                .filter(chapterEvent -> chapterEvent != null && chapterId.equals(chapterEvent.chapterId()))
                .map(ChapterEvent::id)
                .filter(id -> id != null)
                .toList();

        List<UUID> mergeEndpointIds = mergeDecisions == null ? List.of() : mergeDecisions.stream()
                .filter(decision -> decision != null)
                .flatMap(decision -> java.util.stream.Stream.of(decision.eventId1(), decision.eventId2()))
                .filter(id -> id != null && !currentChapterEventIds.contains(id))
                .distinct()
                .toList();

        LinkedHashSet<UUID> rewriteSeedIds = new LinkedHashSet<>();
        rewriteSeedIds.addAll(currentChapterEventIds);
        rewriteSeedIds.addAll(mergeEndpointIds);

        List<UUID> rewriteScope = persistenceService.expandRewriteScope(List.copyOf(rewriteSeedIds));
        Map<UUID, ChapterEvent> rewriteEventsById = loadRewriteEventsById(rewriteScope, eventsById);

        DisjointSet disjointSet = new DisjointSet(rewriteEventsById.keySet());
        if (mergeDecisions != null) {
            for (EventMergeModels.EventMergeDecision decision : mergeDecisions) {
                if (decision == null || decision.eventId1() == null || decision.eventId2() == null) {
                    continue;
                }
                if (!rewriteEventsById.containsKey(decision.eventId1()) || !rewriteEventsById.containsKey(decision.eventId2())) {
                    continue;
                }
                disjointSet.union(decision.eventId1(), decision.eventId2());
            }
        }

        Map<UUID, List<ChapterEvent>> clustersByRoot = new HashMap<>();
        for (ChapterEvent chapterEvent : rewriteEventsById.values()) {
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
                    ctx.stageId(),
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

        List<UUID> scopedChapterEventIds = rewriteEventsById.keySet().stream().toList();

        BookEventPersistenceService.BookEventWriteSummary summary = persistenceService.saveAndLinkBookEvents(
                bookId,
                chapterId,
                jobId,
                bookEventsToCreate,
                chapterEventIdsByBookEvent,
                scopedChapterEventIds
        );

        log.info(
                "[BOOK_EVENT] Consolidation completed: jobId={}, chapterId={}, bookId={}, chapterEventCount={}, mergeDecisionCount={}, clusters={}, bookEventsCreated={}, referenceLinksWritten={}",
                jobId,
                chapterId,
                bookId,
                chapterEvents.size(),
                mergeDecisions == null ? 0 : mergeDecisions.size(),
                clustersByRoot.size(),
                summary.bookEventsCreated(),
                summary.referenceLinksWritten()
        );

        return new BookEventConsolidationResult(summary.bookEventsCreated(), summary.referenceLinksWritten());
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

    private Map<UUID, ChapterEvent> loadRewriteEventsById(
            List<UUID> rewriteScope,
            Map<UUID, ChapterEvent> knownEventsById
    ) {
        Map<UUID, ChapterEvent> rewriteEventsById = new HashMap<>();
        List<UUID> missingIds = new ArrayList<>();

        for (UUID rewriteEventId : rewriteScope) {
            ChapterEvent knownEvent = knownEventsById.get(rewriteEventId);
            if (knownEvent != null) {
                rewriteEventsById.put(rewriteEventId, knownEvent);
            } else {
                missingIds.add(rewriteEventId);
            }
        }

        if (!missingIds.isEmpty()) {
            for (ChapterEvent event : chapterEventRepository.findByIds(missingIds)) {
                if (event != null && event.id() != null) {
                    rewriteEventsById.put(event.id(), event);
                }
            }
        }

        return rewriteEventsById;
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

    public record BookEventConsolidationResult(
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
