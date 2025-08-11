package com.lorevault.api.graph;

import com.lorevault.api.graph.model.*;
import com.lorevault.api.graph.port.ContentPersistencePort;
import com.lorevault.api.test.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class Neo4jContentPersistenceAdapterIT extends IntegrationTestBase {

    @Autowired
    private ContentPersistencePort port;

    @Test
    void chapter_scene_chunk_job_lifecycle() {
        ChapterNode chapter = new ChapterNode();
        chapter.setId(UUID.randomUUID());
        chapter.setUniverse("TestU");
        chapter.setChapterTitle("Ch1");
        chapter.setRawText("Some raw text for testing scenes and chunks.");
        chapter.setContentHash("hash123");
        chapter = port.createChapter(chapter);
        assertThat(chapter.getId()).isNotNull();

        SceneNode scene = new SceneNode();
        scene.setId(UUID.randomUUID());
        scene.setSceneIndex(0);
        scene.setStartOffset(0L);
        scene.setEndOffset((long) chapter.getRawText().length());
        scene.setContextSummary("Summary");
        port.addSceneToChapter(chapter.getId(), scene);
        List<SceneNode> scenes = port.findScenesByChapterId(chapter.getId());
        assertThat(scenes).hasSize(1);

        ChunkNode chunk = new ChunkNode();
        chunk.setId(UUID.randomUUID());
        chunk.setChunkNumberInChapter(1);
        chunk.setStartCharInChapter(0);
        chunk.setEndCharInChapter(10);
        chunk.setContentHash("chunkHash");
        port.addChunksToChapter(chapter.getId(), List.of(chunk));
        assertThat(port.countChunksByChapterId(chapter.getId())).isEqualTo(1);

        IngestionJobNode job = new IngestionJobNode();
        job.setId(UUID.randomUUID());
        job.setChapterId(chapter.getId());
        job.setCurrentStatus(null);
        job.setProgressPercent(0);
        job = port.createJob(job);
        assertThat(job.getId()).isNotNull();

        StatusRecordNode record = new StatusRecordNode();
        record.setId(UUID.randomUUID());
        record.setJobId(job.getId());
        record.setStatus(null);
        record.setStepDescription("Queued");
        record.setProgressPercent(0);
        port.addStatusRecord(job.getId(), record);
        assertThat(port.findRecentStatusRecords(job.getId(), 5)).hasSize(1);

        assertThat(port.hasActiveJobForChapter(chapter.getId())).isTrue();
        assertThat(port.findMostRecentJobForChapter(chapter.getId())).isPresent();

        assertThat(port.deleteChunksByChapterId(chapter.getId())).isEqualTo(1);
        assertThat(port.deleteScenesByChapterId(chapter.getId())).isEqualTo(1);
    }
}
