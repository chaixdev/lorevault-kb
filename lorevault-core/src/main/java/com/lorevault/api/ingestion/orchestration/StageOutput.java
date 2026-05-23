package com.lorevault.api.ingestion.orchestration;

import com.lorevault.api.ingestion.pipeline.StageKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable proof-of-work node created when a Stage completes successfully.
 *
 * <p>Keyed by {@code (chapterId, step)} for chapter-level stages, or
 * {@code (bookId, step)} for book-level stages. Multiple StageOutputs for
 * the same key can exist — they form an audit trail showing each completed
 * run of a stage.
 *
 * <p>Used by idempotency checks: if a StageOutput already exists for this
 * chapter/book and step (and the stage was not cascade-invalidated), the
 * handler SKIPs execution. Stale StageOutputs are deleted during cascade
 * invalidation alongside stale Stage nodes.
 *
 * <p>Distinguishing chapter-level vs book-level: exactly one of
 * {@code chapterId} or {@code bookId} should be set. If both are set,
 * the node is treated as chapter-level for lookup purposes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Node("StageOutput")
public class StageOutput {

    @Id
    private UUID id;

    /** For chapter-level stages. Null for book-level stages. */
    private UUID chapterId;

    /** For book-level stages. Null for chapter-level stages. */
    private UUID bookId;

    /** Which DAG vertex this output proves was completed. */
    private StageKey step;

    /** When the stage completed (when the coordinator wrote the result). */
    @CreatedDate
    private LocalDateTime completedAt;

    // ── Helper factories ────────────────────────────────────────────

    /** Chapter-level StageOutput. */
    public static StageOutput forChapter(UUID chapterId, StageKey step, LocalDateTime completedAt) {
        return StageOutput.builder()
                .id(UUID.randomUUID())
                .chapterId(chapterId)
                .step(step)
                .completedAt(completedAt)
                .build();
    }

    /** Book-level StageOutput. */
    public static StageOutput forBook(UUID bookId, StageKey step, LocalDateTime completedAt) {
        return StageOutput.builder()
                .id(UUID.randomUUID())
                .bookId(bookId)
                .step(step)
                .completedAt(completedAt)
                .build();
    }
}
