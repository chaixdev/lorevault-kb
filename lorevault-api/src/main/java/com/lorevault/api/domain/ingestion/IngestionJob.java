package com.lorevault.api.domain.ingestion;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestionJob {
    private UUID id;
    private UUID chapterId;
    private IngestionStatus currentStatus;
    private List<StatusRecord> statusHistory;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Integer progressPercent;
}
