package com.lorevault.api.graph;

import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.domain.shared.PublicationCoordinates;
import com.lorevault.api.infrastructure.persistence.neo4j.model.*;
import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.service.ingestion.IngestionService;
import com.lorevault.api.test.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class Neo4jContentPersistenceAdapterIT extends IntegrationTestBase {

    @Autowired
    private ContentPersistencePort port;

    @Autowired
    private IngestionService ingestionService;

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

        // Use the production service to create a job with proper initialization
        SubmitChapterRequest request = new SubmitChapterRequest();
        request.setCoordinates(new PublicationCoordinates("TestU", "Series", "Book", "Chapter", 1, 1));
        request.setChapterTitle("Ch1");
        request.setChapterText("Some raw text for testing scenes and chunks.");
        SubmitChapterResponse response = ingestionService.submitChapter(request);
        assertThat(response.getJobId()).isNotNull();
        assertThat(response.getChapterId()).isNotNull();

        // Verify the job was created with proper status
        var createdJob = port.findJob(response.getJobId());
        assertThat(createdJob).isPresent();
        assertThat(createdJob.get().getCurrentStatusRecord()).isEqualTo(IngestionStatus.QUEUED);

        assertThat(port.hasActiveJobForChapter(response.getChapterId())).isTrue();
        assertThat(port.findMostRecentJobForChapter(response.getChapterId())).isPresent();

        assertThat(port.deleteChunksByChapterId(chapter.getId())).isEqualTo(1);
        assertThat(port.deleteScenesByChapterId(chapter.getId())).isEqualTo(1);
    }
}
