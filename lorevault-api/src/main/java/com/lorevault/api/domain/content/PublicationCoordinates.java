package com.lorevault.api.domain.content;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicationCoordinates {
    private String universe;
    private String series;
    private String bookTitle;
    private Integer bookNumber;
    private Integer chapterNumber;
    private LocalDate publicationDate;
}
