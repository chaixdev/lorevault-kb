package com.lorevault.api.ingestion.application.resolution;

import com.lorevault.api.content.entities.ChapterEvent;
import com.lorevault.api.content.entities.ChapterEventGraphRepository;
import com.lorevault.api.content.entities.EventMention;
import com.lorevault.api.content.entities.EventMentionGraphRepository;
import com.lorevault.api.ingestion.application.result.ChapterEventResolutionResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage 3 — ChapterEvent Aggregation.
 *
 * <p>Reads connected components formed by {@code SAME_EVENT} links (produced by Stage 2),
 * materialises one {@link ChapterEvent} per component, and links all component mentions
 * to it via {@code REFERS_TO}. Singletons (unlinked mentions) also become their own
 * {@code ChapterEvent} so every {@code EventMention} is always resolved.</p>
 *
 * <p>The pass is idempotent: existing {@code ChapterEvent} nodes for the chapter are
 * deleted before new ones are written.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapterEventResolutionService {

    public static final String CHAPTER_RESOLVED = "chapter-resolved";

    private final ChapterEventGraphRepository chapterEventRepository;
    private final EventMentionGraphRepository mentionRepository;

    @Transactional
    public ChapterEventResolutionResult resolveChapter(UUID chapterId) {
        if (chapterId == null) {
            return new ChapterEventResolutionResult(null, false, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterEventRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            return new ChapterEventResolutionResult(
                    chapterId, false, 0, 0, "No event mentions found for chapter");
        }

        // Idempotent rebuild: remove previous ChapterEvent nodes + REFERS_TO edges
        chapterEventRepository.deleteByChapterId(chapterId);

        // Derive connected components from SAME_EVENT links
        List<EventMentionGraphRepository.SameEventComponentRow> componentRows =
                mentionRepository.findSameEventComponents(chapterId);

        // Group mention ids by their component representative id
        Map<String, List<String>> componentMap = componentRows.stream()
                .collect(Collectors.groupingBy(
                        EventMentionGraphRepository.SameEventComponentRow::getComponentId,
                        Collectors.mapping(
                                EventMentionGraphRepository.SameEventComponentRow::getMentionId,
                                Collectors.toList()
                        )
                ));

        if (componentMap.isEmpty()) {
            return new ChapterEventResolutionResult(
                    chapterId, false, Math.toIntExact(mentionCount), 0,
                    "No resolvable event mentions found for chapter");
        }

        // Load all mentions in bulk for aggregate card construction
        List<EventMention> allMentions = mentionRepository.findByChapterIdOrdered(chapterId);
        Map<String, EventMention> mentionsById = allMentions.stream()
                .collect(Collectors.toMap(m -> m.id().toString(), m -> m));

        List<ChapterEvent> chapterEvents = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : componentMap.entrySet()) {
            List<String> componentMentionIds = entry.getValue();
            List<EventMention> componentMentions = componentMentionIds.stream()
                    .map(mentionsById::get)
                    .filter(Objects::nonNull)
                    .toList();

            if (componentMentions.isEmpty()) {
                continue;
            }

            ChapterEvent chapterEvent = buildChapterEvent(chapterId, componentMentions);
            chapterEvents.add(chapterEvent);
        }

        if (chapterEvents.isEmpty()) {
            return new ChapterEventResolutionResult(
                    chapterId, false, Math.toIntExact(mentionCount), 0,
                    "No resolvable event mentions found for chapter");
        }

        // Persist all ChapterEvent nodes, then link chapter + mentions
        List<ChapterEvent> savedEvents = new ArrayList<>();
        chapterEventRepository.saveAll(chapterEvents).forEach(savedEvents::add);

        // Build reverse lookup: componentId -> ChapterEvent id
        // We rely on order preservation: savedEvents mirrors chapterEvents order
        List<Map.Entry<String, List<String>>> componentEntries = new ArrayList<>(componentMap.entrySet());
        for (int i = 0; i < savedEvents.size(); i++) {
            ChapterEvent saved = savedEvents.get(i);
            chapterEventRepository.linkChapterToEvent(chapterId, saved.id());

            List<String> memberIds = componentEntries.get(i).getValue();
            for (String mentionId : memberIds) {
                try {
                    chapterEventRepository.linkMentionToChapterEvent(
                            UUID.fromString(mentionId), saved.id(), CHAPTER_RESOLVED);
                } catch (IllegalArgumentException e) {
                    log.warn("[CHAPTER_EVENT_AGGREGATION] Invalid mention UUID, skipping link: mentionId={}", mentionId);
                }
            }
        }

        long createdCount = chapterEventRepository.countChapterEventsByChapterId(chapterId);
        log.info("[CHAPTER_EVENT_AGGREGATION] Resolved: chapterId={}, mentions={}, chapterEvents={}",
                chapterId, mentionCount, createdCount);

        return new ChapterEventResolutionResult(
                chapterId, true, Math.toIntExact(mentionCount), Math.toIntExact(createdCount),
                "Resolved chapter event mentions from co-reference chains");
    }

    /**
     * Builds a {@link ChapterEvent} from all mentions in a connected component.
     * The canonical label is the most-frequent displayName in the component.
     * The aggregate card is derived from all mention fields, not a single representative.
     */
    private ChapterEvent buildChapterEvent(UUID chapterId, List<EventMention> mentions) {
        String displayName = mostFrequent(mentions.stream()
                .map(EventMention::displayName)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .toList());

        String normalizedName = mostFrequent(mentions.stream()
                .map(EventMention::normalizedName)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .toList());

        String representativeEventType = mostFrequent(mentions.stream()
                .map(EventMention::eventType)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .toList());

        String aggregateCard = buildAggregateCard(displayName, normalizedName, representativeEventType, mentions);

        return new ChapterEvent(
                UUID.randomUUID(),
                chapterId,
                displayName,
                normalizedName,
                representativeEventType,
                mentions.size(),
                aggregateCard,
                null,
                null
        );
    }

    /**
     * Builds a deterministic aggregate card from all mentions in the component.
     * The card is a human-readable Markdown summary for embedding and candidate generation.
     * It is not canonical truth — it is rebuilt from evidence on every resolution pass.
     */
    private String buildAggregateCard(
            String displayName,
            String normalizedName,
            String representativeEventType,
            List<EventMention> mentions
    ) {
        StringBuilder card = new StringBuilder();

        String heading = coalesce(displayName, normalizedName);
        card.append("## ").append(heading != null ? heading : "Unnamed Event").append("\n\n");

        if (representativeEventType != null && !representativeEventType.isBlank()) {
            card.append("**Event type:** ").append(representativeEventType).append("\n");
        }

        List<String> eventTypes = distinct(mentions.stream().map(EventMention::eventType).toList());
        if (eventTypes.size() > 1) {
            card.append("**Event type variants:** ").append(String.join(", ", eventTypes)).append("\n");
        }

        card.append("**Mention count:** ").append(mentions.size()).append("\n");

        List<String> relations = distinct(mentions.stream().map(EventMention::sceneRelativeRelation).toList());
        if (!relations.isEmpty()) {
            card.append("**Scene-relative relations:** ").append(String.join(", ", relations)).append("\n");
        }

        List<String> snippets = mentions.stream()
                .map(EventMention::evidence)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .limit(4)
                .toList();

        if (!snippets.isEmpty()) {
            card.append("\n**Evidence:**\n");
            for (String snippet : snippets) {
                card.append("- ").append(snippet.trim()).append("\n");
            }
        }

        return card.toString().trim();
    }

    /** Returns the most frequently occurring non-null value, or null if list is empty. */
    private String mostFrequent(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private List<String> distinct(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));
    }

    private String coalesce(String first, String second) {
        return (first != null && !first.isBlank()) ? first : second;
    }
}
