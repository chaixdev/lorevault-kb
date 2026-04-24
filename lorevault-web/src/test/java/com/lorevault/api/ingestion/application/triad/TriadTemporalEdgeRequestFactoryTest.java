package com.lorevault.api.ingestion.application.triad;

import com.lorevault.api.content.timeline.application.TemporalEdgeWriteRequest;
import com.lorevault.api.ingestion.domain.triad.TriadAnalysisModels;
import com.lorevault.api.ingestion.domain.LlmCallRecord;
import com.lorevault.api.ingestion.domain.StatusRecord;
import com.lorevault.api.ingestion.domain.TriadAnalysisArtifactLookup;
import com.lorevault.api.ingestion.domain.TriadAnalysisException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriadTemporalEdgeRequestFactoryTest {

    @Mock
    private TriadAnalysisArtifactLookup triadAnalysisArtifactLookup;

    private TriadTemporalEdgeRequestFactory factory;

    private final UUID chapterId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID scene0Id = UUID.randomUUID();
    private final UUID scene1Id = UUID.randomUUID();
    private final UUID statusRecordId = UUID.randomUUID();
    private final UUID callRecordId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        factory = new TriadTemporalEdgeRequestFactory(triadAnalysisArtifactLookup);
        when(triadAnalysisArtifactLookup.findLatestJobIdByChapterId(chapterId)).thenReturn(Optional.of(jobId));

        StatusRecord statusRecord = new StatusRecord();
        statusRecord.setId(statusRecordId);
        when(triadAnalysisArtifactLookup.findLatestTriadStatusByCurrentSceneId(eq(jobId), eq(scene1Id)))
                .thenReturn(Optional.of(statusRecord));
    }

    @Test
    void buildRequests_shouldCreateContentOwnedWriteRequestWithProvenance() {
        LlmCallRecord callRecord = new LlmCallRecord();
        callRecord.setId(callRecordId);
        callRecord.setResponseBody("{\"ok\":true}");
        callRecord.setTruncated(false);
        when(triadAnalysisArtifactLookup.findLatestTriadCallRecord(eq(jobId), eq(statusRecordId)))
                .thenReturn(Optional.of(callRecord));

        List<TemporalEdgeWriteRequest> requests = factory.buildRequests(
                chapterId,
                List.of(new TriadAnalysisModels.SceneRelationshipAnalysis(
                        scene0Id,
                        scene1Id,
                        null,
                        0,
                        1,
                        null,
                        "marker",
                        "R:temporal.before",
                        "Explicit",
                        "test evidence",
                        null,
                        null,
                        null,
                        null
                )),
                Map.of(0, scene0Id, 1, scene1Id)
        );

        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.fromSceneId()).isEqualTo(scene0Id);
            assertThat(request.toSceneId()).isEqualTo(scene1Id);
            assertThat(request.temporalType()).isEqualTo("R:temporal.before");
            assertThat(request.provenance().jobId()).isEqualTo(jobId);
            assertThat(request.provenance().chapterId()).isEqualTo(chapterId);
            assertThat(request.provenance().statusRecordId()).isEqualTo(statusRecordId);
            assertThat(request.provenance().llmCallRecordId()).isEqualTo(callRecordId);
        });
    }

    @Test
    void buildRequests_shouldFailWhenTriadArtifactsAreTruncated() {
        LlmCallRecord truncated = new LlmCallRecord();
        truncated.setId(callRecordId);
        truncated.setResponseBody(null);
        truncated.setTruncated(true);
        when(triadAnalysisArtifactLookup.findLatestTriadCallRecord(eq(jobId), eq(statusRecordId)))
                .thenReturn(Optional.of(truncated));

        assertThatThrownBy(() -> factory.buildRequests(
                chapterId,
                List.of(new TriadAnalysisModels.SceneRelationshipAnalysis(
                        scene0Id,
                        scene1Id,
                        null,
                        0,
                        1,
                        null,
                        "marker",
                        "R:temporal.before",
                        "Explicit",
                        "test evidence",
                        null,
                        null,
                        null,
                        null
                )),
                Map.of(0, scene0Id, 1, scene1Id)
        )).isInstanceOf(TriadAnalysisException.class)
                .extracting(Throwable::getMessage)
                .asString()
                .contains("missing or truncated");
    }
}
