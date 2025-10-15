package com.lorevault.api.web.ui.view;

import com.lorevault.api.service.library.LibraryQueryService;

import java.util.UUID;

public record SeriesOption(UUID id, String name) {

    public static SeriesOption from(LibraryQueryService.SeriesSummary series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        return new SeriesOption(series.id(), series.name());
    }
}
