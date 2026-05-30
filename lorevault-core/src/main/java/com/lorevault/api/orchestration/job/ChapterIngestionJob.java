package com.lorevault.api.orchestration.job;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A stable correlation node for one chapter ingestion, replacing the old
 * {@code IngestionJob}.
 *
 * <p>No mutable orchestration state lives on this node — completion is derived
 * from the {@code Stage} subgraph. The {@code currentStatus} and
 * {@code completedAt} fields from the old {@code IngestionJob} are removed:
 * completion status comes from the {@code INGESTION_COMPLETE} Stage node.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Node("ChapterIngestionJob")
public class ChapterIngestionJob {

    @Id
    private UUID id;

    private UUID chapterId;

    @CreatedDate
    private LocalDateTime createdAt;
}
