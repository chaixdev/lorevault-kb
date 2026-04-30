package com.lorevault.api.search.model;

import com.lorevault.api.library.book.PublicationCoordinates;

import java.util.List;
import java.util.UUID;

public class CoreSearchRecords {

    public record CoreAskRequest(
            String question,
            Integer topK,
            Double threshold,
            CoreAskFilters filters,
            SpoilerVisibility visibility
    ) {}

    public record CoreAskFilters(
            String universe,
            String series,
            Integer bookNumber,
            Integer chapterNumber
    ) {}

    public record CoreAskResponse(
            String answer,
            List<CoreCitation> citations,
            CoreAskMetadata metadata
    ) {}

    public record CoreCitation(
            UUID chunkId,
            double score,
            String snippet,
            PublicationCoordinates coordinates
    ) {}

    public record CoreAskMetadata(
            String question,
            int chunksRetrieved,
            int chunksUsed,
            long processingTimeMs,
            String modelId
    ) {}

    public record CoreSemanticSearchRequest(
            String query,
            Integer topK,
            Double threshold,
            CoreSemanticSearchFilters filters,
            SpoilerVisibility visibility
    ) {}

    public record CoreSemanticSearchFilters(
            String universe,
            String series,
            Integer bookNumber,
            Integer chapterNumber
    ) {}

    public record CoreSemanticSearchResponse(
            List<CoreSearchResult> results,
            CoreSearchMetadata metadata
    ) {}

    public record CoreSearchResult(
            UUID chunkId,
            double score,
            String snippet,
            UUID chapterId,
            Integer bookNumber,
            Integer chapterNumber,
            UUID sceneId,
            String sceneSummary,
            List<String> individualsPresent,
            List<String> locationsPresent
    ) {}

    public record CoreSearchMetadata(
            String query,
            int totalResults,
            int returnedResults,
            long processingTimeMs
    ) {}
}
