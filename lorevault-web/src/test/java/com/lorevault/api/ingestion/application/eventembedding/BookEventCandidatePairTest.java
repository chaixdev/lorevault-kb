package com.lorevault.api.ingestion.application.eventembedding;

import java.util.UUID;

import com.lorevault.api.ingestion.resolution.event.BookEventCandidatePair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BookEventCandidatePair")
class BookEventCandidatePairTest {

    @Test
    @DisplayName("Factory canonicalizes unordered event IDs")
    void factoryCanonicalizesUnorderedEventIds() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

        BookEventCandidatePair forward = BookEventCandidatePair.of(first, second, 0.9);
        BookEventCandidatePair reverse = BookEventCandidatePair.of(second, first, 0.8);

        assertThat(forward.eventId1()).isEqualTo(first);
        assertThat(forward.eventId2()).isEqualTo(second);
        assertThat(reverse.eventId1()).isEqualTo(first);
        assertThat(reverse.eventId2()).isEqualTo(second);
        assertThat(reverse.annScore()).isEqualTo(0.8);
    }
}
