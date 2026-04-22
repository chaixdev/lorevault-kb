package com.lorevault.api.search.entityextraction;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryEntityExtractorTest {

    @Mock KnownEntityTrie           knownEntityTrie;
    @Mock OpenNlpNounPhraseExtractor openNlpExtractor;

    QueryEntityExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new QueryEntityExtractor(knownEntityTrie, openNlpExtractor);
    }

    @Test
    void nullQuery_returnsEmpty() {
        ExtractionResult result = extractor.extract(null);
        assertThat(result.isEmpty()).isTrue();
        verifyNoInteractions(knownEntityTrie, openNlpExtractor);
    }

    @Test
    void blankQuery_returnsEmpty() {
        ExtractionResult result = extractor.extract("   ");
        assertThat(result.isEmpty()).isTrue();
        verifyNoInteractions(knownEntityTrie, openNlpExtractor);
    }

    @Test
    void knownEntitiesFound_returnedInResult() {
        when(knownEntityTrie.match("What does Vin do in Luthadel?"))
                .thenReturn(List.of("Vin", "Luthadel"));
        when(openNlpExtractor.extractNounPhrases(eq("What does Vin do in Luthadel?"), any()))
                .thenReturn(List.of());

        ExtractionResult result = extractor.extract("What does Vin do in Luthadel?");

        assertThat(result.knownEntities()).containsExactly("Vin", "Luthadel");
        assertThat(result.unknownNounPhrases()).isEmpty();
    }

    @Test
    void openNlpDiscoveredPhrases_excludeAlreadyKnown() {
        when(knownEntityTrie.match(anyString())).thenReturn(List.of("Kelsier"));
        when(openNlpExtractor.extractNounPhrases(anyString(), any()))
                .thenAnswer(inv -> {
                    Set<String> alreadyMatched = inv.getArgument(1);
                    // OpenNLP should NOT re-add "Kelsier"; returns only new phrase
                    assertThat(alreadyMatched).contains("Kelsier");
                    return List.of("the Pits of Hathsin");
                });

        ExtractionResult result = extractor.extract("How did Kelsier escape the Pits of Hathsin?");

        assertThat(result.knownEntities()).containsExactly("Kelsier");
        assertThat(result.unknownNounPhrases()).containsExactly("the Pits of Hathsin");
        assertThat(result.allCandidates()).containsExactlyInAnyOrder("Kelsier", "the Pits of Hathsin");
    }

    @Test
    void bothStrategiesEmpty_returnsEmpty() {
        when(knownEntityTrie.match(anyString())).thenReturn(List.of());
        when(openNlpExtractor.extractNounPhrases(anyString(), any())).thenReturn(List.of());

        ExtractionResult result = extractor.extract("What happened?");

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void refreshKnownEntities_delegatesToTrie() {
        extractor.refreshKnownEntities();
        verify(knownEntityTrie).refresh();
    }
}
