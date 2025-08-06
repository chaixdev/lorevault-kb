package com.lorevault.api.service;

import com.lorevault.api.dto.SubmitChapterRequest;
import com.lorevault.api.dto.SubmitChapterResponse;
import com.lorevault.api.dto.JobStatusResponse;
import com.lorevault.api.model.*;
import com.lorevault.api.repository.ChapterRepository;
import com.lorevault.api.repository.IngestionJobRepository;
import com.lorevault.api.repository.StatusRecordRepository;
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
    private ChapterRepository chapterRepository;

    @Mock
    private IngestionJobRepository jobRepository;

    @Mock
    private StatusRecordRepository statusRecordRepository;

    @Mock
    private HashService hashService;

    @Mock
    private ChunkService chunkService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private IngestionService ingestionService;

    private SubmitChapterRequest sampleRequest;
    private Chapter sampleChapter;
    private IngestionJob sampleJob;

    @BeforeEach
    void setUp() {
        PublicationCoordinates coordinates = new PublicationCoordinates("Middle Earth", "LOTR", 1, null, 1);
        
        sampleRequest = new SubmitChapterRequest();
        sampleRequest.setCoordinates(coordinates);
        sampleRequest.setChapterTitle("The Shadow of the Past");
        sampleRequest.setChapterText("When Frodo reached his majority...");

        sampleChapter = new Chapter();
        sampleChapter.setId(UUID.randomUUID());
        sampleChapter.setCoordinates(coordinates);
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
        when(chapterRepository.findByContentHash(contentHash)).thenReturn(Optional.empty());
        when(chapterRepository.save(any(Chapter.class))).thenReturn(sampleChapter);
        when(jobRepository.save(any(IngestionJob.class))).thenReturn(sampleJob);

        // When
        SubmitChapterResponse response = ingestionService.submitChapter(sampleRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getJobId()).isEqualTo(sampleJob.getId());
        assertThat(response.getChapterId()).isEqualTo(sampleChapter.getId());
        assertThat(response.getMessage()).contains("submitted successfully");

        verify(chapterRepository).save(any(Chapter.class));
        verify(jobRepository, times(1)).save(any(IngestionJob.class)); // Only job creation in submitChapter  
        verify(statusRecordRepository, times(1)).save(any(StatusRecord.class)); // Only QUEUED status
        verify(eventPublisher).publishEvent(any()); // Event published for processing
        
        // Note: Processing now happens asynchronously via event handler
        // ChunkService calls will happen in ChapterProcessor, not directly in submitChapter
    }

    @Test
    void submitChapter_WhenContentExists_ShouldCreateNewJobForExistingChapter() {
        // Given
        String contentHash = "abc123";
        when(hashService.generateSha256Hash(anyString())).thenReturn(contentHash);
        when(chapterRepository.findByContentHash(contentHash)).thenReturn(Optional.of(sampleChapter));
        when(jobRepository.hasActiveJobForChapter(sampleChapter.getId())).thenReturn(false);
        when(jobRepository.save(any(IngestionJob.class))).thenReturn(sampleJob);

        // When
        SubmitChapterResponse response = ingestionService.submitChapter(sampleRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getJobId()).isEqualTo(sampleJob.getId());
        assertThat(response.getChapterId()).isEqualTo(sampleChapter.getId());

        verify(chapterRepository, never()).save(any(Chapter.class)); // Should not create new chapter
        verify(jobRepository, times(1)).save(any(IngestionJob.class)); // Only job creation
        verify(eventPublisher).publishEvent(any()); // Event published for processing
        
        // Note: Processing logic moved to ChapterProcessor via events
    }

    @Test
    void getJobStatus_WhenJobExists_ShouldReturnStatus() {
        // Given
        UUID jobId = sampleJob.getId();
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
        when(statusRecordRepository.findRecentByJobId(jobId)).thenReturn(List.of());

        // When
        Optional<JobStatusResponse> response = ingestionService.getJobStatus(jobId);

        // Then
        assertThat(response).isPresent();
        JobStatusResponse status = response.get();
        assertThat(status.getJobId()).isEqualTo(jobId);
        assertThat(status.getChapterId()).isEqualTo(sampleChapter.getId());
        assertThat(status.getCurrentStatus()).isEqualTo(IngestionStatus.COMPLETE);
        assertThat(status.getProgressPercent()).isEqualTo(100);
        assertThat(status.getIsComplete()).isTrue();
    }

    @Test
    void getJobStatus_WhenJobNotExists_ShouldReturnEmpty() {
        // Given
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        // When
        Optional<JobStatusResponse> response = ingestionService.getJobStatus(jobId);

        // Then
        assertThat(response).isEmpty();
    }
}
