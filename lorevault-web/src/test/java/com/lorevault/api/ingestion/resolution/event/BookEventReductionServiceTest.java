package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.ai.llm.EventMergeModels;
import com.lorevault.api.content.association.ChapterEvent;
import com.lorevault.api.content.association.ChapterEventGraphRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookEventReductionService")
class BookEventReductionServiceTest {

    @Mock private BookEventPersistenceService persistenceService;
    @Mock private ChapterEventGraphRepository chapterEventRepository;

    @Test
    @DisplayName("Reduces only the expanded rewrite scope and preserves current chapter triggers")
    void reducesExpandedRewriteScope() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();
        UUID mergeEndpointId = UUID.randomUUID();
        UUID linkedCurrentId = UUID.randomUUID();

        ChapterEvent current = chapterEvent(chapterId, currentId, "current");
        ChapterEvent mergeEndpoint = chapterEvent(UUID.randomUUID(), mergeEndpointId, "merge-endpoint");
        ChapterEvent linkedCurrent = chapterEvent(UUID.randomUUID(), linkedCurrentId, "linked-current");

        when(persistenceService.expandRewriteScope(List.of(currentId, mergeEndpointId))).thenReturn(List.of(currentId, mergeEndpointId, linkedCurrentId));
        when(persistenceService.saveAndLinkBookEvents(eq(chapterId), eq(jobId), any(), any(), anyList()))
                .thenReturn(new BookEventPersistenceService.BookEventWriteSummary(1, 2));

        BookEventReductionService service = new BookEventReductionService(persistenceService, chapterEventRepository);
        BookEventReductionService.BookEventReductionResult result = service.reduceAndPersist(
                jobId,
                chapterId,
                bookId,
                List.of(current, mergeEndpoint, linkedCurrent),
                List.of(new EventMergeModels.EventMergeDecision(currentId, mergeEndpointId, 0.9d))
        );

        assertThat(result.bookEventsCreated()).isEqualTo(1);
        assertThat(result.referenceLinksWritten()).isEqualTo(2);

        ArgumentCaptor<List<UUID>> scopeCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).expandRewriteScope(scopeCaptor.capture());
        assertThat(scopeCaptor.getValue()).containsExactly(currentId, mergeEndpointId);
    }

    @Test
    @DisplayName("Does not create singleton BookEvents for non-current keep-separate candidates")
    void excludesNonCurrentKeepSeparateCandidateFromRewriteScope() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();
        UUID keepSeparateCandidateId = UUID.randomUUID();

        ChapterEvent current = chapterEvent(chapterId, currentId, "current");
        ChapterEvent keepSeparateCandidate = chapterEvent(UUID.randomUUID(), keepSeparateCandidateId, "keep-separate");

        when(persistenceService.expandRewriteScope(List.of(currentId))).thenReturn(List.of(currentId));
        when(persistenceService.saveAndLinkBookEvents(eq(chapterId), eq(jobId), any(), any(), anyList()))
                .thenReturn(new BookEventPersistenceService.BookEventWriteSummary(1, 1));

        BookEventReductionService service = new BookEventReductionService(persistenceService, chapterEventRepository);
        BookEventReductionService.BookEventReductionResult result = service.reduceAndPersist(
                jobId,
                chapterId,
                bookId,
                List.of(current, keepSeparateCandidate),
                List.of()
        );

        assertThat(result.bookEventsCreated()).isEqualTo(1);
        assertThat(result.referenceLinksWritten()).isEqualTo(1);

        ArgumentCaptor<List<UUID>> rewriteSeedCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).expandRewriteScope(rewriteSeedCaptor.capture());
        assertThat(rewriteSeedCaptor.getValue()).containsExactly(currentId);

        ArgumentCaptor<List<UUID>> persistedScopeCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).saveAndLinkBookEvents(eq(chapterId), eq(jobId), any(), any(), persistedScopeCaptor.capture());
        assertThat(persistedScopeCaptor.getValue()).containsExactly(currentId);
    }

    private ChapterEvent chapterEvent(UUID chapterId, UUID id, String normalizedName) {
        return new ChapterEvent(
                id,
                chapterId,
                null,
                normalizedName,
                normalizedName,
                "TYPE",
                1,
                normalizedName + " aggregate",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }
}
