package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.library.chapter.Chapter;
import com.lorevault.api.library.chapter.ChapterGraphRepository;
import com.lorevault.api.orchestration.pipeline.StageExecutionContext;
import com.lorevault.api.orchestration.pipeline.StageKey;
import com.lorevault.api.orchestration.pipeline.StageOperation;
import com.lorevault.api.orchestration.pipeline.StageResult;

import java.util.Map;
import java.util.Optional;
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
    private StageOperation chapterObjectResolutionOperation;

    @MockitoBean
    private StepEventMapper stepEventMapper;

    @MockitoBean
    private ChapterGraphRepository chapterGraphRepository;

    @Test
    void consolidateChapterObjectsSuccessReturns200() throws Exception {
        UUID chapterId = UUID.randomUUID();
        Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        when(chapterGraphRepository.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(chapterObjectResolutionOperation.execute(
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

        verify(chapterObjectResolutionOperation).execute(
                new StageExecutionContext(null, null, chapterId, null, StageKey.CHAPTER_OBJECT_CONSOLIDATION));
    }

    @Test
    void consolidateChapterObjectsInvalidUuidReturns400() throws Exception {
        mockMvc.perform(post("/api/command/ingest/chapters/not-a-uuid/chapter-consolidate-objects"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CHAPTER_ID"));
    }

    @Test
    void consolidateChapterObjectsMissingChapterReturns404() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterGraphRepository.findById(chapterId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/chapter-consolidate-objects", chapterId))
                .andExpect(status().isNotFound());
    }
}