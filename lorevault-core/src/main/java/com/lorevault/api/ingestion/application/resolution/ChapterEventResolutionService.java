package com.lorevault.api.ingestion.application.resolution;

import com.lorevault.api.content.entities.ChapterEvent;
import com.lorevault.api.content.entities.ChapterEventGraphRepository;
import com.lorevault.api.ingestion.application.result.ChapterEventResolutionResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChapterEventResolutionService {

    public static final String CHAPTER_RESOLVED = "chapter-resolved";

    private final ChapterEventGraphRepository chapterEventRepository;

    @Transactional
    public ChapterEventResolutionResult resolveChapter(UUID chapterId) {
        if (chapterId == null) {
            return new ChapterEventResolutionResult(null, false, 0, 0, "Chapter ID is required");
        }

        long mentionCount = chapterEventRepository.countMentionsByChapterId(chapterId);
        if (mentionCount == 0) {
            return new ChapterEventResolutionResult(
                    chapterId,
                    false,
                    0,
                    0,
                    "No event mentions found for chapter"
            );
        }

        chapterEventRepository.deleteByChapterId(chapterId);

        List<ChapterEventGraphRepository.ChapterEventCandidateView> candidates =
                chapterEventRepository.findResolutionCandidates(chapterId);
        if (candidates.isEmpty()) {
            return new ChapterEventResolutionResult(
                    chapterId,
                    false,
                    Math.toIntExact(mentionCount),
                    0,
                    "No resolvable event mentions found for chapter"
            );
        }

        List<ChapterEvent> chapterEvents = new ArrayList<>();
        for (ChapterEventGraphRepository.ChapterEventCandidateView candidate : candidates) {
            if (candidate.getNormalizedName() == null || candidate.getNormalizedName().isBlank()) {
                continue;
            }
            String aggregateCard = buildAggregateCard(candidate);
            chapterEvents.add(new ChapterEvent(
                    UUID.randomUUID(),
                    chapterId,
                    candidate.getDisplayName(),
                    candidate.getNormalizedName(),
                    candidate.getRepresentativeEventType(),
                    safeMentionCount(candidate.getMentionCount()),
                    aggregateCard,
                    null,
                    null
            ));
        }

        if (chapterEvents.isEmpty()) {
            return new ChapterEventResolutionResult(
                    chapterId,
                    false,
                    Math.toIntExact(mentionCount),
                    0,
                    "No resolvable event mentions found for chapter"
            );
        }

        List<ChapterEvent> savedEvents = new ArrayList<>();
        chapterEventRepository.saveAll(chapterEvents).forEach(savedEvents::add);

        for (ChapterEvent chapterEvent : savedEvents) {
            chapterEventRepository.linkChapterToEvent(chapterId, chapterEvent.id());
            chapterEventRepository.linkMentionsToChapterEvent(
                    chapterId,
                    chapterEvent.normalizedName(),
                    chapterEvent.id(),
                    CHAPTER_RESOLVED
            );
        }

        return new ChapterEventResolutionResult(
                chapterId,
                true,
                Math.toIntExact(mentionCount),
                Math.toIntExact(chapterEventRepository.countChapterEventsByChapterId(chapterId)),
                "Resolved chapter event mentions"
        );
    }

    /**
     * Builds a deterministic aggregate card for a chapter-scoped event cluster.
     * The card is intended as a human-readable summary for embedding and candidate generation.
     * It is not canonical truth — it is rebuilt from evidence on every resolution pass.
     */
    private String buildAggregateCard(ChapterEventGraphRepository.ChapterEventCandidateView candidate) {
        StringBuilder card = new StringBuilder();

        card.append("## ").append(coalesce(candidate.getDisplayName(), candidate.getNormalizedName())).append("\n\n");

        if (candidate.getRepresentativeEventType() != null && !candidate.getRepresentativeEventType().isBlank()) {
            card.append("**Event type:** ").append(candidate.getRepresentativeEventType()).append("\n");
        }

        List<String> eventTypes = distinct(candidate.getEventTypes());
        if (eventTypes.size() > 1) {
            card.append("**Event type variants:** ").append(String.join(", ", eventTypes)).append("\n");
        }

        long count = candidate.getMentionCount() != null ? candidate.getMentionCount() : 0;
        card.append("**Mention count:** ").append(count).append("\n");

        List<String> relations = distinct(candidate.getSceneRelativeRelations());
        if (!relations.isEmpty()) {
            card.append("**Scene-relative relations:** ").append(String.join(", ", relations)).append("\n");
        }

        List<String> snippets = safeList(candidate.getEvidenceSnippets());
        if (!snippets.isEmpty()) {
            card.append("\n**Evidence:**\n");
            for (String snippet : snippets) {
                if (snippet != null && !snippet.isBlank()) {
                    card.append("- ").append(snippet.trim()).append("\n");
                }
            }
        }

        return card.toString().trim();
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

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }

    private String coalesce(String first, String second) {
        return (first != null && !first.isBlank()) ? first : second;
    }

    private int safeMentionCount(Long mentionCount) {
        if (mentionCount == null) {
            return 0;
        }
        return Math.toIntExact(mentionCount);
    }
}
