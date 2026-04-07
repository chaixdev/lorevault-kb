package com.lorevault.api.domain.content;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a specific book within a series or standalone within a universe.
 * Stable UUID allows uniform references from chapters/chunks.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Node("Book")
public class Book {
    @Id
    private UUID id;

    /**
     * Stable UUID references for graph relationships
     */
    private UUID universeId;
    private UUID seriesId; // optional - null for standalone books
    
    /**
     * Display metadata - kept for human-readable context and spoiler gating
     */
    private String universe; // display name
    private String series;   // optional display name

    private Integer bookNumber; // sequence within series - essential for spoiler gating
    private String title;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String displayLabel() {
        String seriesPart = (series == null || series.isBlank()) ? "" : (series + " ");
        String numPart = (bookNumber == null) ? "" : ("#" + bookNumber + " ");
        return (seriesPart + numPart + (title == null ? "" : title)).trim();
    }

    /**
     * Factory method to create a book within a series.
     */
    public static Book createInSeries(UUID universeId, String universeName, 
                                     UUID seriesId, String seriesName,
                                     Integer bookNumber, String title) {
        Book b = new Book();
        b.setId(UUID.randomUUID());
        b.setUniverseId(universeId);
        b.setSeriesId(seriesId);
        b.setUniverse(universeName);
        b.setSeries(seriesName);
        b.setBookNumber(bookNumber);
        b.setTitle(title);
        b.setCreatedAt(LocalDateTime.now());
        b.setUpdatedAt(b.getCreatedAt());
        return b;
    }

    /**
     * Factory method to create a standalone book in a universe.
     */
    public static Book createStandalone(UUID universeId, String universeName, String title) {
        Book b = new Book();
        b.setId(UUID.randomUUID());
        b.setUniverseId(universeId);
        b.setSeriesId(null); // standalone
        b.setUniverse(universeName);
        b.setSeries(null);
        b.setBookNumber(null);
        b.setTitle(title);
        b.setCreatedAt(LocalDateTime.now());
        b.setUpdatedAt(b.getCreatedAt());
        return b;
    }
}
