package com.lorevault.api.graph;

import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.domain.ingestion.StatusRecord;
import com.lorevault.api.infrastructure.persistence.neo4j.model.*;
import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.test.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class Neo4jContentPersistenceAdapterMultiJobIT extends IntegrationTestBase {

    @Autowired
    private ContentPersistencePort port;

    @Test
    void multipleJobs_statusOrdering() {
        ChapterNode chapter = new ChapterNode();
        chapter.setId(UUID.randomUUID());
        chapter.setUniverse("U");
        chapter.setChapterTitle("Title");
        chapter.setRawText("Text...");
        chapter.setContentHash("hash-multi");
        chapter = port.createChapter(chapter);

        // Create jobs with proper initial status using production approach
        IngestionJobNode job1 = new IngestionJobNode(); 
        job1.setId(UUID.randomUUID()); 
        job1.setChapterId(chapter.getId());

        job1.setCurrentStatusRecord(new StatusRecordNode(UUID.randomUUID(),job1.getId(), LocalDateTime.now(),IngestionStatus.QUEUED,"queued",IngestionStatus.QUEUED.getProgressPercentage(),null));
        job1 = port.createJob(job1);
        
        IngestionJobNode job2 = new IngestionJobNode(); 
        job2.setId(UUID.randomUUID()); 
        job2.setChapterId(chapter.getId()); 
        job2.setCurrentStatusRecord(new StatusRecordNode(UUID.randomUUID(),job2.getId(), LocalDateTime.now(),IngestionStatus.QUEUED,"queued",IngestionStatus.QUEUED.getProgressPercentage(),null));
        job2 = port.createJob(job2);

        // Add status records (out of order creation) to ensure ordering retrieval works
        StatusRecordNode r2 = new StatusRecordNode(); r2.setId(UUID.randomUUID()); r2.setJobId(job2.getId()); r2.setStatus(IngestionStatus.PREPROCESSING_STARTED); r2.setStepDescription("preprocessing"); r2.setProgressPercent(10); port.addStatusRecord(job2.getId(), r2);
        StatusRecordNode r1 = new StatusRecordNode(); r1.setId(UUID.randomUUID()); r1.setJobId(job2.getId()); r1.setStatus(IngestionStatus.QUEUED); r1.setStepDescription("queued"); r1.setProgressPercent(0); port.addStatusRecord(job2.getId(), r1);

        List<StatusRecordNode> recent = port.findStatusHistoryForJob(job2.getId());
        assertThat(recent.size()).isEqualTo(2);
        assertThat(recent.get(0).getProgressPercent()).isGreaterThanOrEqualTo(recent.get(1).getProgressPercent());

        // Most recent job should be the later one (job2) assuming createdAt chronological
        assertThat(port.findMostRecentJobForChapter(chapter.getId()).map(IngestionJobNode::getId)).contains(job2.getId());
    }
}
