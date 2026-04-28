package com.lorevault.api.search.extraction;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Classifies a natural-language question into a {@link QuestionIntent} using keyword rules.
 *
 * <p>This is intentionally a rule-based first implementation. The brainstorm doc recommends
 * starting with keyword rules and optionally upgrading to a lightweight LLM call later.
 *
 * <p>Entity-lookup signals:
 * <ul>
 *   <li>Starts with "Who is", "What is", "Where is", "Describe", "Tell me about"</li>
 *   <li>Matches "Who was X", "What was X", "Where was X"</li>
 * </ul>
 *
 * <p>Everything else falls through to {@link QuestionIntent#NARRATIVE_QA}.
 */
@Component
public class QuestionIntentClassifier {

    private static final List<Pattern> ENTITY_LOOKUP_PATTERNS = List.of(
            Pattern.compile("^who\\s+is\\b",          Pattern.CASE_INSENSITIVE),
            Pattern.compile("^who\\s+was\\b",         Pattern.CASE_INSENSITIVE),
            Pattern.compile("^what\\s+is\\b",         Pattern.CASE_INSENSITIVE),
            Pattern.compile("^what\\s+was\\b",        Pattern.CASE_INSENSITIVE),
            Pattern.compile("^where\\s+is\\b",        Pattern.CASE_INSENSITIVE),
            Pattern.compile("^where\\s+was\\b",       Pattern.CASE_INSENSITIVE),
            Pattern.compile("^describe\\b",           Pattern.CASE_INSENSITIVE),
            Pattern.compile("^tell\\s+me\\s+about\\b",Pattern.CASE_INSENSITIVE)
    );

    /**
     * Classify the question into a retrieval intent.
     *
     * @param question the raw question string from the user
     * @return the classified {@link QuestionIntent}
     */
    public QuestionIntent classify(String question) {
        if (question == null || question.isBlank()) {
            return QuestionIntent.AMBIGUOUS;
        }

        String trimmed = question.trim();

        for (Pattern pattern : ENTITY_LOOKUP_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return QuestionIntent.ENTITY_LOOKUP;
            }
        }

        return QuestionIntent.NARRATIVE_QA;
    }
}
