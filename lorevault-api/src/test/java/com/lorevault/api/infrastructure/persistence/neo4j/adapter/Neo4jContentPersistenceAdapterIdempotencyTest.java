package com.lorevault.api.infrastructure.persistence.neo4j.adapter;

import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.content.Scene;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChapterNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.ChunkNode;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneHasChunk;
import com.lorevault.api.infrastructure.persistence.neo4j.model.SceneNode;
import com.lorevault.api.infrastructure.persistence.neo4j.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Neo4jContentPersistenceAdapterIdempotencyTest {

    @Mock private ChapterGraphRepository chapterRepo;
    @Mock private SceneGraphRepository sceneRepo;
    @Mock private ChunkGraphRepository chunkRepo;
    @Mock private UniverseGraphRepository universeRepo;
    @Mock private SeriesGraphRepository seriesRepo;
    @Mock private BookGraphRepository bookRepo;
    @Mock private ChapterReadRepository chapterReadRepo;
    @Mock private TemporalReadRepository temporalReadRepo;
    @Mock private Neo4jMapper mapper;

    private Neo4jContentPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new Neo4jContentPersistenceAdapter(
                chapterRepo,
                sceneRepo,
                chunkRepo,
                universeRepo,
                seriesRepo,
                bookRepo,
                chapterReadRepo,
                temporalReadRepo,
                mapper
        );
    }

    @Test
    void addScenesToChapter_dedupesHasSceneBySceneId_onRetries() {
        UUID chapterId = UUID.randomUUID();
        UUID sceneId1 = UUID.randomUUID();
        UUID sceneId2 = UUID.randomUUID();

        ChapterNode chapterNode = new ChapterNode();
        chapterNode.setId(chapterId);
        chapterNode.setScenes(new ArrayList<>());

        SceneNode existingScene = new SceneNode();
        existingScene.setId(sceneId1);
        chapterNode.getScenes().add(existingScene);

        when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(chapterNode));
        when(sceneRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(chapterRepo.save(any(ChapterNode.class))).thenAnswer(inv -> inv.getArgument(0));

        Scene s1 = new Scene();
        s1.setId(sceneId1);
        Scene s2 = new Scene();
        s2.setId(sceneId2);

        when(mapper.toNode(s1)).thenAnswer(inv -> {
            SceneNode n = new SceneNode();
            n.setId(sceneId1);
            return n;
        });
        when(mapper.toNode(s2)).thenAnswer(inv -> {
            SceneNode n = new SceneNode();
            n.setId(sceneId2);
            return n;
        });

        adapter.addScenesToChapter(chapterId, List.of(s1, s2));
        assertThat(chapterNode.getScenes()).extracting(SceneNode::getId)
                .containsExactlyInAnyOrder(sceneId1, sceneId2);

        // Retry with same scene IDs should not duplicate relationships
        adapter.addScenesToChapter(chapterId, List.of(s1, s2));
        assertThat(chapterNode.getScenes()).hasSize(2);
    }

    @Test
    void addChunksToChapter_dedupesHasChunkByChunkId_andSortsNullsLast() {
        UUID chapterId = UUID.randomUUID();
        UUID chunkId1 = UUID.randomUUID();
        UUID chunkId2 = UUID.randomUUID();
        UUID chunkId3 = UUID.randomUUID();

        ChapterNode chapterNode = new ChapterNode();
        chapterNode.setId(chapterId);
        chapterNode.setChunks(new ArrayList<>());

        ChunkNode existing = new ChunkNode();
        existing.setId(chunkId1);
        existing.setChunkNumberInChapter(1);
        chapterNode.getChunks().add(existing);

        when(chapterRepo.findById(chapterId)).thenReturn(Optional.of(chapterNode));
        when(chunkRepo.saveAll(anyList())).thenReturn(List.of());
        when(chapterRepo.save(any(ChapterNode.class))).thenAnswer(inv -> inv.getArgument(0));

        Chunk c1 = new Chunk();
        c1.setId(chunkId1);
        c1.setChunkNumberInChapter(1);

        Chunk c2 = new Chunk();
        c2.setId(chunkId2);
        c2.setChunkNumberInChapter(null);

        Chunk c3 = new Chunk();
        c3.setId(chunkId3);
        c3.setChunkNumberInChapter(2);

        when(mapper.toNode(c1)).thenAnswer(inv -> {
            ChunkNode n = new ChunkNode();
            n.setId(chunkId1);
            n.setChunkNumberInChapter(1);
            return n;
        });
        when(mapper.toNode(c2)).thenAnswer(inv -> {
            ChunkNode n = new ChunkNode();
            n.setId(chunkId2);
            n.setChunkNumberInChapter(null);
            return n;
        });
        when(mapper.toNode(c3)).thenAnswer(inv -> {
            ChunkNode n = new ChunkNode();
            n.setId(chunkId3);
            n.setChunkNumberInChapter(2);
            return n;
        });

        adapter.addChunksToChapter(chapterId, List.of(c1, c2, c3));
        assertThat(chapterNode.getChunks()).hasSize(3);
        assertThat(chapterNode.getChunks().get(0).getId()).isEqualTo(chunkId1);
        assertThat(chapterNode.getChunks().get(1).getId()).isEqualTo(chunkId3);
        assertThat(chapterNode.getChunks().get(2).getId()).isEqualTo(chunkId2);

        // Retry should not duplicate
        adapter.addChunksToChapter(chapterId, List.of(c1, c2, c3));
        assertThat(chapterNode.getChunks()).hasSize(3);
    }

    @Test
    void addChunkToScene_isIdempotent_andDoesNotSetChunkIndexWhenChunkNumberNull() {
        UUID sceneId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        SceneNode sceneNode = new SceneNode();
        sceneNode.setId(sceneId);
        sceneNode.setChunks(new ArrayList<>());

        when(sceneRepo.findById(sceneId)).thenReturn(Optional.of(sceneNode));
        when(sceneRepo.save(any(SceneNode.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chunkRepo.save(any(ChunkNode.class))).thenAnswer(inv -> inv.getArgument(0));

        Chunk chunk = new Chunk();
        chunk.setId(chunkId);
        chunk.setChunkNumberInChapter(null);

        when(mapper.toNode(chunk)).thenAnswer(inv -> {
            ChunkNode n = new ChunkNode();
            n.setId(chunkId);
            n.setChunkNumberInChapter(null);
            return n;
        });

        adapter.addChunkToScene(sceneId, chunk);

        ArgumentCaptor<SceneNode> sceneCaptor = ArgumentCaptor.forClass(SceneNode.class);
        verify(sceneRepo, atLeastOnce()).save(sceneCaptor.capture());
        SceneNode saved = sceneCaptor.getValue();

        assertThat(saved.getChunks()).hasSize(1);
        SceneHasChunk rel = saved.getChunks().get(0);
        assertThat(rel.getChunk()).isNotNull();
        assertThat(rel.getChunk().getId()).isEqualTo(chunkId);
        assertThat(rel.getChunkIndex()).isNull();

        // Retry should not duplicate the relationship
        adapter.addChunkToScene(sceneId, chunk);
        assertThat(sceneNode.getChunks()).hasSize(1);
    }
}
