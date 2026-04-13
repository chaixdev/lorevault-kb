package com.lorevault.api.ingestion;

import com.lorevault.api.ai.TextChunkingService;
import com.lorevault.api.content.Chapter;
import com.lorevault.api.content.ChapterGraphRepository;
import com.lorevault.api.content.Chunk;
import com.lorevault.api.content.ChunkGraphRepository;
import com.lorevault.api.content.Scene;
import com.lorevault.api.content.SceneGraphRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanWrapperImpl;
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

        Chapter chapter = new Chapter();
        BeanWrapperImpl chapterBean = new BeanWrapperImpl(chapter);
        chapterBean.setPropertyValue("id", chapterId);
        chapterBean.setPropertyValue("rawText", "Scene one text. Scene two text.");

        Scene scene = new Scene(null, null, null, null, null, null, null, null, null, null, null, null);
        BeanWrapperImpl sceneBean = new BeanWrapperImpl(scene);
        sceneBean.setPropertyValue("id", sceneId);
        sceneBean.setPropertyValue("sceneIndex", 0);
        sceneBean.setPropertyValue("startCharacterOffset", 0L);
        sceneBean.setPropertyValue("endCharacterOffset", 29L);

        Chunk first = new Chunk(null, null, null, null, null, null, null, null, null, null, null);
        BeanWrapperImpl firstBean = new BeanWrapperImpl(first);
        firstBean.setPropertyValue("chunkNumberInChapter", 1);
        firstBean.setPropertyValue("startCharInChapter", 0);
        firstBean.setPropertyValue("endCharInChapter", 10);
        firstBean.setPropertyValue("text", "Scene one ");

        Chunk second = new Chunk(null, null, null, null, null, null, null, null, null, null, null);
        BeanWrapperImpl secondBean = new BeanWrapperImpl(second);
        secondBean.setPropertyValue("chunkNumberInChapter", 2);
        secondBean.setPropertyValue("startCharInChapter", 10);
        secondBean.setPropertyValue("endCharInChapter", 20);
        secondBean.setPropertyValue("text", "text. Scene");

        when(chunkRepo.existsForChapterViaScenes(chapterId)).thenReturn(false);
        when(chunkRepo.existsForChapter(chapterId)).thenReturn(false);
        when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(sceneRepo.findByChapterId(chapterId)).thenReturn(List.of(scene));
        when(textChunkingService.extractChunks("Scene one text. Scene two text"))
                .thenReturn(List.of(first, second));
        when(chunkRepo.save(any(Chunk.class))).thenAnswer(invocation -> {
            Chunk chunk = invocation.getArgument(0);
            BeanWrapperImpl chunkBean = new BeanWrapperImpl(chunk);
            if (chunkBean.getPropertyValue("id") == null) {
                chunkBean.setPropertyValue("id", UUID.randomUUID());
            }
            return chunk;
        });

        handler.handleScenesDetected(new ScenesDetectedEvent(this, jobId, chapterId, bookId, List.of(sceneId)));

        ArgumentCaptor<Chunk> chunkCaptor = ArgumentCaptor.forClass(Chunk.class);
        verify(chunkRepo, org.mockito.Mockito.times(2)).save(chunkCaptor.capture());
        List<Chunk> savedChunks = chunkCaptor.getAllValues();

        verify(sceneRepo).linkChunkToScene(eq(sceneId), eq((UUID) new BeanWrapperImpl(savedChunks.get(0)).getPropertyValue("id")), eq(1));
        verify(sceneRepo).linkChunkToScene(eq(sceneId), eq((UUID) new BeanWrapperImpl(savedChunks.get(1)).getPropertyValue("id")), eq(2));

        assertThat(savedChunks)
                .extracting(chunk -> new BeanWrapperImpl(chunk).getPropertyValue("chunkNumberInChapter"))
                .containsExactly(1, 2);
    }
}
