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
    private String contentHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public int getLength() { return endCharInChapter - startCharInChapter; }
}
