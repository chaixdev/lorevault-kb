package com.lorevault.api.graph.event.consolidation.book;

import com.lorevault.api.ai.infrastructure.PromptName;
import com.lorevault.api.ai.infrastructure.PromptRepository;
import com.lorevault.api.ai.llm.EventMergeModels;
import com.lorevault.api.ai.llm.LlmClient;
import com.lorevault.api.graph.event.persistence.ChapterEvent;
import static com.lorevault.api.common.error.ExceptionSanitizer.safeMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

@Service
public class BookEventMergeVerificationService {

    private static final Logger log = LoggerFactory.getLogger(BookEventMergeVerificationService.class);

    private final PromptRepository promptRepository;
    private final LlmClient llmClient;

    public BookEventMergeVerificationService(PromptRepository promptRepository, LlmClient llmClient) {
        this.promptRepository = promptRepository;
        this.llmClient = llmClient;
    }

    public List<EventMergeModels.EventMergeVerification> verifyCandidates(
            UUID jobId,
            UUID chapterId,
            List<BookEventCandidatePair> candidatePairs,
            Map<UUID, ChapterEvent> chapterEventsById
    ) {
        if (candidatePairs == null || candidatePairs.isEmpty()) {
            log.info("[EVENT_MERGE] No ANN candidates to verify: jobId={}, chapterId={}", jobId, chapterId);
            return List.of();
        }

        PromptTemplate userTemplate = promptRepository.get(PromptName.EVENT_MERGE_USER);
        List<EventMergeModels.EventMergeVerification> verifications = new ArrayList<>();

        for (BookEventCandidatePair pair : candidatePairs) {
            if (pair == null) {
                continue;
            }

            ChapterEvent eventA = chapterEventsById.get(pair.eventId1());
            ChapterEvent eventB = chapterEventsById.get(pair.eventId2());
            if (eventA == null || eventB == null) {
                log.warn(
                        "[EVENT_MERGE] Candidate skipped due to missing ChapterEvent(s): jobId={}, chapterId={}, eventId1={}, eventId2={}",
                        jobId,
                        chapterId,
                        pair.eventId1(),
                        pair.eventId2()
                );
                verifications.add(new EventMergeModels.EventMergeVerification(
                        pair.eventId1(),
                        pair.eventId2(),
                        EventMergeModels.MergeDecision.UNRESOLVED,
                        0.0d,
                        "Missing ChapterEvent payload"
                ));
                continue;
            }

            String userInput = userTemplate.render(buildPromptVariables(pair, eventA, eventB));
            EventMergeModels.EventMergePairResponse response;
            try {
                response = llmClient.runEventMergeVerification(jobId, userInput);
            } catch (RuntimeException ex) {
                log.warn(
                        "[EVENT_MERGE] LLM verification failed; marking unresolved: jobId={}, chapterId={}, eventId1={}, eventId2={}, error={}",
                        jobId,
                        chapterId,
                        pair.eventId1(),
                        pair.eventId2(),
                        safeMessage((Exception) ex)
                );
                response = null;
            }

            EventMergeModels.EventMergeVerification verification = toVerification(pair, response);
            verifications.add(verification);

            log.info(
                    "[EVENT_MERGE] Pair verified: jobId={}, chapterId={}, eventId1={}, eventId2={}, annScore={}, decision={}, confidence={}",
                    jobId,
                    chapterId,
                    pair.eventId1(),
                    pair.eventId2(),
                    pair.annScore(),
                    verification.decision(),
                    verification.confidence()
            );
        }

        long mergeCount = verifications.stream()
                .filter(v -> v.decision() == EventMergeModels.MergeDecision.MERGE)
                .count();
        long keepSeparateCount = verifications.stream()
                .filter(v -> v.decision() == EventMergeModels.MergeDecision.KEEP_SEPARATE)
                .count();
        long unresolvedCount = verifications.size() - mergeCount - keepSeparateCount;

        log.info(
                "[EVENT_MERGE] Completed verification: jobId={}, chapterId={}, candidateCount={}, mergeCount={}, keepSeparateCount={}, unresolvedCount={}",
                jobId,
                chapterId,
                candidatePairs.size(),
                mergeCount,
                keepSeparateCount,
                unresolvedCount
        );

        return List.copyOf(verifications);
    }

    private Map<String, Object> buildPromptVariables(BookEventCandidatePair pair, ChapterEvent eventA, ChapterEvent eventB) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("eventId1", pair.eventId1());
        vars.put("eventId2", pair.eventId2());
        vars.put("annScore", pair.annScore());
        vars.put("event1DisplayName", safe(eventA.displayName()));
        vars.put("event1NormalizedName", safe(eventA.normalizedName()));
        vars.put("event1RepresentativeEventType", safe(eventA.representativeEventType()));
        vars.put("event1AggregateCard", safe(eventA.aggregateCard()));
        vars.put("event1SupportedAliases", toXmlList(eventA.supportedAliases()));
        vars.put("event1SupportedEventTypes", toXmlList(eventA.supportedEventTypes()));
        vars.put("event2DisplayName", safe(eventB.displayName()));
        vars.put("event2NormalizedName", safe(eventB.normalizedName()));
        vars.put("event2RepresentativeEventType", safe(eventB.representativeEventType()));
        vars.put("event2AggregateCard", safe(eventB.aggregateCard()));
        vars.put("event2SupportedAliases", toXmlList(eventB.supportedAliases()));
        vars.put("event2SupportedEventTypes", toXmlList(eventB.supportedEventTypes()));
        return vars;
    }

    private EventMergeModels.EventMergeVerification toVerification(
            BookEventCandidatePair pair,
            EventMergeModels.EventMergePairResponse response
    ) {
        EventMergeModels.MergeDecision decision = response == null
                ? EventMergeModels.MergeDecision.UNRESOLVED
                : EventMergeModels.MergeDecision.from(response.decision());
        double confidence = normalizeConfidence(response == null ? null : response.confidence());
        String rationale = response == null || response.rationale() == null
                ? "LLM response unavailable"
                : response.rationale();

        return new EventMergeModels.EventMergeVerification(
                pair.eventId1(),
                pair.eventId2(),
                decision,
                confidence,
                rationale
        );
    }

    private String toXmlList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            builder.append("<item>").append(escapeXml(value.trim())).append("</item>\n");
        }
        return builder.toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : escapeXml(value);
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private double normalizeConfidence(Double confidence) {
        if (confidence == null || confidence.isNaN() || confidence.isInfinite()) {
            return 0.0d;
        }
        if (confidence < 0.0d) {
            return 0.0d;
        }
        if (confidence > 1.0d) {
            return 1.0d;
        }
        return confidence;
    }

}
