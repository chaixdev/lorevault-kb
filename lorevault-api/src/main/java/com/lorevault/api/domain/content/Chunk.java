package com.lorevault.api.domain.content;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Chunk {
    private UUID id;
    private Chapter chapter;
    private Scene scene;
    private Integer chunkNumberInChapter;
    private Integer startCharInChapter;
    private Integer endCharInChapter;
    
    /**
     * The actual text content of this chunk, materialized for embedding independence
     * This supports the distributed content storage model where chunks are decoupled
     * from their source chapter and store their own embeddable text content.
     */
    private String text;
    
    private String contentHash;
    private double[] embedding;
    private String embeddingHash;
    private LocalDateTime embeddedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public int getLength() { return endCharInChapter - startCharInChapter; }
}
