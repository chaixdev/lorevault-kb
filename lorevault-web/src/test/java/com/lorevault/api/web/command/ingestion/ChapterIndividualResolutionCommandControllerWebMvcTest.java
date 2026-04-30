package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualResolutionResult;
import com.lorevault.api.ingestion.resolution.individual.ChapterIndividualResolutionService;

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
    private ChapterIndividualResolutionService chapterIndividualResolutionService;

    @Test
    void resolveChapterIndividuals_success_returns200() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterIndividualResolutionService.chapterExists(chapterId)).thenReturn(true);
        when(chapterIndividualResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterIndividualResolutionResult(
                        chapterId,
                        true,
                        3,
                        2,
                        "Resolved chapter individual mentions"
                ));

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/resolve-individuals", chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapterId").value(chapterId.toString()))
                .andExpect(jsonPath("$.processed").value(true))
                .andExpect(jsonPath("$.mentionCount").value(3))
                .andExpect(jsonPath("$.chapterIndividualCount").value(2));

        verify(chapterIndividualResolutionService).resolveChapter(chapterId);
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
        when(chapterIndividualResolutionService.chapterExists(chapterId)).thenReturn(false);

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/resolve-individuals", chapterId))
                .andExpect(status().isNotFound());
    }
}
