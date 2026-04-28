package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.pipeline.IngestionCompletionCoordinator;
import com.lorevault.api.ingestion.domain.IngestionJob;
import com.lorevault.api.ingestion.domain.IngestionStatus;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.events.BookIndividualsReducedEvent;
import com.lorevault.api.ingestion.events.BookEventCandidatesGeneratedEvent;
import com.lorevault.api.ingestion.events.BookLocationsReducedEvent;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import com.lorevault.api.ingestion.events.IngestionCompletedEvent;
import com.lorevault.api.ingestion.events.EmbeddingsCompletedEvent;
import com.lorevault.api.ingestion.events.IngestionFailedEvent;
import com.lorevault.api.ingestion.infrastructure.IngestionJobGraphRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionCompletionCoordinator")
class IngestionCompletionCoordinatorTest {

    @Mock
    private IngestionJobGraphRepository jobRepo;

    @Mock
    private IngestionJobService ingestionJobService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private IngestionCompletionCoordinator coordinator;

    @Test
    @DisplayName("Completes ingestion only after all fan-in branches finish")
    void completesOnlyWhenBothBranchesArrive() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        job.setChapterId(chapterId);

        when(jobRepo.findById(jobId)).thenReturn(java.util.Optional.of(job));

        coordinator.handleBookIndividualsReduced(new BookIndividualsReducedEvent(this, jobId, chapterId, UUID.randomUUID(), true, 3, 1));

        verify(ingestionJobService, never()).completeJob(any(), any(), any(Integer.class));

        coordinator.handleEmbeddingsCompleted(new EmbeddingsCompletedEvent(this, jobId, chapterId, 2, 4, 4, 1200));

        verify(ingestionJobService, never()).completeJob(any(), any(), any(Integer.class));

        coordinator.handleBookLocationsReduced(new BookLocationsReducedEvent(this, jobId, chapterId, UUID.randomUUID(), true, 2, 1));

        verify(ingestionJobService, never()).completeJob(any(), any(), any(Integer.class));

        coordinator.handleChapterEventsResolved(new ChapterEventsResolvedEvent(
                this,
                jobId,
                chapterId,
                UUID.randomUUID(),
                true,
                5,
                2,
                0
        ));

        verify(ingestionJobService, never()).completeJob(any(), any(), any(Integer.class));

        coordinator.handleBookEventCandidatesGenerated(new BookEventCandidatesGeneratedEvent(
                this,
                jobId,
                chapterId,
                UUID.randomUUID(),
                2,
                1
        ));

        verify(ingestionJobService).completeJob(job, chapterId, 1200);
        verify(eventPublisher).publishEvent(any(IngestionCompletedEvent.class));
    }

    @Test
    @DisplayName("Does not complete ingestion for failed jobs")
    void doesNotCompleteFailedJobs() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        job.setChapterId(chapterId);

        StatusRecord failedStatus = new StatusRecord();
        failedStatus.setStatus(IngestionStatus.FAILED);
        job.setCurrentStatus(failedStatus);

        coordinator.handleBookIndividualsReduced(new BookIndividualsReducedEvent(this, jobId, chapterId, UUID.randomUUID(), true, 3, 1));
        coordinator.handleEmbeddingsCompleted(new EmbeddingsCompletedEvent(this, jobId, chapterId, 2, 4, 4, 1200));
        coordinator.handleBookLocationsReduced(new BookLocationsReducedEvent(this, jobId, chapterId, UUID.randomUUID(), true, 2, 1));
        coordinator.handleChapterEventsResolved(new ChapterEventsResolvedEvent(
                this,
                jobId,
                chapterId,
                UUID.randomUUID(),
                true,
                5,
                2,
                0
        ));
        coordinator.handleBookEventCandidatesGenerated(new BookEventCandidatesGeneratedEvent(
                this,
                jobId,
                chapterId,
                UUID.randomUUID(),
                2,
                1
        ));

        verify(ingestionJobService, never()).completeJob(any(), any(), any(Integer.class));
        verify(eventPublisher, never()).publishEvent(any(IngestionCompletedEvent.class));
    }

    @Test
    @DisplayName("Removes retained completion state when ingestion fails before all branches arrive")
    void removesRetainedCompletionStateOnIngestionFailure() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        coordinator.handleBookIndividualsReduced(new BookIndividualsReducedEvent(this, jobId, chapterId, bookId, true, 3, 1));
        coordinator.handleEmbeddingsCompleted(new EmbeddingsCompletedEvent(this, jobId, chapterId, 2, 4, 4, 1200));

        coordinator.handleIngestionFailed(new IngestionFailedEvent(
                this,
                jobId,
                chapterId,
                "EVENT_COREF",
                "llm backend unavailable",
                false
        ));

        coordinator.handleBookLocationsReduced(new BookLocationsReducedEvent(this, jobId, chapterId, bookId, true, 2, 1));
        coordinator.handleChapterEventsResolved(new ChapterEventsResolvedEvent(
                this,
                jobId,
                chapterId,
                bookId,
                true,
                5,
                2,
                0
        ));
        coordinator.handleBookEventCandidatesGenerated(new BookEventCandidatesGeneratedEvent(
                this,
                jobId,
                chapterId,
                bookId,
                2,
                1
        ));

        coordinator.handleBookIndividualsReduced(new BookIndividualsReducedEvent(this, jobId, chapterId, bookId, true, 3, 1));
        coordinator.handleEmbeddingsCompleted(new EmbeddingsCompletedEvent(this, jobId, chapterId, 2, 4, 4, 1200));
        coordinator.handleBookLocationsReduced(new BookLocationsReducedEvent(this, jobId, chapterId, bookId, true, 2, 1));
        coordinator.handleChapterEventsResolved(new ChapterEventsResolvedEvent(this, jobId, chapterId, bookId, true, 5, 2, 0));
        coordinator.handleBookEventCandidatesGenerated(new BookEventCandidatesGeneratedEvent(this, jobId, chapterId, bookId, 2, 1));

        verify(jobRepo, never()).findById(jobId);
        verify(ingestionJobService, never()).completeJob(any(), any(), any(Integer.class));
        verify(eventPublisher, never()).publishEvent(any(IngestionCompletedEvent.class));
    }

    @Test
    @DisplayName("Replaying all fan-in branches after successful completion does not publish completion again")
    void doesNotRepublishCompletionOnReplay() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(jobId);
        job.setChapterId(chapterId);

        // First call: job not yet complete
        // Second call (replay): job is already COMPLETE
        StatusRecord completeStatus = new StatusRecord();
        completeStatus.setStatus(IngestionStatus.COMPLETE);
        IngestionJob completedJob = new IngestionJob();
        completedJob.setId(jobId);
        completedJob.setChapterId(chapterId);
        completedJob.setCurrentStatus(completeStatus);

        when(jobRepo.findById(jobId))
                .thenReturn(java.util.Optional.of(job))        // first fan-in
                .thenReturn(java.util.Optional.of(completedJob)); // replay fan-in

        // First full fan-in — should complete exactly once
        coordinator.handleBookIndividualsReduced(new BookIndividualsReducedEvent(this, jobId, chapterId, bookId, true, 3, 1));
        coordinator.handleEmbeddingsCompleted(new EmbeddingsCompletedEvent(this, jobId, chapterId, 2, 4, 4, 1200));
        coordinator.handleBookLocationsReduced(new BookLocationsReducedEvent(this, jobId, chapterId, bookId, true, 2, 1));
        coordinator.handleChapterEventsResolved(new ChapterEventsResolvedEvent(this, jobId, chapterId, bookId, true, 5, 2, 0));
        coordinator.handleBookEventCandidatesGenerated(new BookEventCandidatesGeneratedEvent(this, jobId, chapterId, bookId, 2, 1));

        verify(ingestionJobService, times(1)).completeJob(job, chapterId, 1200);
        verify(eventPublisher, times(1)).publishEvent(any(IngestionCompletedEvent.class));

        // Replay all five branches again — job status is now COMPLETE; must be absorbed silently
        coordinator.handleBookIndividualsReduced(new BookIndividualsReducedEvent(this, jobId, chapterId, bookId, true, 3, 1));
        coordinator.handleEmbeddingsCompleted(new EmbeddingsCompletedEvent(this, jobId, chapterId, 2, 4, 4, 1200));
        coordinator.handleBookLocationsReduced(new BookLocationsReducedEvent(this, jobId, chapterId, bookId, true, 2, 1));
        coordinator.handleChapterEventsResolved(new ChapterEventsResolvedEvent(this, jobId, chapterId, bookId, true, 5, 2, 0));
        coordinator.handleBookEventCandidatesGenerated(new BookEventCandidatesGeneratedEvent(this, jobId, chapterId, bookId, 2, 1));

        // Still exactly once — replay must not re-trigger completion
        verify(ingestionJobService, times(1)).completeJob(any(), any(), any(Integer.class));
        verify(eventPublisher, times(1)).publishEvent(any(IngestionCompletedEvent.class));
    }
}
