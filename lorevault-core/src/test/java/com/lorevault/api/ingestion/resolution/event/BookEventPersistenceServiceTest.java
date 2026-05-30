package com.lorevault.api.ingestion.resolution.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.content.association.BookEvent;
import com.lorevault.api.content.association.BookEventGraphRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookEventPersistenceService")
class BookEventPersistenceServiceTest {

    @Mock private BookEventGraphRepository bookEventRepository;
    @Mock private Neo4jClient neo4jClient;

    @Test
    @DisplayName("Links saved BookEvents back to their Book")
    void linksSavedBookEventsBackToBook() {
        UUID bookId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        BookEvent bookEvent = new BookEvent(UUID.randomUUID(), bookId, UUID.randomUUID(), "Duel", "duel", "ACTION", null, null);
        when(bookEventRepository.saveAll(List.of(bookEvent))).thenReturn(List.of(bookEvent));

        BookEventPersistenceService service = new BookEventPersistenceService(bookEventRepository, neo4jClient);
        BookEventPersistenceService.BookEventWriteSummary summary = service.saveAndLinkBookEvents(
                bookId,
                chapterId,
                jobId,
                List.of(bookEvent),
                List.of(List.<UUID>of()),
                List.<UUID>of()
        );

        assertThat(summary.bookEventsCreated()).isEqualTo(1);
        assertThat(summary.referenceLinksWritten()).isZero();
        verify(bookEventRepository).linkBookToEvent(bookId, bookEvent.id());
        verify(neo4jClient, never()).query(anyString());
    }
}
