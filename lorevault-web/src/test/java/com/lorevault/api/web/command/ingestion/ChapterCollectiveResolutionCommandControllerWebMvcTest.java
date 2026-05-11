package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.resolution.collective.ChapterCollectiveResolutionOperation;

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

@WebMvcTest(controllers = ChapterCollectiveResolutionCommandController.class)
class ChapterCollectiveResolutionCommandControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChapterCollectiveResolutionOperation chapterCollectiveResolutionOperation;

    @MockitoBean
    private StepEventMapper stepEventMapper;

    @MockitoBean
    private ChapterGraphRepository chapterGraphRepository;

    @Test
    void resolveChapterCollectivesSuccessReturns200() throws Exception {
        UUID chapterId = UUID.randomUUID();
        Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        when(chapterGraphRepository.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(chapterCollectiveResolutionOperation.execute(null, chapterId))
                .thenReturn(StepResult.success(
                        "RESOLVE_COLLECTIVES",
                        "Resolved chapter collectives",
                        Map.of("mentionCount", 3, "chapterCollectiveCount", 2),
                        150L
                ));

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/resolve-collectives", chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.step").value("resolve-collectives"))
                .andExpect(jsonPath("$.scope").value("chapter"))
                .andExpect(jsonPath("$.scopeId").value(chapterId.toString()))
                .andExpect(jsonPath("$.counts.mentionCount").value(3))
                .andExpect(jsonPath("$.counts.chapterCollectiveCount").value(2));

        verify(chapterCollectiveResolutionOperation).execute(null, chapterId);
    }

    @Test
    void resolveChapterCollectivesInvalidUuidReturns400() throws Exception {
        mockMvc.perform(post("/api/command/ingest/chapters/not-a-uuid/resolve-collectives"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CHAPTER_ID"));
    }

    @Test
    void resolveChapterCollectivesMissingChapterReturns404() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterGraphRepository.findById(chapterId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/resolve-collectives", chapterId))
                .andExpect(status().isNotFound());
    }
}