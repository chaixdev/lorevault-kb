package com.lorevault.api.web.query.job;

import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.dto.ingestion.JobListResponse;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.service.ingestion.IngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JobsController.class)
class JobsControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    IngestionService ingestionService;

    @AfterEach
    void tearDown() { reset(ingestionService); }

    @Test
    void getJobStatus_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/query/jobs/not-a-uuid").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_JOB_ID"))
                .andExpect(jsonPath("$.path").value("/api/query/jobs/not-a-uuid"));
    }

    @Test
    void getJobStatus_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(ingestionService.getJobStatus(eq(id))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/query/jobs/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJobStatus_success_returns200WithBody() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        JobStatusResponse resp = new JobStatusResponse(
                jobId,
                chapterId,
                IngestionStatus.SCENE_SEGMENTATION,
                15,
                LocalDateTime.now().minusMinutes(1),
                null,
                false,
                List.of()
        );
        when(ingestionService.getJobStatus(eq(jobId))).thenReturn(Optional.of(resp));

        mockMvc.perform(get("/api/query/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.currentStatus").value("SCENE_SEGMENTATION"))
                .andExpect(jsonPath("$.isComplete").value(false));
    }

    @Test
    void listJobs_invalidLimit_returns400() throws Exception {
        mockMvc.perform(get("/api/query/jobs").param("limit", "0").param("offset", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PAGINATION"));
    }

    @Test
    void listJobs_invalidOffset_returns400() throws Exception {
        mockMvc.perform(get("/api/query/jobs").param("limit", "10").param("offset", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PAGINATION"));
    }

    @Test
    void listJobs_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/query/jobs").param("status", "FOOBAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS"));
    }

    @Test
    void listJobs_success_returns200WithList() throws Exception {
        JobListResponse.JobSummary summary = new JobListResponse.JobSummary(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Middle Earth",
                "LOTR",
                1,
                1,
                "A Long-Expected Party",
                IngestionStatus.COMPLETE,
                100,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now()
        );
        JobListResponse.Pagination page = new JobListResponse.Pagination(1, 10, 0, false);
        JobListResponse resp = new JobListResponse(List.of(summary), page);

        when(ingestionService.listJobs(any(), any(), anyInt(), anyInt())).thenReturn(resp);

        mockMvc.perform(get("/api/query/jobs").param("status", "COMPLETE").param("limit", "10").param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].universe").value("Middle Earth"))
                .andExpect(jsonPath("$.jobs[0].status").value("COMPLETE"))
                .andExpect(jsonPath("$.pagination.total").value(1));
    }
}
