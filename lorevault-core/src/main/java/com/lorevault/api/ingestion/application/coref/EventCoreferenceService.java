package com.lorevault.api.ingestion.application.coref;

import com.lorevault.api.ai.infrastructure.LlmClient;
import com.lorevault.api.ai.infrastructure.PromptRepository;
import com.lorevault.api.content.entities.EventMention;
import com.lorevault.api.content.entities.EventMentionGraphRepository;
import com.lorevault.api.ingestion.domain.coref.EventCorefModels;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 2 — Event Co-reference Pass.
 *
 * <p>Loads all {@code EventMention} nodes for a chapter in deterministic order,
 * drives a rolling-triad window over them, calls the LLM for each window to judge
 * which pairs refer to the same real-world event, and writes {@code SAME_EVENT}
 * relationship edges for confident matches.</p>
 *
 * <p>The pass is idempotent: it deletes all existing {@code SAME_EVENT} links for
 * the chapter before writing new ones.</p>
 *
 * <p>Confidence threshold for writing a link: {@link #CONFIDENCE_THRESHOLD}.
 * When uncertain, the model is instructed to prefer fragmentation over false merges.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventCoreferenceService {

    /** Minimum model confidence required to write a SAME_EVENT link. */
    static final double CONFIDENCE_THRESHOLD = 0.75;

    /** Rolling window size. 3 mentions per window → up to 3 pairs per call. */
    static final int WINDOW_SIZE = 3;

    private final EventMentionGraphRepository mentionRepository;
    private final LlmClient llmClient;
    private final PromptRepository promptRepository;

    /**
     * Run the co-reference pass for a chapter.
     *
     * @param chapterId chapter to process
     * @param jobId     correlation id for LLM call logging
     * @return summary of the pass
     */
    @Transactional
    public EventCorefModels.CorefPassResult runCorefPass(UUID chapterId, UUID jobId) {
        String passId = UUID.randomUUID().toString();
        String modelId = "event-coref"; // logical label for logging

        log.info("[EVENT_COREF] Starting Stage 2 pass: chapterId={}, jobId={}, passId={}", chapterId, jobId, passId);

        List<EventMention> mentions = mentionRepository.findByChapterIdOrdered(chapterId);
        if (mentions.size() < 2) {
            log.info("[EVENT_COREF] Skipping: fewer than 2 mentions for chapterId={}", chapterId);
            return new EventCorefModels.CorefPassResult(chapterId, passId, modelId, 0, 0);
        }

        // Delete stale links before writing new ones (idempotent rebuild)
        mentionRepository.deleteCoreferenceLinks(chapterId);

        int windowsRun = 0;
        int linksCreated = 0;

        // Rolling window: slide by 1
        for (int i = 0; i <= mentions.size() - 2; i++) {
            int end = Math.min(i + WINDOW_SIZE, mentions.size());
            List<EventMention> window = mentions.subList(i, end);

            String userInput = renderUserInput(window, chapterId);
            EventCorefModels.CorefWindowResponse response;

            try {
                response = llmClient.runEventCoref(jobId, userInput);
            } catch (Exception e) {
                log.warn("[EVENT_COREF] Window {} failed, skipping: chapterId={}, error={}",
                        windowsRun, chapterId, e.getMessage());
                windowsRun++;
                continue;
            }

            windowsRun++;

            if (response == null || response.pairs() == null) {
                continue;
            }

            for (EventCorefModels.CorefPairJudgment pair : response.pairs()) {
                if (!pair.sameEvent() || pair.confidence() < CONFIDENCE_THRESHOLD) {
                    continue;
                }
                if (pair.mentionIdA() == null || pair.mentionIdB() == null) {
                    log.debug("[EVENT_COREF] Skipping pair with null id: passId={}", passId);
                    continue;
                }
                try {
                    UUID idA = UUID.fromString(pair.mentionIdA());
                    UUID idB = UUID.fromString(pair.mentionIdB());
                    mentionRepository.createSameEventLink(idA, idB, pair.confidence(), passId, modelId);
                    linksCreated++;
                    log.debug("[EVENT_COREF] SAME_EVENT link: {} <-> {}, confidence={}, passId={}",
                            idA, idB, pair.confidence(), passId);
                } catch (IllegalArgumentException e) {
                    log.warn("[EVENT_COREF] Invalid UUID in pair judgment: mentionIdA={}, mentionIdB={}, passId={}",
                            pair.mentionIdA(), pair.mentionIdB(), passId);
                }
            }
        }

        log.info("[EVENT_COREF] Stage 2 complete: chapterId={}, jobId={}, passId={}, windowsRun={}, linksCreated={}",
                chapterId, jobId, passId, windowsRun, linksCreated);

        return new EventCorefModels.CorefPassResult(chapterId, passId, modelId, windowsRun, linksCreated);
    }

    /**
     * Renders the user input for a single window by formatting mention fields as
     * a structured text block — no raw chapter text is included.
     */
    private String renderUserInput(List<EventMention> window, UUID chapterId) {
        PromptTemplate userTemplate = promptRepository.get("event-coref-user");

        StringBuilder mentionsText = new StringBuilder();
        for (int i = 0; i < window.size(); i++) {
            EventMention m = window.get(i);
            mentionsText.append("  <mention index=\"").append(i + 1).append("\">\n");
            mentionsText.append("    <id>").append(m.id()).append("</id>\n");
            mentionsText.append("    <displayName>").append(safe(m.displayName())).append("</displayName>\n");
            mentionsText.append("    <normalizedName>").append(safe(m.normalizedName())).append("</normalizedName>\n");
            mentionsText.append("    <eventType>").append(safe(m.eventType())).append("</eventType>\n");
            mentionsText.append("    <sceneRelativeRelation>").append(safe(m.sceneRelativeRelation())).append("</sceneRelativeRelation>\n");
            mentionsText.append("    <certainty>").append(safe(m.certainty())).append("</certainty>\n");
            mentionsText.append("    <evidence>").append(safe(m.evidence())).append("</evidence>\n");
            mentionsText.append("  </mention>\n");
        }

        return userTemplate.render(Map.of(
                "chapterId", chapterId.toString(),
                "mentions", mentionsText.toString()
        ));
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
