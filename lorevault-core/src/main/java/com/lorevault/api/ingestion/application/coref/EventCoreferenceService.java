package com.lorevault.api.ingestion.application.coref;

import com.lorevault.api.ai.infrastructure.LlmClient;
import com.lorevault.api.ai.infrastructure.PromptRepository;
import com.lorevault.api.content.entities.EventMention;
import com.lorevault.api.content.entities.EventMentionGraphRepository;
import com.lorevault.api.ingestion.domain.IngestionFailure;
import com.lorevault.api.ingestion.domain.IngestionFailureCarrier;
import com.lorevault.api.ingestion.domain.coref.EventCorefModels;
import jakarta.annotation.Nullable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage 2 — Event Co-reference Pass.
 *
 * <p>Accepts an ordered list of scene IDs for a chapter (from {@code ScenesDetectedEvent}),
 * drives a rolling window of 3 scenes over them, and for each window calls the LLM with all
 * {@code EventMention} nodes grouped by scene to judge which mentions refer to the same event.
 * Writes {@code SAME_EVENT} relationship edges for confident matches.</p>
 *
 * <p>The pass is idempotent: existing {@code SAME_EVENT} links for the chapter are deleted
 * before new ones are written.</p>
 */
@Service
public class EventCoreferenceService {

    private static final Logger log = LoggerFactory.getLogger(EventCoreferenceService.class);

    /** Minimum model confidence required to write a SAME_EVENT link. */
    static final double CONFIDENCE_THRESHOLD = 0.75;

    /** Number of scenes per rolling window. */
    static final int WINDOW_SIZE = 3;

    static final String COREF_SOURCE = "EVENT_COREF_LLM";

    private final EventMentionGraphRepository mentionRepo;
    private final LlmClient llmClient;
    private final PromptRepository promptRepository;

    /**
     * Self-reference injected lazily so transactional write method is invoked through
     * the Spring proxy rather than direct self-invocation.
     */
    @Lazy
    @Autowired
    private EventCoreferenceService self;

    public EventCoreferenceService(
            EventMentionGraphRepository mentionRepo,
            LlmClient llmClient,
            PromptRepository promptRepository
    ) {
        this.mentionRepo = mentionRepo;
        this.llmClient = llmClient;
        this.promptRepository = promptRepository;
    }

    public EventCorefModels.CorefPassResult runCorefPass(List<UUID> orderedSceneIds, UUID chapterId, UUID jobId) {
        String modelId = llmClient.getEventCorefModelId();
        if (orderedSceneIds == null || orderedSceneIds.isEmpty()) {
            return new EventCorefModels.CorefPassResult(chapterId, UUID.randomUUID().toString(), modelId, 0, 0, 0);
        }

        List<List<UUID>> windows = buildSceneWindows(orderedSceneIds);
        PromptTemplate userTemplate = promptRepository.get("event-coref-user");

        Map<AbstractMap.SimpleEntry<UUID, UUID>, Double> bestConfidenceByPair = new HashMap<>();
        int failureCount = 0;
        List<List<UUID>> failedWindows = new ArrayList<>();

        for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
            List<UUID> windowSceneIds = windows.get(windowIndex);
            List<EventMention> windowMentions = mentionRepo.findMentionsBySceneIds(
                    windowSceneIds.stream().map(UUID::toString).toList()
            );

            if (windowMentions.size() < 2) {
                continue;
            }

            Map<UUID, List<EventMention>> mentionsByScene = groupMentionsBySceneOrdered(windowMentions);
            Set<UUID> mentionAllowlist = windowMentions.stream().map(EventMention::id).collect(Collectors.toSet());

            String userPrompt = renderWindowPrompt(userTemplate, chapterId, windowSceneIds, mentionsByScene);

            try {
                EventCorefModels.CorefWindowResponse response = llmClient.runEventCoref(jobId, userPrompt);
                ingestWindowResponse(response, mentionAllowlist, bestConfidenceByPair);
            } catch (RuntimeException ex) {
                failureCount++;
                failedWindows.add(windowSceneIds);
                log.warn("[EVENT_COREF] Window failed: chapterId={}, jobId={}, windowIndex={}, windowScenes={}, error={}",
                        chapterId, jobId, windowIndex, windowSceneIds, safeMessage(ex));
                log.debug("[EVENT_COREF] Window failure details: chapterId={}, jobId={}, windowIndex={}",
                        chapterId, jobId, windowIndex, ex);
            }
        }

        if (failureCount == windows.size()) {
            throw new EventCoreferenceException(IngestionFailure.builder(
                    "EVENT_COREF_ALL_WINDOWS_FAILED",
                    "All event co-reference windows failed"
            )
                    .stage("EVENT_COREF")
                    .detail("chapterId", chapterId)
                    .detail("jobId", jobId)
                    .detail("windowCount", windows.size())
                    .build());
        }

        if (failureCount > 0) {
            log.warn(
                    "[EVENT_COREF] Partial window failure: chapterId={}, jobId={}, failedWindowCount={}, totalWindowCount={}, failedWindowScenes={}",
                    chapterId,
                    jobId,
                    failureCount,
                    windows.size(),
                    failedWindows.stream().map(this::formatWindowRange).toList()
            );
        }

        String passId = UUID.randomUUID().toString();
        EventCoreferenceService writer = self != null ? self : this;
        writer.writeCoreferenceLinksTransactional(chapterId, passId, modelId, bestConfidenceByPair);

        return new EventCorefModels.CorefPassResult(
                chapterId,
                passId,
                modelId,
                windows.size(),
                bestConfidenceByPair.size(),
                failureCount
        );
    }

    public static List<List<UUID>> buildSceneWindows(List<UUID> orderedSceneIds) {
        if (orderedSceneIds == null || orderedSceneIds.isEmpty()) {
            return List.of();
        }

        if (orderedSceneIds.size() <= WINDOW_SIZE) {
            return List.of(List.copyOf(orderedSceneIds));
        }

        List<List<UUID>> windows = new ArrayList<>();
        for (int start = 0; start <= orderedSceneIds.size() - WINDOW_SIZE; start++) {
            windows.add(List.copyOf(orderedSceneIds.subList(start, start + WINDOW_SIZE)));
        }
        return windows;
    }

    private Map<UUID, List<EventMention>> groupMentionsBySceneOrdered(List<EventMention> mentions) {
        return mentions.stream()
                .filter(m -> m.sceneId() != null)
                .collect(Collectors.groupingBy(
                        EventMention::sceneId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                perScene -> perScene.stream()
                                        .sorted(Comparator.comparing(EventMention::extractionIndex, Comparator.nullsLast(Integer::compareTo)))
                                        .toList()
                        )
                ));
    }

    private String renderWindowPrompt(
            PromptTemplate userTemplate,
            UUID chapterId,
            List<UUID> windowSceneIds,
            Map<UUID, List<EventMention>> mentionsByScene
    ) {
        StringBuilder scenesBuilder = new StringBuilder();

        for (UUID sceneId : windowSceneIds) {
            scenesBuilder.append("  <scene id=\"").append(sceneId).append("\">\n");
            List<EventMention> sceneMentions = mentionsByScene.getOrDefault(sceneId, List.of());

            for (EventMention mention : sceneMentions) {
                scenesBuilder
                        .append("    <mention id=\"").append(mention.id()).append("\" eventType=\"")
                        .append(escapeXml(nonBlankOrFallback(mention.eventType(), "UNKNOWN")))
                        .append("\" normalizedName=\"")
                        .append(escapeXml(nonBlankOrFallback(mention.normalizedName(), "")))
                        .append("\" sceneRelativeRelation=\"")
                        .append(escapeXml(nonBlankOrFallback(mention.sceneRelativeRelation(), "unknown")))
                        .append("\" certainty=\"")
                        .append(escapeXml(confidenceToken(mention.certainty())))
                        .append("\">\n")
                        .append("      <displayName>")
                        .append(escapeXml(nonBlankOrFallback(mention.displayName(), "")))
                        .append("</displayName>\n")
                        .append("      <description>")
                        .append(escapeXml(nonBlankOrFallback(mention.description(), "")))
                        .append("</description>\n")
                        .append("      <evidence>")
                        .append(escapeXml(nonBlankOrFallback(mention.evidence(), "")))
                        .append("</evidence>\n")
                        .append("    </mention>\n");
            }

            if (sceneMentions.isEmpty()) {
                scenesBuilder.append("    <noMentions>true</noMentions>\n");
            }
            scenesBuilder.append("  </scene>\n");
        }

        return userTemplate.render(Map.of(
                "chapterId", chapterId,
                "scenes", scenesBuilder.toString().trim()
        ));
    }

    private void ingestWindowResponse(
            EventCorefModels.CorefWindowResponse response,
            Set<UUID> mentionAllowlist,
            Map<AbstractMap.SimpleEntry<UUID, UUID>, Double> bestConfidenceByPair
    ) {
        if (response == null || response.pairs() == null || response.pairs().isEmpty()) {
            return;
        }

        for (EventCorefModels.CorefPairJudgment pair : response.pairs()) {
            if (pair == null || !pair.sameEvent() || pair.confidence() < CONFIDENCE_THRESHOLD) {
                continue;
            }

            UUID mentionA = parseUuid(pair.mentionIdA());
            UUID mentionB = parseUuid(pair.mentionIdB());
            if (mentionA == null || mentionB == null) {
                continue;
            }
            if (mentionA.equals(mentionB)) {
                continue;
            }
            if (!mentionAllowlist.contains(mentionA) || !mentionAllowlist.contains(mentionB)) {
                continue;
            }

            AbstractMap.SimpleEntry<UUID, UUID> canonicalPair = canonicalPair(mentionA, mentionB);
            bestConfidenceByPair.merge(canonicalPair, pair.confidence(), Math::max);
        }
    }

    @Transactional
    public void writeCoreferenceLinksTransactional(
            UUID chapterId,
            String passId,
            String modelId,
            Map<AbstractMap.SimpleEntry<UUID, UUID>, Double> bestConfidenceByPair
    ) {
        mentionRepo.deleteCoreferenceLinks(chapterId);
        for (Map.Entry<AbstractMap.SimpleEntry<UUID, UUID>, Double> entry : bestConfidenceByPair.entrySet()) {
            AbstractMap.SimpleEntry<UUID, UUID> pair = entry.getKey();
            mentionRepo.createSameEventLink(pair.getKey(), pair.getValue(), entry.getValue(), passId, COREF_SOURCE, modelId);
        }
    }

    static AbstractMap.SimpleEntry<UUID, UUID> canonicalPair(UUID a, UUID b) {
        if (a.toString().compareTo(b.toString()) <= 0) {
            return new AbstractMap.SimpleEntry<>(a, b);
        }
        return new AbstractMap.SimpleEntry<>(b, a);
    }

    private @Nullable UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String confidenceToken(String certainty) {
        return nonBlankOrFallback(certainty, "unknown");
    }

    private String nonBlankOrFallback(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String formatWindowRange(List<UUID> windowSceneIds) {
        return windowSceneIds.stream().map(UUID::toString).collect(Collectors.joining("->"));
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return "unknown";
        }
        return throwable.getMessage();
    }

    public static final class EventCoreferenceException extends RuntimeException implements IngestionFailureCarrier {
        private final IngestionFailure failure;

        public EventCoreferenceException(IngestionFailure failure) {
            super(failure != null ? failure.message() : "Event coreference failed");
            this.failure = failure;
        }

        @Override
        public IngestionFailure failure() {
            return failure;
        }
    }
}
