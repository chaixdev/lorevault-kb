package com.lorevault.api.search;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.application.CoreSearchRecords.*;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class QuestionIntentClassifierTest {

    private final QuestionIntentClassifier classifier = new QuestionIntentClassifier();

    @ParameterizedTest
    @ValueSource(strings = {
            "Who is Vin?",
            "who is Kelsier",
            "Who was Elend?",
            "What is the Final Empire?",
            "What was the Pits of Hathsin?",
            "Where is Luthadel?",
            "Where was the Well of Ascension?",
            "Describe Sazed",
            "describe the Lord Ruler",
            "Tell me about Breeze",
            "tell me about the skaa rebellion"
    })
    void shouldClassifyEntityLookupQuestions(String question) {
        assertThat(classifier.classify(question)).isEqualTo(QuestionIntent.ENTITY_LOOKUP);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "What happened when Vin met Kelsier?",
            "How did the skaa rebellion start?",
            "Why did Elend become king?",
            "What did Kelsier do at the Pits?",
            "Explain the mist spirit",
            "In which scenes does Vin appear with Kelsier?"
    })
    void shouldClassifyNarrativeQaQuestions(String question) {
        assertThat(classifier.classify(question)).isEqualTo(QuestionIntent.NARRATIVE_QA);
    }

    @Test
    void shouldReturnAmbiguousForNullQuestion() {
        assertThat(classifier.classify(null)).isEqualTo(QuestionIntent.AMBIGUOUS);
    }

    @Test
    void shouldReturnAmbiguousForBlankQuestion() {
        assertThat(classifier.classify("   ")).isEqualTo(QuestionIntent.AMBIGUOUS);
    }
}
