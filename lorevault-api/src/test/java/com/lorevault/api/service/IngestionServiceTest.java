package com.lorevault.api.service;

import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.domain.shared.PublicationCoordinates;
import com.lorevault.api.service.shared.HashService;
import com.lorevault.api.service.ingestion.IngestionService;
import com.lorevault.api.service.content.SceneDetectionService;
import com.lorevault.api.service.content.TextChunkingService;
import com.lorevault.api.service.content.ChunkEmbeddingService;
import com.lorevault.api.graph.port.ContentPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IngestionService using Mockito.
 * Tests business logic without database interactions.
 */
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private HashService hashService;

    @Mock
    private SceneDetectionService sceneDetectionService;

    @Mock
    private TextChunkingService textChunkingService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ChunkEmbeddingService chunkEmbeddingService;

    @Mock
    private ContentPersistencePort contentPersistencePort;

    @InjectMocks
    private IngestionService ingestionService;

    private SubmitChapterRequest sampleRequest;
    private com.lorevault.api.graph.model.ChapterNode sampleChapter;
    private IngestionJob sampleJob;

    @BeforeEach
    void setUp() {
        PublicationCoordinates coordinates = new PublicationCoordinates("Middle Earth", "LOTR", 1, null, 1);
        sampleRequest = new SubmitChapterRequest();
        sampleRequest.setCoordinates(coordinates);
        sampleRequest.setChapterTitle("The Shadow of the Past");
        sampleRequest.setChapterText("When Frodo reached his majority...");

        sampleChapter = new com.lorevault.api.graph.model.ChapterNode();
        sampleChapter.setId(UUID.randomUUID());
        sampleChapter.setUniverse(coordinates.getUniverse());
        sampleChapter.setSeries(coordinates.getSeries());
        sampleChapter.setBookNumber(coordinates.getBookNumber());
        sampleChapter.setPartNumber(coordinates.getPartNumber());
        sampleChapter.setChapterNumber(coordinates.getChapterNumber());
        sampleChapter.setChapterTitle("The Shadow of the Past");
        sampleChapter.setRawText("When Frodo reached his majority...");
        sampleChapter.setContentHash("abc123");

        sampleJob = new IngestionJob();
        sampleJob.setId(UUID.randomUUID());
        sampleJob.setChapterId(sampleChapter.getId());
        sampleJob.setCurrentStatus(IngestionStatus.COMPLETE);
        sampleJob.setProgressPercent(100);
    }

    @Test
    void submitChapter_WhenNewContent_ShouldCreateChapterAndJob() {
        // Given
        String contentHash = "abc123";
        when(hashService.generateSha256Hash(anyString())).thenReturn(contentHash);
        when(contentPersistencePort.findChapterByContentHash(contentHash)).thenReturn(Optional.empty());
        when(contentPersistencePort.createChapter(any())).thenAnswer(inv -> {
            com.lorevault.api.graph.model.ChapterNode n = inv.getArgument(0);
            if (n.getId() == null) n.setId(UUID.randomUUID());
            return n;
        });
        when(contentPersistencePort.createJob(any())).thenAnswer(inv -> {
            com.lorevault.api.graph.model.IngestionJobNode n = inv.getArgument(0);
            if (n.getId() == null) n.setId(UUID.randomUUID());
            return n;
        });
        when(contentPersistencePort.addStatusRecord(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        // When
        SubmitChapterResponse response = ingestionService.submitChapter(sampleRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getJobId()).isNotNull();
        assertThat(response.getChapterId()).isNotNull();
        verify(eventPublisher).publishEvent(any());
        verify(contentPersistencePort).createChapter(any());
        verify(contentPersistencePort).createJob(any());
        verify(contentPersistencePort).addStatusRecord(any(), any());
    }

    @Test
    void submitChapter_WhenContentExists_ShouldReuseOrCreateJob() {
        // Given
        String contentHash = "abc123";
        UUID existingChapterId = UUID.randomUUID();
        var existingChapterNode = new com.lorevault.api.graph.model.ChapterNode(); existingChapterNode.setId(existingChapterId);
        when(hashService.generateSha256Hash(anyString())).thenReturn(contentHash);
        when(contentPersistencePort.findChapterByContentHash(contentHash)).thenReturn(Optional.of(existingChapterNode));
        when(contentPersistencePort.hasActiveJobForChapter(existingChapterId)).thenReturn(false);
        when(contentPersistencePort.createJob(any())).thenAnswer(inv -> {
            com.lorevault.api.graph.model.IngestionJobNode n = inv.getArgument(0);
            if (n.getId() == null) n.setId(UUID.randomUUID());
            return n;
        });
        when(contentPersistencePort.addStatusRecord(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        // When
        SubmitChapterResponse response = ingestionService.submitChapter(sampleRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getChapterId()).isEqualTo(existingChapterId);
        verify(contentPersistencePort, never()).createChapter(any());
        verify(contentPersistencePort).createJob(any());
    }

    @Test
    void getJobStatus_WhenJobExists_ShouldReturnStatus() {
        // Given
        UUID jobId = UUID.randomUUID();
        var jobNode = new com.lorevault.api.graph.model.IngestionJobNode(); jobNode.setId(jobId); jobNode.setChapterId(UUID.randomUUID()); jobNode.setCurrentStatus(IngestionStatus.COMPLETE); jobNode.setProgressPercent(100);
        when(contentPersistencePort.findJob(jobId)).thenReturn(Optional.of(jobNode));
        when(contentPersistencePort.findRecentStatusRecords(jobId, 5)).thenReturn(List.of());

        // When
        Optional<JobStatusResponse> response = ingestionService.getJobStatus(jobId);

        // Then
        assertThat(response).isPresent();
        assertThat(response.get().getJobId()).isEqualTo(jobId);
        assertThat(response.get().getCurrentStatus()).isEqualTo(IngestionStatus.COMPLETE);
        assertThat(response.get().getIsComplete()).isTrue();
    }

    @Test
    void getJobStatus_WhenJobNotExists_ShouldReturnEmpty() {
        // Given
        UUID jobId = UUID.randomUUID();
        when(contentPersistencePort.findJob(jobId)).thenReturn(Optional.empty());

        // When
        Optional<JobStatusResponse> response = ingestionService.getJobStatus(jobId);

        // Then
        assertThat(response).isEmpty();
    }
}
