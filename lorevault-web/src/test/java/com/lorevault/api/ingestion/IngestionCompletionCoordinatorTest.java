package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.ingestion.events.BookIndividualsReducedEvent;
import com.lorevault.api.ingestion.events.BookLocationsReducedEvent;
import com.lorevault.api.ingestion.events.EmbeddingsCompletedEvent;
import com.lorevault.api.ingestion.events.IngestionCompletedEvent;
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
    @DisplayName("Completes ingestion only after embedding, individual, and location branches finish")
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

        when(jobRepo.findById(jobId)).thenReturn(java.util.Optional.of(job));

        coordinator.handleBookIndividualsReduced(new BookIndividualsReducedEvent(this, jobId, chapterId, UUID.randomUUID(), true, 3, 1));
        coordinator.handleEmbeddingsCompleted(new EmbeddingsCompletedEvent(this, jobId, chapterId, 2, 4, 4, 1200));
        coordinator.handleBookLocationsReduced(new BookLocationsReducedEvent(this, jobId, chapterId, UUID.randomUUID(), true, 2, 1));

        verify(ingestionJobService, never()).completeJob(any(), any(), any(Integer.class));
        verify(eventPublisher, never()).publishEvent(any(IngestionCompletedEvent.class));
    }
}
