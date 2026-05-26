package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.pipeline.StepResult;
import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualResolutionOperation;

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

@WebMvcTest(controllers = ChapterIndividualResolutionCommandController.class)
class ChapterIndividualResolutionCommandControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChapterIndividualResolutionOperation chapterIndividualResolutionOperation;

    @MockitoBean
    private StepEventMapper stepEventMapper;

    @MockitoBean
    private ChapterGraphRepository chapterGraphRepository;

    @Test
    void resolveChapterIndividuals_success_returns200() throws Exception {
        UUID chapterId = UUID.randomUUID();
        Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        when(chapterGraphRepository.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(chapterIndividualResolutionOperation.execute(null, chapterId))
                .thenReturn(StepResult.success(
                        StageKey.CHAPTER_INDIVIDUAL_RESOLUTION,
                        "Resolved chapter individual mentions",
                        Map.of("mentionCount", 3, "chapterIndividualCount", 2),
                        150L
                ));

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/resolve-individuals", chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.step").value("resolve-individuals"))
                .andExpect(jsonPath("$.scope").value("chapter"))
                .andExpect(jsonPath("$.scopeId").value(chapterId.toString()))
                .andExpect(jsonPath("$.counts.mentionCount").value(3))
                .andExpect(jsonPath("$.counts.chapterIndividualCount").value(2));

        verify(chapterIndividualResolutionOperation).execute(null, chapterId);
    }

    @Test
    void resolveChapterIndividuals_invalidUuid_returns400() throws Exception {
        mockMvc.perform(post("/api/command/ingest/chapters/not-a-uuid/resolve-individuals"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CHAPTER_ID"));
    }

    @Test
    void resolveChapterIndividuals_missingChapter_returns404() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterGraphRepository.findById(chapterId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/resolve-individuals", chapterId))
                .andExpect(status().isNotFound());
    }
}