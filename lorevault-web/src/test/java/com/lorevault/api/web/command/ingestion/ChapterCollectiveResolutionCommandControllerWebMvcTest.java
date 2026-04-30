package com.lorevault.api.web.command.ingestion;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lorevault.api.ingestion.resolution.collective.ChapterCollectiveResolutionResult;
import com.lorevault.api.ingestion.resolution.collective.ChapterCollectiveResolutionService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ChapterCollectiveResolutionCommandController.class)
class ChapterCollectiveResolutionCommandControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChapterCollectiveResolutionService chapterCollectiveResolutionService;

    @Test
    void resolveChapterCollectivesSuccessReturns200() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterCollectiveResolutionService.chapterExists(chapterId)).thenReturn(true);
        when(chapterCollectiveResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterCollectiveResolutionResult(chapterId, true, 3, 2, "Resolved chapter collectives"));

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/resolve-collectives", chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapterId").value(chapterId.toString()))
                .andExpect(jsonPath("$.processed").value(true))
                .andExpect(jsonPath("$.mentionCount").value(3))
                .andExpect(jsonPath("$.chapterCollectiveCount").value(2));

        verify(chapterCollectiveResolutionService).resolveChapter(chapterId);
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
        when(chapterCollectiveResolutionService.chapterExists(chapterId)).thenReturn(false);

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/resolve-collectives", chapterId))
                .andExpect(status().isNotFound());
    }
}
