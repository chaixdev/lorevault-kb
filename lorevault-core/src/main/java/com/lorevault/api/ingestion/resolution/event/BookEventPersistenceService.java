package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.content.association.BookEvent;
import com.lorevault.api.content.association.BookEventGraphRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookEventPersistenceService {

    private final BookEventGraphRepository bookEventRepository;
    private final Neo4jClient neo4jClient;

    public BookEventPersistenceService(BookEventGraphRepository bookEventRepository, Neo4jClient neo4jClient) {
        this.bookEventRepository = bookEventRepository;
        this.neo4jClient = neo4jClient;
    }

    @Transactional
    public BookEventWriteSummary saveAndLinkBookEvents(
            UUID chapterId,
            UUID jobId,
            List<BookEvent> bookEvents,
            List<List<UUID>> chapterEventIdsByBookEvent,
            List<UUID> scopedChapterEventIds
    ) {
        clearExistingBookEventLinks(scopedChapterEventIds);

        if (bookEvents == null || bookEvents.isEmpty()) {
            return new BookEventWriteSummary(0, 0);
        }

        List<BookEvent> savedBookEvents = new ArrayList<>(bookEventRepository.saveAll(bookEvents));
        int writtenLinks = 0;

        for (int i = 0; i < savedBookEvents.size(); i++) {
            BookEvent bookEvent = savedBookEvents.get(i);
            List<UUID> chapterEventIds = chapterEventIdsByBookEvent.get(i);
            writtenLinks += linkChapterEventsToBookEvent(chapterEventIds, bookEvent.id());
        }

        return new BookEventWriteSummary(savedBookEvents.size(), writtenLinks);
    }

    @Transactional(readOnly = true)
    public List<UUID> expandRewriteScope(List<UUID> scopedChapterEventIds) {
        if (scopedChapterEventIds == null || scopedChapterEventIds.isEmpty()) {
            return List.of();
        }

        List<String> scopedIds = scopedChapterEventIds.stream().map(UUID::toString).toList();
        Object rawIds = neo4jClient.query("""
                MATCH (ce:ChapterEvent)
                WHERE ce.id IN $chapterEventIds
                OPTIONAL MATCH (ce)-[:REFERS_TO]->(be:BookEvent)<-[:REFERS_TO]-(linked:ChapterEvent)
                WITH collect(DISTINCT be) AS touchedBookEvents
                UNWIND touchedBookEvents AS be
                MATCH (linked:ChapterEvent)-[:REFERS_TO]->(be)
                RETURN collect(DISTINCT linked.id) AS chapterEventIds
                """)
                .bind(scopedIds).to("chapterEventIds")
                .fetch()
                .one()
                .map(row -> row.get("chapterEventIds"))
                .orElse(List.of());

        LinkedHashSet<UUID> expanded = new LinkedHashSet<>(scopedChapterEventIds);
        if (rawIds instanceof List<?> linkedIds) {
            for (Object linkedId : linkedIds) {
                UUID uuid = toUuid(linkedId);
                if (uuid != null) {
                    expanded.add(uuid);
                }
            }
        }
        return List.copyOf(expanded);
    }

    private void clearExistingBookEventLinks(List<UUID> scopedChapterEventIds) {
        if (scopedChapterEventIds == null || scopedChapterEventIds.isEmpty()) {
            return;
        }

        List<String> scopedIds = scopedChapterEventIds.stream().map(UUID::toString).toList();

        neo4jClient.query("""
                UNWIND $chapterEventIds AS chapterEventId
                MATCH (ce:ChapterEvent {id: chapterEventId})-[r:REFERS_TO]->(be:BookEvent)
                DELETE r
                WITH DISTINCT be
                WHERE NOT EXISTS { MATCH (:ChapterEvent)-[:REFERS_TO]->(be) }
                DETACH DELETE be
                """)
                .bind(scopedIds).to("chapterEventIds")
                .run();
    }

    private int linkChapterEventsToBookEvent(List<UUID> chapterEventIds, UUID bookEventId) {
        if (chapterEventIds == null || chapterEventIds.isEmpty() || bookEventId == null) {
            return 0;
        }

        List<String> ids = chapterEventIds.stream().map(UUID::toString).toList();

        Object rawCount = neo4jClient.query("""
                UNWIND $chapterEventIds AS chapterEventId
                MATCH (ce:ChapterEvent {id: chapterEventId})
                MATCH (be:BookEvent {id: $bookEventId})
                MERGE (ce)-[:REFERS_TO]->(be)
                RETURN count(*) AS linkCount
                """)
                .bind(ids).to("chapterEventIds")
                .bind(bookEventId.toString()).to("bookEventId")
                .fetch()
                .one()
                .map(row -> row.get("linkCount"))
                .orElse(0);

        if (rawCount instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    public record BookEventWriteSummary(
            int bookEventsCreated,
            int referenceLinksWritten
    ) {}
}
