package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.ai.TextChunkingService;
import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.Chunk;
import com.lorevault.api.content.ChunkGraphRepository;
import com.lorevault.api.content.Scene;
import com.lorevault.api.content.SceneGraphRepository;
import com.lorevault.api.ingestion.events.ScenesDetectedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChunkingHandler")
class ChunkingHandlerTest {

    @Mock
    private ChapterGraphRepository chapterRepo;

    @Mock
    private ChunkGraphRepository chunkRepo;

    @Mock
    private SceneGraphRepository sceneRepo;

    @Mock
    private TextChunkingService textChunkingService;

    @Mock
    private IngestionJobService ingestionJobService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChunkingHandler handler;

    @Test
    @DisplayName("Writes chunkIndex onto HAS_CHUNK links in scene-local order")
    void writesChunkIndexWhenLinkingChunksToScene() {
        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        String chapterText = "Scene one text. Scene two text.";

        Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        chapter.setRawText(chapterText);

        Scene scene = new Scene(null, null, null, null, null, null, null, null, null, null, null, null);
        scene.setId(sceneId);
        scene.setSceneIndex(0);
        scene.setStartCharacterOffset(0L);
        scene.setEndCharacterOffset((long) chapterText.length());

        Chunk first = new Chunk(null, null, null, null, null, null, null, null, null, null, null);
        first.setChunkNumberInChapter(1);
        first.setStartCharInChapter(0);
        first.setEndCharInChapter(10);
        first.setText("Scene one ");

        Chunk second = new Chunk(null, null, null, null, null, null, null, null, null, null, null);
        second.setChunkNumberInChapter(2);
        second.setStartCharInChapter(10);
        second.setEndCharInChapter(20);
        second.setText("text. Scene");

        when(chunkRepo.existsForChapterViaScenes(chapterId)).thenReturn(false);
        when(chunkRepo.existsForChapter(chapterId)).thenReturn(false);
        when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(sceneRepo.findByChapterId(chapterId)).thenReturn(List.of(scene));
        when(textChunkingService.extractChunks(chapterText))
                .thenReturn(List.of(first, second));
        when(chunkRepo.save(any(Chunk.class))).thenAnswer(invocation -> {
            Chunk chunk = invocation.getArgument(0);
            if (chunk.getId() == null) {
                chunk.setId(UUID.randomUUID());
            }
            return chunk;
        });

        handler.handleScenesDetected(new ScenesDetectedEvent(this, jobId, chapterId, bookId, List.of(sceneId)));

        ArgumentCaptor<Chunk> chunkCaptor = ArgumentCaptor.forClass(Chunk.class);
        verify(chunkRepo, org.mockito.Mockito.times(2)).save(chunkCaptor.capture());
        List<Chunk> savedChunks = chunkCaptor.getAllValues();

        verify(sceneRepo).linkChunkToScene(eq(sceneId), eq(savedChunks.get(0).getId()), eq(1));
        verify(sceneRepo).linkChunkToScene(eq(sceneId), eq(savedChunks.get(1).getId()), eq(2));

        assertThat(savedChunks)
                .extracting(Chunk::getChunkNumberInChapter)
                .containsExactly(1, 2);
    }
}
