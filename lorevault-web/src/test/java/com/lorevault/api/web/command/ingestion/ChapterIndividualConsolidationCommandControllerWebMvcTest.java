package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StageResult;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChapterIndividualConsolidationCommandController.class)
class ChapterIndividualConsolidationCommandControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StageOperation chapterIndividualResolutionOperation;

    @MockitoBean
    private StepEventMapper stepEventMapper;

    @Test
    void consolidateChapterIndividuals_success_returns200() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterIndividualResolutionOperation.execute(
                new StageExecutionContext(null, null, chapterId, null, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION)))
                .thenReturn(StageResult.success(
                        StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION,
                        "Resolved chapter individual mentions",
                        Map.of("mentionCount", 3, "chapterIndividualCount", 2),
                        150L
                ));

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/chapter-consolidate-individuals", chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.step").value("chapter-individual-consolidation"))
                .andExpect(jsonPath("$.scope").value("chapter"))
                .andExpect(jsonPath("$.scopeId").value(chapterId.toString()))
                .andExpect(jsonPath("$.counts.mentionCount").value(3))
                .andExpect(jsonPath("$.counts.chapterIndividualCount").value(2));

        verify(chapterIndividualResolutionOperation).execute(
                new StageExecutionContext(null, null, chapterId, null, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION));
    }

    @Test
    void consolidateChapterIndividuals_invalidUuid_returns400() throws Exception {
        mockMvc.perform(post("/api/command/ingest/chapters/not-a-uuid/chapter-consolidate-individuals"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CHAPTER_ID"));
    }

    @Test
    void consolidateChapterIndividuals_stageFailure_returns200WithFailureResult() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterIndividualResolutionOperation.execute(
                new StageExecutionContext(null, null, chapterId, null, StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION)))
                .thenReturn(StageResult.failure(
                        StageKey.CHAPTER_INDIVIDUAL_CONSOLIDATION, "Entity not found", 0L));

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/chapter-consolidate-individuals", chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.summary").value("Entity not found"));
    }
}