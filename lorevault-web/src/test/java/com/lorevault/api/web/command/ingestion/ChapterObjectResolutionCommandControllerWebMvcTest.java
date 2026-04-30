package com.lorevault.api.web.command.ingestion;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lorevault.api.ingestion.resolution.object.ChapterObjectResolutionResult;
import com.lorevault.api.ingestion.resolution.object.ChapterObjectResolutionService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ChapterObjectResolutionCommandController.class)
class ChapterObjectResolutionCommandControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChapterObjectResolutionService chapterObjectResolutionService;

    @Test
    void resolveChapterObjectsSuccessReturns200() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterObjectResolutionService.chapterExists(chapterId)).thenReturn(true);
        when(chapterObjectResolutionService.resolveChapter(chapterId))
                .thenReturn(new ChapterObjectResolutionResult(chapterId, true, 3, 2, "Resolved chapter objects"));

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/resolve-objects", chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chapterId").value(chapterId.toString()))
                .andExpect(jsonPath("$.processed").value(true))
                .andExpect(jsonPath("$.mentionCount").value(3))
                .andExpect(jsonPath("$.chapterObjectCount").value(2));

        verify(chapterObjectResolutionService).resolveChapter(chapterId);
    }

    @Test
    void resolveChapterObjectsInvalidUuidReturns400() throws Exception {
        mockMvc.perform(post("/api/command/ingest/chapters/not-a-uuid/resolve-objects"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CHAPTER_ID"));
    }

    @Test
    void resolveChapterObjectsMissingChapterReturns404() throws Exception {
        UUID chapterId = UUID.randomUUID();
        when(chapterObjectResolutionService.chapterExists(chapterId)).thenReturn(false);

        mockMvc.perform(post("/api/command/ingest/chapters/{chapterId}/resolve-objects", chapterId))
                .andExpect(status().isNotFound());
    }
}
