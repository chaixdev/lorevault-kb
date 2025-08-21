package com.lorevault.api.domain.content;

import com.lorevault.api.domain.shared.PublicationCoordinates;
import com.lorevault.api.testutil.builders.ChapterBuilder;
import com.lorevault.api.testutil.builders.PublicationCoordinatesBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("Chapter")
class ChapterTest {

    @Test
    @DisplayName("should add and remove scenes while maintaining relationships")
    void shouldAddAndRemoveScenes() {
        Chapter chapter = ChapterBuilder.aChapter().build();

        Scene s1 = chapter.addScene(0, 0, 100, "Intro");
        Scene s2 = chapter.addScene(1, 101, 300, "Conflict");

        assertThat(chapter.getSceneCount()).isEqualTo(2);
        assertThat(s1.getChapter()).isEqualTo(chapter);
        assertThat(s2.getChapter()).isEqualTo(chapter);

        chapter.removeScene(s1);
        assertThat(chapter.getSceneCount()).isEqualTo(1);
        assertThat(s1.getChapter()).isNull();

        chapter.clearScenes();
        assertThat(chapter.getSceneCount()).isZero();
    }

    @Test
    @DisplayName("should add chunks and associate with scenes when requested")
    void shouldAddChunksAndAssociateWithScenes() {
        Chapter chapter = ChapterBuilder.aChapter().build();
        Scene scene = chapter.addScene(0, 0, 50, "Setup");

        Chunk c1 = chapter.addChunk(1, 0, 25, "hash1");
        Chunk c2 = chapter.addChunkToScene(scene, 2, 26, 50, "hash2");

        assertThat(chapter.getChunkCount()).isEqualTo(2);
        assertThat(c1.getChapter()).isEqualTo(chapter);
        assertThat(c2.getChapter()).isEqualTo(chapter);
        assertThat(c2.getScene()).isEqualTo(scene);

        List<Chunk> chunksForScene = chapter.getChunksForScene(scene);
        assertThat(chunksForScene).containsExactly(c2);

        chapter.removeChunk(c1);
        assertThat(chapter.getChunkCount()).isEqualTo(1);
        chapter.clearChunks();
        assertThat(chapter.getChunkCount()).isZero();
    }

    @Test
    @DisplayName("should reject adding chunk to a different chapter's scene")
    void shouldRejectAddingChunkToDifferentChapterScene() {
        Chapter chapter1 = ChapterBuilder.aChapter().build();
        Chapter chapter2 = ChapterBuilder.aChapter().build();
        Scene otherScene = chapter2.addScene(0, 0, 10, "Other");

        assertThatThrownBy(() -> chapter1.addChunkToScene(otherScene, 1, 0, 9, "hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Scene must belong to this chapter");
    }

    @Test
    @DisplayName("should create with references and standalone correctly")
    void shouldCreateWithReferencesAndStandalone() {
        PublicationCoordinates coords = PublicationCoordinatesBuilder.coordinates()
                .withUniverse("Cosmere")
                .withSeries("Stormlight Archive")
                .withBookTitle("The Way of Kings")
                .withChapterTitle("Kaladin")
                .withBookNumber(1)
                .withChapterNumber(1)
                .build();

        Chapter withRefs = Chapter.createWithReferences(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                coords, "Kaladin", "text", "hash");
        assertThat(withRefs.getCoordinates()).isEqualTo(coords);

        Chapter standalone = Chapter.createStandalone(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                coords, "Prologue", "text2", "hash2");
        assertThat(standalone.getSeriesId()).isNull();
    }
}
