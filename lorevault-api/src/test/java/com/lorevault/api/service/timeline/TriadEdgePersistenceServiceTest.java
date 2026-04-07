package com.lorevault.api.service.timeline;

import com.lorevault.api.infrastructure.persistence.neo4j.repository.TemporalEdgeWriteRepository;
import com.lorevault.api.service.content.TriadOrchestrationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TriadEdgePersistenceServiceTest {

    @Test
    void applies_edges_for_prev_and_next_when_present() {
    TemporalEdgeWriteRepository repository = mock(TemporalEdgeWriteRepository.class);
    when(repository.upsertTemporalEdge(any(), any(), anyString(), anyString(), any(), anyString(), anyString(), any(), any(), any())).thenReturn(1L);

    TriadEdgePersistenceService svc = new TriadEdgePersistenceService(repository);

        UUID prev = UUID.randomUUID();
        UUID curr = UUID.randomUUID();
        UUID next = UUID.randomUUID();

        var analysis = new TriadOrchestrationService.TriadAnalysis(
                prev, curr, next,
                null,
                "R:temporal.before", "Explicit", "prev-evid",
                "R:temporal.meets", "Heuristic", "next-evid",
                "R:temporal.after"
        );

        svc.applyTriadAnalyses(List.of(analysis));

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
    verify(repository, times(2)).upsertTemporalEdge(any(), any(), typeCaptor.capture(), anyString(), any(), anyString(), anyString(), any(), any(), any());

        assertThat(typeCaptor.getAllValues()).containsExactlyInAnyOrder("R:temporal.before", "R:temporal.meets");
    }
}
