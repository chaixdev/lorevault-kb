package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.graph.collective.consolidation.chapter.ChapterCollectiveConsolidationHandler;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
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

@WebMvcTest(controllers = ChapterCollectiveConsolidationCommandController.class)
class ChapterCollectiveConsolidationCommandControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChapterCollectiveConsolidationHandler chapterCollectiveConsolidator;

    @MockitoBean
    private StepEventMapper stepEventMapper;

    @Test
    void consolidateChapterCollectivesSuccessReturns200() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterCollectiveConsolidator.execute(
                new StageExecutionContext(null, null, chapterId, null, StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION)))
                .thenReturn(StageResult.success(
                        StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION,
                        "Resolved chapter collectives",
                        Map.of("mentionCount", 3, "chapterCollectiveCount", 2),
                        150L
                ));

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/chapter-consolidate-collectives", chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.step").value("chapter-collective-consolidation"))
                .andExpect(jsonPath("$.scope").value("chapter"))
                .andExpect(jsonPath("$.scopeId").value(chapterId.toString()))
                .andExpect(jsonPath("$.counts.mentionCount").value(3))
                .andExpect(jsonPath("$.counts.chapterCollectiveCount").value(2));

        verify(chapterCollectiveConsolidator).execute(
                new StageExecutionContext(null, null, chapterId, null, StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION));
    }

    @Test
    void consolidateChapterCollectivesInvalidUuidReturns400() throws Exception {
        mockMvc.perform(post("/api/command/ingest/chapters/not-a-uuid/chapter-consolidate-collectives"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CHAPTER_ID"));
    }

    @Test
    void consolidateChapterCollectives_stageFailure_returns200WithFailureResult() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterCollectiveConsolidator.execute(
                new StageExecutionContext(null, null, chapterId, null, StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION)))
                .thenReturn(StageResult.failure(
                        StageKey.CHAPTER_COLLECTIVE_CONSOLIDATION, "Entity not found", 0L));

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/chapter-consolidate-collectives", chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.summary").value("Entity not found"));
    }
}