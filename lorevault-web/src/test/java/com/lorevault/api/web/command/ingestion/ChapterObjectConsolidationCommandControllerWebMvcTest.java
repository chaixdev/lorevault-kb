package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.graph.object.consolidation.chapter.ChapterObjectConsolidationHandler;
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

@WebMvcTest(controllers = ChapterObjectConsolidationCommandController.class)
class ChapterObjectConsolidationCommandControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChapterObjectConsolidationHandler chapterObjectConsolidator;

    @MockitoBean
    private StageEventMapper stepEventMapper;

    @Test
    void consolidateChapterObjectsSuccessReturns200() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterObjectConsolidator.execute(
                new StageExecutionContext(null, null, chapterId, null, StageKey.CHAPTER_OBJECT_CONSOLIDATION)))
                .thenReturn(StageResult.success(
                        StageKey.CHAPTER_OBJECT_CONSOLIDATION,
                        "Resolved chapter objects",
                        Map.of("mentionCount", 3, "chapterObjectCount", 2),
                        150L
                ));

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/chapter-consolidate-objects", chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.step").value("chapter-object-consolidation"))
                .andExpect(jsonPath("$.scope").value("chapter"))
                .andExpect(jsonPath("$.scopeId").value(chapterId.toString()))
                .andExpect(jsonPath("$.counts.mentionCount").value(3))
                .andExpect(jsonPath("$.counts.chapterObjectCount").value(2));

        verify(chapterObjectConsolidator).execute(
                new StageExecutionContext(null, null, chapterId, null, StageKey.CHAPTER_OBJECT_CONSOLIDATION));
    }

    @Test
    void consolidateChapterObjectsInvalidUuidReturns400() throws Exception {
        mockMvc.perform(post("/api/command/ingest/chapters/not-a-uuid/chapter-consolidate-objects"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CHAPTER_ID"));
    }

    @Test
    void consolidateChapterObjects_stageFailure_returns200WithFailureResult() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterObjectConsolidator.execute(
                new StageExecutionContext(null, null, chapterId, null, StageKey.CHAPTER_OBJECT_CONSOLIDATION)))
                .thenReturn(StageResult.failure(
                        StageKey.CHAPTER_OBJECT_CONSOLIDATION, "Entity not found", 0L));

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/chapter-consolidate-objects", chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.summary").value("Entity not found"));
    }
}