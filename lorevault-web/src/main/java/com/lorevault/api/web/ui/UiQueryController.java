package com.lorevault.api.web.ui;

import com.lorevault.api.web.query.ask.AskDtos;
import com.lorevault.api.web.query.ask.AskDtos.CitationDto;
import com.lorevault.api.web.query.ask.SemanticSearchDtos;
import com.lorevault.api.web.query.ask.SemanticSearchDtos.SearchResultDto;
import com.lorevault.api.library.service.LibraryQueryService;
import com.lorevault.api.search.model.CoreSearchRecords.CoreAskRequest;
import com.lorevault.api.search.model.CoreSearchRecords.CoreAskResponse;
import com.lorevault.api.search.model.CoreSearchRecords.CoreSemanticSearchRequest;
import com.lorevault.api.search.model.CoreSearchRecords.CoreSemanticSearchResponse;
import com.lorevault.api.search.model.SeriesProgress;
import com.lorevault.api.search.model.SpoilerVisibility;
import com.lorevault.api.search.model.UnconfiguredSeriesPolicy;
import com.lorevault.api.search.rag.RagService;
import com.lorevault.api.search.semantic.SemanticSearchService;
import com.lorevault.api.search.model.EntityLookupException;
import com.lorevault.api.search.model.SemanticSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/ui/query")
public class UiQueryController {

    private static final Logger log = LoggerFactory.getLogger(UiQueryController.class);

    private final RagService ragService;
    private final SemanticSearchService semanticSearchService;
    private final LibraryQueryService libraryQueryService;

    public UiQueryController(RagService ragService,
                             SemanticSearchService semanticSearchService,
                             LibraryQueryService libraryQueryService) {
        this.ragService = ragService;
        this.semanticSearchService = semanticSearchService;
        this.libraryQueryService = libraryQueryService;
    }

    @PostMapping("/ask/vector")
    public String askVector(@RequestParam("question") String question,
                            @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                            @RequestParam(value = "universeId", required = false) String universeId,
                            @RequestParam(value = "universe", required = false) String universe,
                            @RequestParam(value = "seriesId", required = false) String seriesId,
                            @RequestParam(value = "series", required = false) String series,
                            @RequestParam(value = "bookId", required = false) String bookId,
                            @RequestParam(value = "readThroughBookNumber", required = false) Integer readThroughBookNumber,
                            @RequestParam(value = "readThroughChapterNumber", required = false) String readThroughChapterNumber,
                            @RequestParam(value = "unconfiguredSeriesPolicy", defaultValue = "HIDE") UnconfiguredSeriesPolicy unconfiguredSeriesPolicy,
                            Model model) {
        SpoilerVisibility visibility = buildVisibility(universeId, universe, seriesId, series, bookId, readThroughBookNumber, readThroughChapterNumber, unconfiguredSeriesPolicy);
        CoreSemanticSearchRequest coreRequest = new CoreSemanticSearchRequest(
            question, topK, null, null, visibility
        );

        try {
            CoreSemanticSearchResponse coreResponse = semanticSearchService.search(coreRequest);
        
            List<SearchResultDto> results = coreResponse.results().stream()
                .map(r -> SearchResultDto.of(
                    r.chunkId(), r.score(), r.snippet(), r.chapterId(),
                    r.bookNumber(), r.chapterNumber(), r.sceneId(),
                    r.sceneSummary(), r.individualsPresent(), r.locationsPresent()
                ))
                .toList();
             
            SemanticSearchDtos.SearchMetadata metadata = SemanticSearchDtos.SearchMetadata.of(
                coreResponse.metadata().query(),
                coreResponse.metadata().totalResults(),
                coreResponse.metadata().returnedResults(),
                coreResponse.metadata().processingTimeMs()
            );
         
            SemanticSearchDtos.SemanticSearchResponse searchResponse = SemanticSearchDtos.SemanticSearchResponse.of(results, metadata);

            model.addAttribute("question", question);
            model.addAttribute("mode", "Vector retrieval");
            model.addAttribute("response", searchResponse);
            applyVisibilityModelAttributes(model, visibility);
            return "ui/query :: vectorResponse";
        } catch (SemanticSearchException e) {
            return serviceUnavailable(model, question, "Vector retrieval", visibility, e);
        }
    }

    @PostMapping("/ask/rag")
    public String askRag(@RequestParam("question") String question,
                         @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                         @RequestParam(value = "universeId", required = false) String universeId,
                         @RequestParam(value = "universe", required = false) String universe,
                         @RequestParam(value = "seriesId", required = false) String seriesId,
                         @RequestParam(value = "series", required = false) String series,
                         @RequestParam(value = "bookId", required = false) String bookId,
                         @RequestParam(value = "readThroughBookNumber", required = false) Integer readThroughBookNumber,
                         @RequestParam(value = "readThroughChapterNumber", required = false) String readThroughChapterNumber,
                         @RequestParam(value = "unconfiguredSeriesPolicy", defaultValue = "HIDE") UnconfiguredSeriesPolicy unconfiguredSeriesPolicy,
                         Model model) {
        SpoilerVisibility visibility = buildVisibility(universeId, universe, seriesId, series, bookId, readThroughBookNumber, readThroughChapterNumber, unconfiguredSeriesPolicy);
        CoreAskRequest coreRequest = new CoreAskRequest(
            question, topK, null, null, visibility
        );

        try {
            CoreAskResponse coreResponse = ragService.askRagBaseline(coreRequest);
            AskDtos.AskResponse askResponse = mapToAskResponse(coreResponse);

            model.addAttribute("question", question);
            model.addAttribute("mode", "RAG baseline");
            model.addAttribute("response", askResponse);
            applyVisibilityModelAttributes(model, visibility);
            return "ui/query :: ragResponse";
        } catch (SemanticSearchException | EntityLookupException e) {
            return serviceUnavailable(model, question, "RAG baseline", visibility, e);
        }
    }

    @PostMapping("/ask/graph-aware")
    public String askGraphAware(@RequestParam("question") String question,
                                @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                                @RequestParam(value = "universeId", required = false) String universeId,
                                @RequestParam(value = "universe", required = false) String universe,
                                @RequestParam(value = "seriesId", required = false) String seriesId,
                                @RequestParam(value = "series", required = false) String series,
                                @RequestParam(value = "bookId", required = false) String bookId,
                                @RequestParam(value = "readThroughBookNumber", required = false) Integer readThroughBookNumber,
                                @RequestParam(value = "readThroughChapterNumber", required = false) String readThroughChapterNumber,
                                @RequestParam(value = "unconfiguredSeriesPolicy", defaultValue = "HIDE") UnconfiguredSeriesPolicy unconfiguredSeriesPolicy,
                                Model model) {
        SpoilerVisibility visibility = buildVisibility(universeId, universe, seriesId, series, bookId, readThroughBookNumber, readThroughChapterNumber, unconfiguredSeriesPolicy);
        CoreAskRequest coreRequest = new CoreAskRequest(
            question, topK, null, null, visibility
        );

        try {
            CoreAskResponse coreResponse = ragService.askGraphAware(coreRequest);
            AskDtos.AskResponse askResponse = mapToAskResponse(coreResponse);

            model.addAttribute("question", question);
            model.addAttribute("mode", "Graph-aware");
            model.addAttribute("response", askResponse);
            applyVisibilityModelAttributes(model, visibility);
            return "ui/query :: ragResponse";
        } catch (SemanticSearchException | EntityLookupException e) {
            return serviceUnavailable(model, question, "Graph-aware", visibility, e);
        }
    }

    @PostMapping("/ask/hybrid")
    public String askHybrid(@RequestParam("question") String question,
                            @RequestParam(value = "topK", defaultValue = "5") Integer topK,
                            @RequestParam(value = "universeId", required = false) String universeId,
                            @RequestParam(value = "universe", required = false) String universe,
                            @RequestParam(value = "seriesId", required = false) String seriesId,
                            @RequestParam(value = "series", required = false) String series,
                            @RequestParam(value = "bookId", required = false) String bookId,
                            @RequestParam(value = "readThroughBookNumber", required = false) Integer readThroughBookNumber,
                            @RequestParam(value = "readThroughChapterNumber", required = false) String readThroughChapterNumber,
                            @RequestParam(value = "unconfiguredSeriesPolicy", defaultValue = "HIDE") UnconfiguredSeriesPolicy unconfiguredSeriesPolicy,
                            Model model) {
        SpoilerVisibility visibility = buildVisibility(universeId, universe, seriesId, series, bookId, readThroughBookNumber, readThroughChapterNumber, unconfiguredSeriesPolicy);
        CoreAskRequest coreRequest = new CoreAskRequest(
            question, topK, null, null, visibility
        );

        try {
            CoreAskResponse coreResponse = ragService.askHybrid(coreRequest);
            AskDtos.AskResponse askResponse = mapToAskResponse(coreResponse);

            model.addAttribute("question", question);
            model.addAttribute("mode", "Hybrid RRF");
            model.addAttribute("response", askResponse);
            applyVisibilityModelAttributes(model, visibility);
            return "ui/query :: ragResponse";
        } catch (SemanticSearchException | EntityLookupException e) {
            return serviceUnavailable(model, question, "Hybrid RRF", visibility, e);
        }
    }

    private String serviceUnavailable(Model model,
                                      String question,
                                      String mode,
                                      SpoilerVisibility visibility,
                                      RuntimeException exception) {
        log.warn("UI query unavailable for mode '{}' and question '{}': {}", mode, question, exception.getMessage());
        log.debug("UI query business failure details for mode '{}' and question '{}'", mode, question, exception);
        model.addAttribute("question", question);
        model.addAttribute("mode", mode);
        model.addAttribute("errorMessage", "Search is temporarily unavailable. Please try again.");
        applyVisibilityModelAttributes(model, visibility);
        return "ui/query :: queryError";
    }

    private void applyVisibilityModelAttributes(Model model, SpoilerVisibility visibility) {
        model.addAttribute("visibility", visibility);
        model.addAttribute("visibilitySummary", visibilitySummary(visibility));
    }

    private SpoilerVisibility buildVisibility(String universeId,
                                              String universe,
                                              String seriesId,
                                              String series,
                                              String bookId,
                                              Integer readThroughBookNumber,
                                              String readThroughChapterNumber,
                                              UnconfiguredSeriesPolicy unconfiguredSeriesPolicy) {
        String resolvedUniverse = resolveUniverseName(universeId).orElseGet(() -> trimToNull(universe));
        String resolvedSeries = resolveSeriesName(universeId, seriesId).orElseGet(() -> trimToNull(series));
        Integer resolvedBookNumber = readThroughBookNumber == null
                ? resolveBookNumber(bookId).orElse(null)
                : readThroughBookNumber;
        if (!StringUtils.hasText(resolvedUniverse) || resolvedBookNumber == null) {
            return null;
        }

        SeriesProgress progress = new SeriesProgress();
        progress.setSeries(resolvedSeries);
        progress.setReadThroughBookNumber(resolvedBookNumber);
        progress.setReadThroughChapterNumber(parseChapterNumber(readThroughChapterNumber));

        SpoilerVisibility visibility = new SpoilerVisibility();
        visibility.setUniverse(resolvedUniverse);
        visibility.setSeriesProgress(List.of(progress));
        visibility.setUnconfiguredSeriesPolicy(unconfiguredSeriesPolicy == null ? UnconfiguredSeriesPolicy.HIDE : unconfiguredSeriesPolicy);
        return visibility;
    }

    private Optional<String> resolveUniverseName(String universeId) {
        UUID resolvedUniverseId = parseUuid(universeId).orElse(null);
        if (resolvedUniverseId == null) {
            return Optional.empty();
        }
        return libraryQueryService.listUniverses().stream()
                .filter(universe -> resolvedUniverseId.equals(universe.id()))
                .map(LibraryQueryService.UniverseSummary::name)
                .findFirst();
    }

    private Optional<Integer> resolveBookNumber(String bookId) {
        UUID resolvedBookId = parseUuid(bookId).orElse(null);
        if (resolvedBookId == null) {
            return Optional.empty();
        }
        return libraryQueryService.listChaptersForBook(resolvedBookId).stream()
                .map(LibraryQueryService.ChapterSummary::bookNumber)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    private Optional<String> resolveSeriesName(String universeId, String seriesId) {
        UUID resolvedUniverseId = parseUuid(universeId).orElse(null);
        UUID resolvedSeriesId = parseUuid(seriesId).orElse(null);
        if (resolvedUniverseId == null || resolvedSeriesId == null) {
            return Optional.empty();
        }
        return libraryQueryService.listSeries(resolvedUniverseId).stream()
                .filter(series -> resolvedSeriesId.equals(series.id()))
                .map(LibraryQueryService.SeriesSummary::name)
                .findFirst();
    }

    private Optional<UUID> parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Integer parseChapterNumber(String readThroughChapterNumber) {
        if (!StringUtils.hasText(readThroughChapterNumber)) {
            return null;
        }
        return Integer.valueOf(readThroughChapterNumber.trim());
    }

    private String visibilitySummary(SpoilerVisibility visibility) {
        if (visibility == null || visibility.getSeriesProgress() == null || visibility.getSeriesProgress().isEmpty()) {
            return null;
        }

        SeriesProgress progress = visibility.getSeriesProgress().getFirst();
        String chapterSummary = progress.getReadThroughChapterNumber() == null
            ? "full book"
            : "chapter " + progress.getReadThroughChapterNumber();

        String seriesSummary = StringUtils.hasText(progress.getSeries()) ? progress.getSeries() : "Standalone";

        return visibility.getUniverse()
            + " • " + seriesSummary
            + " • book " + progress.getReadThroughBookNumber()
            + " (" + chapterSummary + ")"
            + " • unconfigured " + visibility.getUnconfiguredSeriesPolicy();
    }

    private AskDtos.AskResponse mapToAskResponse(CoreAskResponse coreResponse) {
        List<CitationDto> citations = coreResponse.citations().stream()
            .map(c -> CitationDto.of(
                c.chunkId(),
                c.score(),
                c.snippet(),
                c.coordinates()
            ))
            .toList();
            
        AskDtos.AskMetadata metadata = AskDtos.AskMetadata.of(
            coreResponse.metadata().question(),
            coreResponse.metadata().chunksRetrieved(),
            coreResponse.metadata().chunksUsed(),
            coreResponse.metadata().processingTimeMs(),
            coreResponse.metadata().modelId()
        );
        
        return AskDtos.AskResponse.of(coreResponse.answer(), citations, metadata);
    }
}
