package com.lorevault.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Neo4j schema initializer aggregate label backfills")
class Neo4jSchemaInitializerAggregateLabelBackfillTest {

    @Mock
    private Neo4jClient neo4jClient;

    @Mock
    private LoreVaultEmbeddingProperties embeddingProperties;

    @BeforeEach
    void setUp() {
        Neo4jClient.UnboundRunnableSpec runnableSpec = mock(Neo4jClient.UnboundRunnableSpec.class);
        when(neo4jClient.query(anyString())).thenReturn(runnableSpec);
    }

    @Test
    @DisplayName("should backfill aggregate labels for existing mention and aggregate entity nodes")
    void shouldBackfillAggregateLabelsForExistingMentionAndAggregateEntityNodes() {
        Neo4jSchemaInitializer initializer = new Neo4jSchemaInitializer(neo4jClient, embeddingProperties);

        initializer.ensureMinimalSchema();

        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        verify(neo4jClient, org.mockito.Mockito.atLeastOnce()).query(cypherCaptor.capture());

        assertThat(cypherCaptor.getAllValues()).containsAll(List.of(
                "MATCH (m:IndividualMention) SET m:Mention",
                "MATCH (m:LocationMention) SET m:Mention",
                "MATCH (m:ObjectMention) SET m:Mention",
                "MATCH (m:CollectiveMention) SET m:Mention",
                "MATCH (m:EventMention) SET m:Mention",
                "MATCH (ce:ChapterEvent) SET ce:ChapterEntity",
                "MATCH (ci:ChapterIndividual) SET ci:ChapterEntity",
                "MATCH (cl:ChapterLocation) SET cl:ChapterEntity",
                "MATCH (co:ChapterObject) SET co:ChapterEntity",
                "MATCH (cc:ChapterCollective) SET cc:ChapterEntity",
                "MATCH (be:BookEvent) SET be:BookEntity",
                "MATCH (bi:BookIndividual) SET bi:BookEntity",
                "MATCH (bl:BookLocation) SET bl:BookEntity",
                "MATCH (bo:BookObject) SET bo:BookEntity",
                "MATCH (bc:BookCollective) SET bc:BookEntity"
        ));
    }
}
