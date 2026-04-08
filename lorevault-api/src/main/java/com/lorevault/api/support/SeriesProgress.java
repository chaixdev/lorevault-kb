package com.lorevault.api.support;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A reader's reading progress within one series.
 * All chapters up to (and including) the specified book/chapter are visible;
 * anything beyond is treated as a spoiler.
 *
 * A null {@code readThroughChapterNumber} means the entire specified book is visible.
 */
@Data
public class SeriesProgress {

    @NotBlank
    @Size(max = 100)
    private String series;

    @Min(1)
    private Integer readThroughBookNumber;

    /** Null means the reader has finished the entire book. */
    private Integer readThroughChapterNumber;
}
