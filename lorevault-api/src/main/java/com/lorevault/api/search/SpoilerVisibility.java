package com.lorevault.api.search;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SpoilerVisibility {

    @NotBlank
    @Size(max = 100)
    private String universe;

    @NotNull
    @Valid
    private List<SeriesProgress> seriesProgress;

    private UnconfiguredSeriesPolicy unconfiguredSeriesPolicy = UnconfiguredSeriesPolicy.HIDE;
}
