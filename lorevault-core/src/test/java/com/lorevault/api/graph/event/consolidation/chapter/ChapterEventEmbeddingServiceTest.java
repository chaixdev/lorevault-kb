package com.lorevault.api.graph.event.consolidation.chapter;
import com.lorevault.api.ai.embedding.EmbeddingGenerationException;
import com.lorevault.api.graph.event.persistence.ChapterEvent;
import com.lorevault.api.graph.event.consolidation.chapter.ChapterEventEmbeddingService;
import com.lorevault.api.graph.event.consolidation.chapter.ChapterEventEmbeddingTransactionSupport;
import com.lorevault.api.testutil.fakes.FakeEmbeddingModel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChapterEventEmbeddingService")
class ChapterEventEmbeddingServiceTest {

    @Mock private ChapterEventEmbeddingTransactionSupport txSupport;

    @Test
    @DisplayName("Embedding hash is deterministic and includes model and aggregate card")
    void embeddingHashIncludesModelAndAggregateCard() {
        ChapterEventEmbeddingService service = new ChapterEventEmbeddingService(
                null,
                new FakeEmbeddingModel("fake-model", 8)
        );

        String first = service.computeEmbeddingHash("model-a", "The duel begins.");
        String second = service.computeEmbeddingHash("model-a", "The duel begins.");
        String modelChanged = service.computeEmbeddingHash("model-b", "The duel begins.");
        String cardChanged = service.computeEmbeddingHash("model-a", "The duel ends.");

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(modelChanged);
        assertThat(first).isNotEqualTo(cardChanged);
    }

    @Test
    @DisplayName("Rejects backend vectors with unexpected dimensions before persisting freshness")
    void rejectsWrongDimensionVectorsBeforePersisting() {
        UUID chapterId = UUID.randomUUID();
        ChapterEvent event = chapterEvent(chapterId, null, null, null);
        ChapterEventEmbeddingService service = serviceWithDimension(8, new FakeEmbeddingModel("fake-model", 4));

        when(txSupport.loadChapterEvents(chapterId)).thenReturn(List.of(event));

        assertThatThrownBy(() -> service.embedChapterEvents(chapterId))
                .isInstanceOf(EmbeddingGenerationException.class)
                .hasMessageContaining("unexpected dimensions");

        verify(txSupport, never()).saveChapterEvents(anyList());
    }

    @Test
    @DisplayName("Treats stored wrong-dimension embeddings as stale even when hash matches")
    void reembedsStoredWrongDimensionVectorWithMatchingHash() {
        UUID chapterId = UUID.randomUUID();
        ChapterEventEmbeddingService service = serviceWithDimension(8, new FakeEmbeddingModel("fake-model", 8));
        String aggregateCard = "A duel begins in the throne room.";
        String currentHash = service.computeEmbeddingHash("fake-model", aggregateCard);
        ChapterEvent stale = chapterEvent(chapterId, aggregateCard, new double[] {0.1, 0.2, 0.3, 0.4}, currentHash);

        when(txSupport.loadChapterEvents(chapterId)).thenReturn(List.of(stale));

        int embeddedCount = service.embedChapterEvents(chapterId);

        assertThat(embeddedCount).isEqualTo(1);
        ArgumentCaptor<List<ChapterEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(txSupport).saveChapterEvents(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        ChapterEvent updated = captor.getValue().getFirst();
        assertThat(updated.embedding()).hasSize(8);
        assertThat(updated.embeddingHash()).isEqualTo(currentHash);
        assertThat(updated.embeddedAt()).isNotNull();
    }

    private ChapterEventEmbeddingService serviceWithDimension(int dimension, FakeEmbeddingModel embeddingModel) {
        ChapterEventEmbeddingService service = new ChapterEventEmbeddingService(txSupport, embeddingModel);
        service.setEmbeddingDim(dimension);
        service.setConfiguredEmbeddingModelId("fake-model");
        return service;
    }

    private static ChapterEvent chapterEvent(UUID chapterId, String aggregateCard, double[] embedding, String embeddingHash) {
        return new ChapterEvent(
                UUID.randomUUID(),
                chapterId,
                UUID.randomUUID(),
                "component",
                "Duel begins",
                "duel begins",
                "DUEL",
                1,
                aggregateCard != null ? aggregateCard : "A duel begins in the throne room.",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                embedding,
                embeddingHash,
                null
        );
    }
}
