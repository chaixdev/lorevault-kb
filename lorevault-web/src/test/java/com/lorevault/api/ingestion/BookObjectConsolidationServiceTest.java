package com.lorevault.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lorevault.api.content.association.BookObject;
import com.lorevault.api.content.association.ChapterObject;
import com.lorevault.api.content.association.ChapterObjectGraphRepository;
import com.lorevault.api.ingestion.pipeline.StageExecutionContext;
import com.lorevault.api.ingestion.pipeline.StageKey;
import com.lorevault.api.ingestion.resolution.object.BookObjectPersistenceService;
import com.lorevault.api.ingestion.resolution.object.BookObjectConsolidationService;
import com.lorevault.api.ingestion.resolution.object.BookObjectConsolidationResult;
import com.lorevault.api.library.book.BookGraphRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Retryable;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookObjectConsolidationService")
class BookObjectConsolidationServiceTest {

    @Mock
    private BookGraphRepository bookGraphRepository;

    @Mock
    private ChapterObjectGraphRepository chapterObjectRepository;

    @Mock
    private BookObjectPersistenceService bookObjectPersistenceService;

    private static final StageExecutionContext CTX = new StageExecutionContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            StageKey.BOOK_OBJECT_CONSOLIDATION);

    @InjectMocks
    private BookObjectConsolidationService service;

    @Test
    @DisplayName("Rebuilds book objects by normalized name only")
    void rebuildsBookObjectsByNormalizedNameOnly() {
        UUID bookId = UUID.randomUUID();
        UUID chapterAId = UUID.randomUUID();
        UUID chapterBId = UUID.randomUUID();
        UUID swordAId = UUID.randomUUID();
        UUID swordBId = UUID.randomUUID();
        UUID doorId = UUID.randomUUID();

        ChapterObject swordA = chapterObject(swordAId, chapterAId, "Silver Sword", "silver sword", List.of("Moonblade"), "weapon", "silver", "duel", "A named blade", 2);
        ChapterObject swordB = chapterObject(swordBId, chapterBId, "Moonblade", "silver sword", List.of("Silver Sword"), null, null, "ritual", "Alias mention", 1);
        ChapterObject door = chapterObject(doorId, chapterAId, "Stone Door", "stone door", List.of("gate"), "door", "stone", "barrier", "Blocks the hall", 1);

        when(chapterObjectRepository.findByBookId(bookId)).thenReturn(List.of(door, swordB, swordA));
        when(bookObjectPersistenceService.replaceBookObjects(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(bookObjectPersistenceService.countByBookId(bookId)).thenReturn(2L);

        BookObjectConsolidationResult response = service.consolidateBook(CTX, bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterObjectsProcessed()).isEqualTo(3);
        assertThat(response.bookObjectsCreated()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BookObject>> savedCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<UUID>>> linkedIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bookObjectPersistenceService).replaceBookObjects(eq(bookId), savedCaptor.capture(), linkedIdsCaptor.capture());

        List<BookObject> saved = savedCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved)
                .extracting(BookObject::displayName, BookObject::normalizedName, BookObject::chapterObjectCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Moonblade", "silver sword", 2),
                        org.assertj.core.groups.Tuple.tuple("Stone Door", "stone door", 1)
                );

        BookObject swordCluster = saved.stream()
                .filter(object -> "silver sword".equals(object.normalizedName()))
                .findFirst()
                .orElseThrow();
        assertThat(swordCluster.aliases()).containsExactly("Silver Sword", "Moonblade");
        assertThat(swordCluster.type()).isEqualTo("weapon");
        assertThat(swordCluster.material()).isEqualTo("silver");
        assertThat(swordCluster.purpose()).isEqualTo("ritual");
        assertThat(swordCluster.description()).isEqualTo("Alias mention");
        assertThat(swordCluster.representativeChapterObjectId()).isEqualTo(swordBId);
        assertThat(swordCluster.firstSeenChapterId()).isEqualTo(chapterBId);

        assertThat(linkedIdsCaptor.getValue()).satisfiesExactly(
                ids -> assertThat(ids).containsExactlyInAnyOrder(swordAId, swordBId),
                ids -> assertThat(ids).containsExactly(doorId)
        );
    }

    @Test
    @DisplayName("Returns successful zero-count result when no chapter objects exist for the book")
    void returnsSuccessfulNoOpWhenNoChapterObjectsExist() {
        UUID bookId = UUID.randomUUID();
        when(chapterObjectRepository.findByBookId(bookId)).thenReturn(List.of());

        BookObjectConsolidationResult response = service.consolidateBook(CTX, bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.chapterObjectsProcessed()).isZero();
        assertThat(response.bookObjectsCreated()).isZero();

        verify(bookObjectPersistenceService).replaceBookObjects(eq(bookId), eq(List.of()), eq(List.of()));
    }

    @Test
    @DisplayName("Retries transient Neo4j lock conflicts at the consolidation boundary")
    void retriesTransientNeo4jLockConflictsAtConsolidationBoundary() throws NoSuchMethodException {
        Retryable retryable = MergedAnnotations.from(BookObjectConsolidationService.class.getMethod("consolidateBook", StageExecutionContext.class, UUID.class))
                .get(Retryable.class)
                .synthesize();

        assertThat(retryable.retryFor()).contains(TransientDataAccessException.class, org.neo4j.driver.exceptions.TransientException.class);
        assertThat(retryable.maxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("Does not consolidate chapter objects through shared aliases when normalized names differ")
    void doesNotReduceObjectsThroughSharedAliases() {
        UUID bookId = UUID.randomUUID();
        UUID chapterAId = UUID.randomUUID();
        UUID chapterBId = UUID.randomUUID();
        UUID swordId = UUID.randomUUID();
        UUID daggerId = UUID.randomUUID();

        ChapterObject sword = chapterObject(swordId, chapterAId, "Silver Sword", "silver sword", List.of("Moonblade"), "weapon", "silver", "duel", "A named blade", 1);
        ChapterObject dagger = chapterObject(daggerId, chapterBId, "Ceremonial Dagger", "ceremonial dagger", List.of("Moonblade"), "weapon", "silver", "duel", "Shares an alias", 1);

        when(chapterObjectRepository.findByBookId(bookId)).thenReturn(List.of(dagger, sword));
        when(bookObjectPersistenceService.replaceBookObjects(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(bookObjectPersistenceService.countByBookId(bookId)).thenReturn(2L);

        BookObjectConsolidationResult response = service.consolidateBook(CTX, bookId);

        assertThat(response.success()).isTrue();
        assertThat(response.bookObjectsCreated()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BookObject>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(bookObjectPersistenceService).replaceBookObjects(eq(bookId), savedCaptor.capture(), any());

        assertThat(savedCaptor.getValue())
                .extracting(BookObject::normalizedName, BookObject::chapterObjectCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("silver sword", 1),
                        org.assertj.core.groups.Tuple.tuple("ceremonial dagger", 1)
                );
    }

    private ChapterObject chapterObject(
            UUID id,
            UUID chapterId,
            String displayName,
            String normalizedName,
            List<String> aliases,
            String type,
            String material,
            String purpose,
            String description,
            int mentionCount
    ) {
        return new ChapterObject(id, chapterId, null, displayName, normalizedName, aliases, type, material, purpose, description, mentionCount, null, null);
    }
}
