package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.content.association.ChapterEvent;
import com.lorevault.api.content.association.ChapterEventGraphRepository;
import com.lorevault.api.content.mention.EventMention;
import com.lorevault.api.content.mention.EventMentionComponentLookup;
import com.lorevault.api.content.mention.EventMentionGraphRepository;

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
public class ChapterEventConsolidationService {

    public static final String CHAPTER_CONSOLIDATED = "chapter-consolidated";

    private final ChapterEventGraphRepository chapterEventRepository;
    private final EventMentionGraphRepository mentionRepository;
    private final EventMentionComponentLookup componentLookup;

    @Transactional
    public ChapterEventConsolidationResult consolidateChapter(UUID chapterId) {
        if (chapterId == null) {
            return new ChapterEventConsolidationResult(null, false, 0, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterEventRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            return new ChapterEventConsolidationResult(
                    chapterId, false, 0, 0, 0, "No event mentions found for chapter");
        }

        // Idempotent rebuild: remove previous ChapterEvent nodes + REFERS_TO edges
        chapterEventRepository.deleteByChapterId(chapterId);

        // Derive connected components from SAME_EVENT links
        List<EventMentionComponentLookup.SameEventComponentRow> componentRows =
                componentLookup.findSameEventComponents(chapterId);

        // Group mention ids by their component representative id
        Map<String, List<String>> componentMap = componentRows.stream()
                .collect(Collectors.groupingBy(
                        EventMentionComponentLookup.SameEventComponentRow::componentId,
                        Collectors.mapping(
                                EventMentionComponentLookup.SameEventComponentRow::mentionId,
                                Collectors.toList()
                        )
                ));

        if (componentMap.isEmpty()) {
            return new ChapterEventConsolidationResult(
                    chapterId, false, Math.toIntExact(mentionCount), 0, 0,
                    "No resolvable event mentions found for chapter");
        }

        // Load all mentions in bulk for aggregate card construction
        List<EventMention> allMentions = mentionRepository.findByChapterIdOrdered(chapterId);
        Map<String, EventMention> mentionsById = allMentions.stream()
                .collect(Collectors.toMap(m -> m.id().toString(), m -> m));

        List<ChapterEvent> chapterEvents = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : componentMap.entrySet()) {
            String componentId = entry.getKey();
            List<String> componentMentionIds = entry.getValue();
            List<EventMention> componentMentions = componentMentionIds.stream()
                    .map(mentionsById::get)
                    .filter(Objects::nonNull)
                    .toList();

            if (componentMentions.isEmpty()) {
                continue;
            }

            ChapterEvent chapterEvent = buildChapterEvent(chapterId, componentId, componentMentions);
            chapterEvents.add(chapterEvent);
        }

        if (chapterEvents.isEmpty()) {
            return new ChapterEventConsolidationResult(
                    chapterId, false, Math.toIntExact(mentionCount), 0, 0,
                    "No resolvable event mentions found for chapter");
        }

        // Persist all ChapterEvent nodes, then link chapter + mentions
        // Build a stable lookup by componentId — do NOT rely on saveAll order
        List<ChapterEvent> savedEvents = new ArrayList<>();
        chapterEventRepository.saveAll(chapterEvents).forEach(savedEvents::add);

        Map<String, ChapterEvent> savedByComponentId = savedEvents.stream()
                .filter(e -> e.componentId() != null)
                .collect(Collectors.toMap(ChapterEvent::componentId, e -> e));

        for (Map.Entry<String, List<String>> entry : componentMap.entrySet()) {
            String componentId = entry.getKey();
            ChapterEvent saved = savedByComponentId.get(componentId);
            if (saved == null) {
                log.warn("[CHAPTER_EVENT_AGGREGATION] No saved ChapterEvent for componentId={}, skipping links", componentId);
                continue;
            }

            chapterEventRepository.linkChapterToEvent(chapterId, saved.id());

            for (String mentionId : entry.getValue()) {
                try {
                    chapterEventRepository.linkMentionToChapterEvent(
                            UUID.fromString(mentionId), saved.id(), CHAPTER_CONSOLIDATED);
                } catch (IllegalArgumentException e) {
                    log.warn("[CHAPTER_EVENT_AGGREGATION] Invalid mention UUID, skipping link: mentionId={}", mentionId);
                }
            }
        }

        long createdCount = chapterEventRepository.countChapterEventsByChapterId(chapterId);
        log.info("[CHAPTER_EVENT_AGGREGATION] Resolved: chapterId={}, mentions={}, chapterEvents={}",
                chapterId, mentionCount, createdCount);

        return new ChapterEventConsolidationResult(
                chapterId, true, Math.toIntExact(mentionCount), Math.toIntExact(createdCount), 0,
                "Resolved chapter event mentions from co-reference chains");
    }

    /**
     * Builds a {@link ChapterEvent} from all mentions in a connected component.
     * The canonical label is the most-frequent displayName in the component.
     * The aggregate card is derived from all mention fields, not a single representative.
     *
     * @param componentId the stable co-reference component representative ID (used as lookup key post-save)
     */
    private ChapterEvent buildChapterEvent(UUID chapterId, String componentId, List<EventMention> mentions) {
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

        List<String> supportedAliases = collectSupportedAliases(mentions, displayName, normalizedName);
        List<String> supportedEventTypes = distinct(mentions.stream()
                .map(EventMention::eventType)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .sorted()
                .toList());
        List<String> identityEvidence = collectIdentityEvidence(mentions);

        String aggregateCard = buildAggregateCard(
                displayName,
                normalizedName,
                representativeEventType,
                supportedEventTypes,
                mentions);

        return new ChapterEvent(
                UUID.randomUUID(),
                chapterId,
                componentId,
                displayName,
                normalizedName,
                representativeEventType,
                mentions.size(),
                aggregateCard,
                supportedAliases,
                supportedEventTypes,
                identityEvidence,
                null,
                null,
                null,
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
            List<String> supportedEventTypes,
            List<EventMention> mentions
    ) {
        StringBuilder card = new StringBuilder();

        String heading = coalesce(displayName, normalizedName);
        card.append("## ").append(heading != null ? heading : "Unnamed Event").append("\n\n");

        if (representativeEventType != null && !representativeEventType.isBlank()) {
            card.append("**Event type:** ").append(representativeEventType).append("\n");
        }

        if (supportedEventTypes.size() > 1) {
            card.append("**Supported event type variants:** ").append(String.join(", ", supportedEventTypes)).append("\n");
        }

        card.append("**Mention count:** ").append(mentions.size()).append("\n");

        Map<String, Long> sceneRelativeDistribution = mentions.stream()
                .map(EventMention::sceneRelativeRelation)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        if (!sceneRelativeDistribution.isEmpty()) {
            card.append("\n**Scene-relative relation distribution:**\n");
            sceneRelativeDistribution.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> card.append("- ")
                            .append(entry.getKey())
                            .append(": ")
                            .append(entry.getValue())
                            .append("\n"));
        }

        List<String> descriptions = mentions.stream()
                .map(EventMention::description)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(4)
                .toList();

        if (!descriptions.isEmpty()) {
            card.append("\n**Descriptions:**\n");
            for (String description : descriptions) {
                card.append("- ").append(description).append("\n");
            }
        }

        return card.toString().trim();
    }

    private List<String> collectSupportedAliases(List<EventMention> mentions, String displayName, String normalizedName) {
        List<String> aliases = new ArrayList<>();
        if (displayName != null && !displayName.isBlank()) {
            aliases.add(displayName.trim());
        }
        if (normalizedName != null && !normalizedName.isBlank()) {
            aliases.add(normalizedName.trim());
        }
        mentions.stream()
                .flatMap(mention -> mention.aliases() == null ? java.util.stream.Stream.empty() : mention.aliases().stream())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(aliases::add);

        return aliases.stream()
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> collectIdentityEvidence(List<EventMention> mentions) {
        return mentions.stream()
                .map(EventMention::evidence)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted()
                .limit(4)
                .toList();
    }

    /** Returns the most frequently occurring non-null value, or null if list is empty. */
    private String mostFrequent(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream()
                .sorted(
                        Map.Entry.<String, Long>comparingByValue().reversed()
                                .thenComparing(Map.Entry.comparingByKey())
                )
                .map(Map.Entry::getKey)
                .findFirst()
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
