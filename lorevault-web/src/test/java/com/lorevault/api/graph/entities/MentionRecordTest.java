package com.lorevault.api.graph.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.lorevault.api.ai.infrastructure.PromptName;
import com.lorevault.api.graph.event.persistence.EventMention;
import com.lorevault.api.graph.collective.persistence.CollectiveMention;
import com.lorevault.api.graph.concept.persistence.ConceptMention;
import com.lorevault.api.graph.individual.persistence.IndividualMention;
import com.lorevault.api.graph.location.persistence.LocationMention;
import com.lorevault.api.graph.object.persistence.ObjectMention;
import com.lorevault.api.graph.mention.Mention;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.schema.Node;

@Tag("unit")
@DisplayName("Mention records")
class MentionRecordTest {

    private static final UUID SCENE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CHAPTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID BOOK_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 4, 23, 10, 15);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 4, 23, 11, 45);

    @Test
    @DisplayName("should expose individual mention fields through record accessors")
    void shouldExposeIndividualMentionFieldsThroughRecordAccessors() {
        IndividualMention mention = individualMention(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                List.of("Kal"), "UNRESOLVED");

        assertThat(mention.id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(mention.source()).isEqualTo(PromptName.SCENE_ANALYSIS.promptKey());
        assertThat(mention.displayName()).isEqualTo("Kaladin");
        assertThat(mention.normalizedName()).isEqualTo("kaladin");
        assertThat(mention.aliases()).containsExactly("Kal");
        assertThat(mention.activity()).isEqualTo("protecting");
        assertThat(mention.age()).isEqualTo("young adult");
        assertThat(mention.physicalProperties()).isEqualTo("scarred");
        assertThat(mention.stageId()).isNull();
        assertThat(mention.sceneId()).isEqualTo(SCENE_ID);
        assertThat(mention.chapterId()).isEqualTo(CHAPTER_ID);
        assertThat(mention.bookId()).isEqualTo(BOOK_ID);
        assertThat(mention.resolutionStatus()).isEqualTo("UNRESOLVED");
        assertThat(mention.extractionIndex()).isEqualTo(3);
        assertThat(mention.createdAt()).isEqualTo(CREATED_AT);
        assertThat(mention.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    @DisplayName("should expose object mention fields through record accessors")
    void shouldExposeObjectMentionFieldsThroughRecordAccessors() {
        ObjectMention mention = objectMention(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                List.of("Nightblood"), "UNRESOLVED");

        assertThat(mention.id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(mention.source()).isEqualTo(PromptName.SCENE_ANALYSIS.promptKey());
        assertThat(mention.displayName()).isEqualTo("Nightblood");
        assertThat(mention.normalizedName()).isEqualTo("nightblood");
        assertThat(mention.aliases()).containsExactly("Nightblood");
        assertThat(mention.type()).isEqualTo("sentient sword");
        assertThat(mention.material()).isEqualTo("black metal");
        assertThat(mention.purpose()).isEqualTo("destroy evil");
        assertThat(mention.description()).isEqualTo("A dangerous awakened blade");
        assertThat(mention.stageId()).isNull();
        assertThat(mention.sceneId()).isEqualTo(SCENE_ID);
        assertThat(mention.chapterId()).isEqualTo(CHAPTER_ID);
        assertThat(mention.bookId()).isEqualTo(BOOK_ID);
        assertThat(mention.resolutionStatus()).isEqualTo("UNRESOLVED");
        assertThat(mention.extractionIndex()).isEqualTo(4);
        assertThat(mention.createdAt()).isEqualTo(CREATED_AT);
        assertThat(mention.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    @DisplayName("should preserve record equality semantics for mentions")
    void shouldPreserveRecordEqualitySemanticsForMentions() {
        LocationMention first = locationMention(UUID.fromString("00000000-0000-0000-0000-000000000010"),
                List.of("The Tower"), "RESOLVED");
        LocationMention same = locationMention(UUID.fromString("00000000-0000-0000-0000-000000000010"),
                List.of("The Tower"), "RESOLVED");
        LocationMention different = locationMention(UUID.fromString("00000000-0000-0000-0000-000000000011"),
                List.of("The Tower"), "RESOLVED");

        assertThat(first).isEqualTo(same);
        assertThat(first.hashCode()).isEqualTo(same.hashCode());
        assertThat(first).isNotEqualTo(different);
    }

    @Test
    @DisplayName("should allow null aliases without affecting other shared fields")
    void shouldAllowNullAliasesWithoutAffectingOtherSharedFields() {
        EventMention mention = eventMention(UUID.fromString("00000000-0000-0000-0000-000000000020"), null, "PENDING");

        assertThat(mention.aliases()).isNull();
        assertThat(mention.displayName()).isEqualTo("The Duel");
        assertThat(mention.normalizedName()).isEqualTo("the_duel");
        assertThat(mention.stageId()).isNull();
        assertThat(mention.sceneId()).isEqualTo(SCENE_ID);
        assertThat(mention.chapterId()).isEqualTo(CHAPTER_ID);
        assertThat(mention.bookId()).isEqualTo(BOOK_ID);
        assertThat(mention.resolutionStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("should support shared mention contract across mention record types")
    void shouldSupportSharedMentionContractAcrossMentionRecordTypes() {
        List<Mention> mentions = List.of(
                individualMention(UUID.fromString("00000000-0000-0000-0000-000000000031"), List.of("Kal"), "UNRESOLVED"),
                collectiveMention(UUID.fromString("00000000-0000-0000-0000-000000000035"), List.of("Bridge Four"), "UNRESOLVED"),
                objectMention(UUID.fromString("00000000-0000-0000-0000-000000000034"), List.of("Nightblood"), "UNRESOLVED"),
                locationMention(UUID.fromString("00000000-0000-0000-0000-000000000032"), List.of("The Tower"), "RESOLVED"),
                eventMention(UUID.fromString("00000000-0000-0000-0000-000000000033"), List.of("Contest"), "PENDING")
        );

        assertThat(mentions)
                .extracting(Mention::sceneId)
                .containsOnly(SCENE_ID);
        assertThat(mentions)
                .extracting(Mention::chapterId)
                .containsOnly(CHAPTER_ID);
        assertThat(mentions)
                .extracting(Mention::bookId)
                .containsOnly(BOOK_ID);
        assertThat(mentions)
                .extracting(Mention::displayName)
                .containsExactly("Kaladin", "Bridge Four", "Nightblood", "Urithiru", "The Duel");
        assertThat(mentions)
                .extracting(Mention::normalizedName)
                .containsExactly("kaladin", "bridge four", "nightblood", "urithiru", "the_duel");
        assertThat(mentions)
                .extracting(Mention::resolutionStatus)
                .containsExactly("UNRESOLVED", "UNRESOLVED", "UNRESOLVED", "RESOLVED", "PENDING");
    }

    @Test
    @DisplayName("should map all mention records with EntityMention + entity-type labels")
    void shouldMapAllMentionRecordsWithSpecificPrimaryLabelsAndSharedMentionLabel() {
        assertNodeLabels(IndividualMention.class, "IndividualMention", "EntityMention", "IndividualNode", "EntityNode");
        assertNodeLabels(CollectiveMention.class, "CollectiveMention", "EntityMention", "CollectiveNode", "EntityNode");
        assertNodeLabels(ConceptMention.class, "ConceptMention", "EntityMention", "ConceptNode", "EntityNode");
        assertNodeLabels(ObjectMention.class, "ObjectMention", "EntityMention", "ObjectNode", "EntityNode");
        assertNodeLabels(LocationMention.class, "LocationMention", "EntityMention", "LocationNode", "EntityNode");
        assertNodeLabels(EventMention.class, "EventMention", "EntityMention", "EventNode", "EntityNode");
    }

    private static CollectiveMention collectiveMention(UUID id, List<String> aliases, String resolutionStatus) {
        return new CollectiveMention(
                id,
                PromptName.SCENE_ANALYSIS.promptKey(),
                "Bridge Four",
                "bridge four",
                aliases,
                "military",
                "Explicit",
                "Bridge Four forms up around Kaladin",
                null,
                SCENE_ID,
                CHAPTER_ID,
                BOOK_ID,
                resolutionStatus,
                6,
                CREATED_AT,
                UPDATED_AT
        );
    }

    private static void assertNodeLabels(Class<?> entityType, String primaryLabel, String... additionalLabels) {
        Node node = entityType.getAnnotation(Node.class);

        assertThat(node).isNotNull();
        assertThat(node.primaryLabel()).isEqualTo(primaryLabel);
        assertThat(node.labels()).containsExactlyInAnyOrder(additionalLabels);
    }

    private static IndividualMention individualMention(UUID id, List<String> aliases, String resolutionStatus) {
        return new IndividualMention(
                id,
                PromptName.SCENE_ANALYSIS.promptKey(),
                "Kaladin",
                "kaladin",
                aliases,
                "protecting",
                "young adult",
                "scarred",
                null,
                SCENE_ID,
                CHAPTER_ID,
                BOOK_ID,
                resolutionStatus,
                3,
                CREATED_AT,
                UPDATED_AT
        );
    }

    private static LocationMention locationMention(UUID id, List<String> aliases, String resolutionStatus) {
        return new LocationMention(
                id,
                PromptName.SCENE_ANALYSIS.promptKey(),
                "Urithiru",
                "urithiru",
                aliases,
                "city",
                "Roshar",
                "Ancient tower city",
                null,
                SCENE_ID,
                CHAPTER_ID,
                BOOK_ID,
                resolutionStatus,
                5,
                CREATED_AT,
                UPDATED_AT
        );
    }

    private static ObjectMention objectMention(UUID id, List<String> aliases, String resolutionStatus) {
        return new ObjectMention(
                id,
                PromptName.SCENE_ANALYSIS.promptKey(),
                "Nightblood",
                "nightblood",
                aliases,
                "sentient sword",
                "black metal",
                "destroy evil",
                "A dangerous awakened blade",
                null,
                SCENE_ID,
                CHAPTER_ID,
                BOOK_ID,
                resolutionStatus,
                4,
                CREATED_AT,
                UPDATED_AT
        );
    }

    private static EventMention eventMention(UUID id, List<String> aliases, String resolutionStatus) {
        return new EventMention(
                id,
                PromptName.SCENE_ANALYSIS.promptKey(),
                "The Duel",
                "the_duel",
                aliases,
                "duel",
                "A formal duel between two champions",
                "during",
                "high",
                "Two champions face off",
                null,
                SCENE_ID,
                CHAPTER_ID,
                BOOK_ID,
                resolutionStatus,
                7,
                CREATED_AT,
                UPDATED_AT
        );
    }
}
