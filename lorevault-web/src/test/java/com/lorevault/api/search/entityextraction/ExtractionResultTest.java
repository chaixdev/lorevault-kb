package com.lorevault.api.search.entityextraction;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionResultTest {

    @Test
    void empty_hasNoEntities() {
        ExtractionResult result = ExtractionResult.empty();
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.allCandidates()).isEmpty();
    }

    @Test
    void allCandidates_deduplicatesCaseInsensitively() {
        ExtractionResult result = ExtractionResult.of(
                List.of("Vin", "Kelsier"),
                List.of("vin", "Luthadel") // "vin" is a dupe of "Vin"
        );

        Set<String> candidates = result.allCandidates();
        assertThat(candidates).hasSize(3); // Vin (deduped), Kelsier, Luthadel
    }

    @Test
    void allCandidates_containsKnownAndUnknown() {
        ExtractionResult result = ExtractionResult.of(
                List.of("Vin"),
                List.of("the Well of Ascension")
        );

        Set<String> candidates = result.allCandidates();
        assertThat(candidates).contains("Vin", "the Well of Ascension");
    }

    @Test
    void isEmpty_falseWhenKnownEntitiesPresent() {
        ExtractionResult result = ExtractionResult.of(List.of("Vin"), List.of());
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void isEmpty_falseWhenNounPhrasesPresent() {
        ExtractionResult result = ExtractionResult.of(List.of(), List.of("the Final Empire"));
        assertThat(result.isEmpty()).isFalse();
    }
}
