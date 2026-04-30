package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.resolution.event.ChapterEventAnnRerunResult;
import com.lorevault.api.ingestion.resolution.event.ChapterEventAnnRerunService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EventAnnRerunCommandController.class)
class EventAnnRerunCommandControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChapterEventAnnRerunService chapterEventAnnRerunService;

    @Test
    void rerunAnn_success_returnsPayload() throws Exception {
        UUID universeId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        when(chapterEventAnnRerunService.rerun(universeId, null, null)).thenReturn(new ChapterEventAnnRerunResult(
                true,
                new ChapterEventAnnRerunResult.SelectedScope(universeId, null, null),
                0,
                jobId,
                correlationId,
                "Triggered 0 chapter ANN rerun(s) for universe scope"
        ));

        mockMvc.perform(post("/api/command/ingest/events/rerun-ann")
                        .param("universeId", universeId.toString())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.selectedScope.universeId").value(universeId.toString()))
                .andExpect(jsonPath("$.triggeredChapterCount").value(0))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.correlationId").value(correlationId.toString()));
    }

    @Test
    void rerunAnn_invalidBookUuid_returns400() throws Exception {
        mockMvc.perform(post("/api/command/ingest/events/rerun-ann")
                        .param("bookId", "not-a-uuid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_BOOK_ID"));
    }

    @Test
    void rerunAnn_missingUniverseForDefaultScope_returns400() throws Exception {
        mockMvc.perform(post("/api/command/ingest/events/rerun-ann")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_UNIVERSE_ID"));
    }
}
