package com.lorevault.api.graph.event.consolidation.book;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lorevault.api.config.LoreVaultEmbeddingProperties;
import com.lorevault.api.graph.event.persistence.ChapterEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.neo4j.driver.Record;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.types.TypeSystem;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.DatabaseSelection;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.UserSelection;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.neo4j.driver.QueryRunner;
import org.springframework.lang.NonNull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookEventAnnCandidateService")
class BookEventAnnCandidateServiceTest {

    @Mock private Neo4jClient neo4jClient;

    private ListAppender<ILoggingEvent> logAppender;

    /** Dimension matches {@link LoreVaultEmbeddingProperties#DIMENSIONS}. */
    private static final BookEventAnnProperties TEST_PROPS =
            new BookEventAnnProperties(8, 3, 0.82, 3);

    private static double[] testEmbedding(double... values) {
        double[] vec = new double[LoreVaultEmbeddingProperties.DIMENSIONS];
        Arrays.fill(vec, 0.01);
        for (int i = 0; i < values.length && i < vec.length; i++) {
            vec[i] = values[i];
        }
        return vec;
    }

    @Test
    @DisplayName("Queries book-wide candidates, excludes same-chapter candidates, and propagates ANN failures")
    void queryFailurePropagatesAndQueryScopesToBookExcludingSameChapterCandidates() {
        BookEventAnnCandidateService service = new BookEventAnnCandidateService(
                neo4jClient, TEST_PROPS
        );
        UUID chapterId = UUID.randomUUID();
        ChapterEvent source = chapterEvent(chapterId, testEmbedding(0.1, 0.2, 0.3));

        when(neo4jClient.query(anyString())).thenThrow(new IllegalStateException("index missing"));

        assertThatThrownBy(() -> service.generateCandidates(List.of(source), chapterId))
                .isInstanceOf(BookEventAnnCandidateException.class)
                .hasMessageContaining("ChapterEvent ANN query failed");

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(neo4jClient).query(queryCaptor.capture());
        assertThat(queryCaptor.getValue())
                .contains("MATCH (:Chapter {id: $chapterId})-[:IN_BOOK]->(book:Book)")
                .contains("MATCH (:Chapter {id: candidate.chapterId})-[:IN_BOOK]->(book)")
                .contains("candidate.chapterId <> $chapterId");
    }

    @Test
    @DisplayName("Skips source event whose embedding dimension does not match configured dimension")
    void skipsSourceEventWithWrongEmbeddingDimension() {
        BookEventAnnCandidateService service = new BookEventAnnCandidateService(
                neo4jClient, TEST_PROPS
        );
        UUID chapterId = UUID.randomUUID();
        // 2-element embedding — wrong dimension
        ChapterEvent wrongDim = chapterEvent(chapterId, new double[] {0.1, 0.2});

        List<BookEventCandidatePair> result = service.generateCandidates(List.of(wrongDim), chapterId);

        assertThat(result).isEmpty();
        verifyNoInteractions(neo4jClient);
    }

    @Test
    @DisplayName("Warns when fewer candidates survive book filtering than configured topK")
    void warnsWhenRecallMayBeInsufficientAfterBookFiltering() {
        logAppender = attachAppender();
        StubNeo4jClient stubClient = new StubNeo4jClient();
        BookEventAnnCandidateService service = new BookEventAnnCandidateService(stubClient, TEST_PROPS);
        UUID chapterId = UUID.randomUUID();
        ChapterEvent source = chapterEvent(chapterId, testEmbedding(0.1, 0.2, 0.3));

        List<BookEventCandidatePair> result = service.generateCandidates(List.of(source), chapterId);

        assertThat(result).isEmpty();
        assertThat(stubClient.lastQuery())
                .contains("CALL db.index.vector.queryNodes($indexName, $limit, $embedding)")
                .contains("LIMIT $topK");
        assertThat(logAppender.list)
                .anyMatch(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("Recall may be insufficient")
                        && event.getFormattedMessage().contains("only 0/8 candidates survived book-filter")
                        && event.getFormattedMessage().contains("oversampleFactor (current=3)"));
    }

    @AfterEach
    void detachAppender() {
        if (logAppender != null) {
            Logger logger = (Logger) LoggerFactory.getLogger(BookEventAnnCandidateService.class);
            logger.detachAppender(logAppender);
        }
    }

    private static ChapterEvent chapterEvent(UUID chapterId, double[] embedding) {
        return new ChapterEvent(
                UUID.randomUUID(),
                chapterId,
                UUID.randomUUID(),
                "component",
                "Duel begins",
                "duel begins",
                "DUEL",
                1,
                "A duel begins in the throne room.",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                embedding,
                "hash",
                null
        );
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(BookEventAnnCandidateService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static final class StubNeo4jClient implements Neo4jClient {

        private String lastQuery;

        @Override
        @NonNull
        public QueryRunner getQueryRunner(@NonNull DatabaseSelection databaseSelection, @NonNull UserSelection asUser) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        @NonNull
        public UnboundRunnableSpec query(@NonNull String cypher) {
            lastQuery = cypher;
            return new StubRunnableSpec();
        }

        @Override
        @NonNull
        public UnboundRunnableSpec query(@NonNull Supplier<String> cypherSupplier) {
            return query(cypherSupplier.get());
        }

        @Override
        @NonNull
        public <T> OngoingDelegation<T> delegateTo(
                @NonNull java.util.function.Function<QueryRunner, java.util.Optional<T>> callback
        ) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        @NonNull
        public DatabaseSelectionProvider getDatabaseSelectionProvider() {
            throw new UnsupportedOperationException("Not used by this test");
        }

        private String lastQuery() {
            return lastQuery;
        }
    }

    private static final class StubRunnableSpec implements Neo4jClient.UnboundRunnableSpec {

        @Override
        @NonNull
        public Neo4jClient.RunnableSpecBoundToDatabase in(String targetDatabase) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        @NonNull
        public Neo4jClient.RunnableSpecBoundToUser asUser(String asUser) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        @NonNull
        public <T> Neo4jClient.OngoingBindSpec<T, Neo4jClient.RunnableSpec> bind(T value) {
            return new StubBindSpec<>(this);
        }

        @Override
        @NonNull
        public Neo4jClient.RunnableSpec bindAll(@NonNull java.util.Map<String, Object> parameters) {
            return this;
        }

        @Override
        @NonNull
        public <T> Neo4jClient.MappingSpec<T> fetchAs(@NonNull Class<T> targetClass) {
            return new StubMappingSpec<>();
        }

        @Override
        @NonNull
        public Neo4jClient.RecordFetchSpec<java.util.Map<String, Object>> fetch() {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        @NonNull
        public ResultSummary run() {
            throw new UnsupportedOperationException("Not used by this test");
        }

    }

    private record StubBindSpec<T>(Neo4jClient.RunnableSpec spec)
            implements Neo4jClient.OngoingBindSpec<T, Neo4jClient.RunnableSpec> {

        @Override
        @NonNull
        public Neo4jClient.RunnableSpec to(@NonNull String name) {
            return spec;
        }

        @Override
        @NonNull
        public Neo4jClient.RunnableSpec with(@NonNull java.util.function.Function<T, java.util.Map<String, Object>> binder) {
            return spec;
        }
    }

    private record StubMappingSpec<T>() implements Neo4jClient.MappingSpec<T> {

        @Override
        @NonNull
        public Neo4jClient.RecordFetchSpec<T> mappedBy(@NonNull BiFunction<TypeSystem, Record, T> mappingFunction) {
            return new StubRecordFetchSpec<>();
        }

        @Override
        @NonNull
        public java.util.Optional<T> one() {
            return java.util.Optional.empty();
        }

        @Override
        @NonNull
        public java.util.Optional<T> first() {
            return java.util.Optional.empty();
        }

        @Override
        @NonNull
        public Collection<T> all() {
            return List.of();
        }
    }

    private record StubRecordFetchSpec<T>() implements Neo4jClient.RecordFetchSpec<T> {

        @Override
        @NonNull
        public java.util.Optional<T> one() {
            return java.util.Optional.empty();
        }

        @Override
        @NonNull
        public java.util.Optional<T> first() {
            return java.util.Optional.empty();
        }

        @Override
        @NonNull
        public Collection<T> all() {
            return List.of();
        }
    }
}
