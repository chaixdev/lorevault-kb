package com.lorevault.api.web.ui.view;

import com.lorevault.api.library.application.LibraryQueryService;

import java.util.UUID;

public record UniverseOption(UUID id, String name) {

    public static UniverseOption from(LibraryQueryService.UniverseSummary universe) {
        if (universe == null) {
            throw new IllegalArgumentException("Universe cannot be null");
        }
        return new UniverseOption(universe.id(), universe.name());
    }
}
