package com.lorevault.api.graph.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.lorevault.api.graph.event.persistence.BookEvent;
import com.lorevault.api.graph.collective.persistence.BookCollective;
import com.lorevault.api.graph.individual.persistence.BookIndividual;
import com.lorevault.api.graph.location.persistence.BookLocation;
import com.lorevault.api.graph.object.persistence.BookObject;
import com.lorevault.api.graph.event.persistence.ChapterEvent;
import com.lorevault.api.graph.collective.persistence.ChapterCollective;
import com.lorevault.api.graph.individual.persistence.ChapterIndividual;
import com.lorevault.api.graph.location.persistence.ChapterLocation;
import com.lorevault.api.graph.object.persistence.ChapterObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.schema.Node;

@Tag("unit")
@DisplayName("Association entity labels")
class AssociationEntityLabelTest {

    @Test
    @DisplayName("should map chapter aggregate entities with specific primary labels and shared ChapterEntity label")
    void shouldMapChapterAggregateEntitiesWithSpecificPrimaryLabelsAndSharedChapterEntityLabel() {
        assertNodeLabels(ChapterIndividual.class, "ChapterIndividual", "ChapterEntity");
        assertNodeLabels(ChapterCollective.class, "ChapterCollective", "ChapterEntity");
        assertNodeLabels(ChapterLocation.class, "ChapterLocation", "ChapterEntity");
        assertNodeLabels(ChapterObject.class, "ChapterObject", "ChapterEntity");
        assertNodeLabels(ChapterEvent.class, "ChapterEvent", "ChapterEntity");
    }

    @Test
    @DisplayName("should map book aggregate entities with specific primary labels and shared BookEntity label")
    void shouldMapBookAggregateEntitiesWithSpecificPrimaryLabelsAndSharedBookEntityLabel() {
        assertNodeLabels(BookIndividual.class, "BookIndividual", "BookEntity");
        assertNodeLabels(BookCollective.class, "BookCollective", "BookEntity");
        assertNodeLabels(BookLocation.class, "BookLocation", "BookEntity");
        assertNodeLabels(BookObject.class, "BookObject", "BookEntity");
        assertNodeLabels(BookEvent.class, "BookEvent", "BookEntity");
    }

    private static void assertNodeLabels(Class<?> entityType, String primaryLabel, String additionalLabel) {
        Node node = entityType.getAnnotation(Node.class);

        assertThat(node).isNotNull();
        assertThat(node.primaryLabel()).isEqualTo(primaryLabel);
        assertThat(node.labels()).containsExactly(additionalLabel);
    }
}
