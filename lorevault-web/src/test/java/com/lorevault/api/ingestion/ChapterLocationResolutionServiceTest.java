package com.lorevault.api.ingestion;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.content.ChapterLocation;
import com.lorevault.api.content.ChapterLocationGraphRepository;
import com.lorevault.api.content.LocationMention;
import com.lorevault.api.content.LocationMentionGraphRepository;
import com.lorevault.api.ingestion.application.result.ChapterLocationResolutionResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterLocationResolutionService")
class ChapterLocationResolutionServiceTest {

    @Mock
    private ChapterLocationGraphRepository chapterLocationRepository;

    @Mock
    private LocationMentionGraphRepository locationMentionRepository;

    @InjectMocks
    private ChapterLocationResolutionService service;

    @Test
    @DisplayName("Merges mentions through exact normalized names and alias bridges")
    void mergesMentionsThroughExactNamesAndAliasBridges() {
        UUID chapterId = UUID.randomUUID();
        UUID rivendellAId = UUID.randomUUID();
        UUID rivendellBId = UUID.randomUUID();
        UUID lastHomelyHouseId = UUID.randomUUID();
        UUID shireId = UUID.randomUUID();

        LocationMention rivendell = mention(rivendellAId, chapterId, "Rivendell", "rivendell", List.of("Imladris"), 0);
        LocationMention lastHomelyHouse = mention(lastHomelyHouseId, chapterId, "The Last Homely House", "the last homely house", List.of("Rivendell"), 1);
        LocationMention imladris = mention(rivendellBId, chapterId, "Imladris", "imladris", List.of(), 2);
        LocationMention shire = mention(shireId, chapterId, "The Shire", "the shire", List.of(), 3);

        when(chapterLocationRepository.countMentionsByChapterId(chapterId)).thenReturn(4L);
        when(locationMentionRepository.findByChapterId(chapterId)).thenReturn(List.of(lastHomelyHouse, shire, imladris, rivendell));
        when(chapterLocationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterLocationRepository.countChapterLocationsByChapterId(chapterId)).thenReturn(2L);

        ChapterLocationResolutionResult response = service.resolveChapter(chapterId);

        assertThat(response.success()).isTrue();
        assertThat(response.rawLocationsProcessed()).isEqualTo(4);
        assertThat(response.chapterLocationsCreated()).isEqualTo(2);

        verify(chapterLocationRepository).deleteByChapterId(chapterId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ChapterLocation>> savedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(chapterLocationRepository).saveAll(savedCaptor.capture());
        List<ChapterLocation> saved = org.assertj.core.util.Lists.newArrayList(savedCaptor.getValue());

        assertThat(saved).hasSize(2);
        assertThat(saved)
                .extracting(ChapterLocation::displayName, ChapterLocation::normalizedName, ChapterLocation::mentionCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Imladris", "imladris", 3),
                        org.assertj.core.groups.Tuple.tuple("The Shire", "the shire", 1)
                );

        ChapterLocation mergedCluster = saved.stream()
                .filter(location -> "imladris".equals(location.normalizedName()))
                .findFirst()
                .orElseThrow();
        assertThat(mergedCluster.aliases()).containsExactly("Imladris", "Rivendell");

        ChapterLocation shireCluster = saved.stream()
                .filter(location -> "the shire".equals(location.normalizedName()))
                .findFirst()
                .orElseThrow();
        assertThat(shireCluster.aliases()).isEmpty();

        for (ChapterLocation chapterLocation : saved) {
            verify(chapterLocationRepository).linkChapterToLocation(chapterId, chapterLocation.id());
        }
        verify(chapterLocationRepository).linkMentionsToChapterLocation(List.of(rivendellBId, rivendellAId, lastHomelyHouseId), mergedCluster.id(), ChapterLocationResolutionService.CHAPTER_RESOLVED);
        verify(chapterLocationRepository).linkMentionsToChapterLocation(List.of(shireId), shireCluster.id(), ChapterLocationResolutionService.CHAPTER_RESOLVED);
    }

    @Test
    @DisplayName("Skips save when chapter mentions are present but none are resolvable")
    void skipsSaveWhenNoResolvableMentionsExist() {
        UUID chapterId = UUID.randomUUID();
        LocationMention blank = new LocationMention(
                UUID.randomUUID(),
                "ai-scene-analysis",
                " ",
                " ",
                List.of(" "),
                null,
                null,
                null,
                UUID.randomUUID(),
                chapterId,
                UUID.randomUUID(),
                "unresolved",
                0,
                null,
                null
        );

        when(chapterLocationRepository.countMentionsByChapterId(chapterId)).thenReturn(1L);
        when(locationMentionRepository.findByChapterId(chapterId)).thenReturn(List.of(blank));

        ChapterLocationResolutionResult response = service.resolveChapter(chapterId);

        assertThat(response.success()).isFalse();
        assertThat(response.rawLocationsProcessed()).isEqualTo(1);
        assertThat(response.chapterLocationsCreated()).isZero();

        verify(chapterLocationRepository).deleteByChapterId(chapterId);
        verify(chapterLocationRepository, never()).saveAll(any());
        verify(chapterLocationRepository, never()).linkChapterToLocation(any(), any());
        verify(chapterLocationRepository, never()).linkMentionsToChapterLocation(any(), any(), any());
    }

    @Test
    @DisplayName("Returns no-op response when chapter has no location mentions")
    void returnsNoOpWhenChapterHasNoMentions() {
        UUID chapterId = UUID.randomUUID();
        when(chapterLocationRepository.countMentionsByChapterId(chapterId)).thenReturn(0L);

        ChapterLocationResolutionResult response = service.resolveChapter(chapterId);

        assertThat(response.success()).isFalse();
        assertThat(response.rawLocationsProcessed()).isZero();
        assertThat(response.chapterLocationsCreated()).isZero();

        verify(chapterLocationRepository, never()).deleteByChapterId(any());
        verify(locationMentionRepository, never()).findByChapterId(any());
    }

    private LocationMention mention(
            UUID id,
            UUID chapterId,
            String displayName,
            String normalizedName,
            List<String> aliases,
            int extractionIndex
    ) {
        return new LocationMention(
                id,
                "ai-scene-analysis",
                displayName,
                normalizedName,
                aliases,
                "settlement",
                "Eriador",
                null,
                UUID.randomUUID(),
                chapterId,
                UUID.randomUUID(),
                "unresolved",
                extractionIndex,
                null,
                null
        );
    }
}
