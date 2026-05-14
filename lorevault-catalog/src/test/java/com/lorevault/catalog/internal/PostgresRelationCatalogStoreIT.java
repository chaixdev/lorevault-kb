package com.lorevault.catalog.internal;

import com.lorevault.catalog.RelationCatalogDefinition;
import com.lorevault.catalog.RelationCatalogId;
import com.lorevault.catalog.RelationKindSignature;
import com.lorevault.catalog.RelationQuery;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("integration")
class PostgresRelationCatalogStoreIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("lorevault_catalog")
            .withUsername("lorevault")
            .withPassword("lorevault_secret");

    private static DataSource dataSource;

    private PostgresRelationCatalogStore store;

    @BeforeAll
    static void migrate() {
        dataSource = createDataSource();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/catalog")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    @BeforeEach
    void setUp() {
        var jdbcClient = JdbcClient.create(new JdbcTemplate(dataSource));
        store = new PostgresRelationCatalogStore(jdbcClient);
    }

    private static DataSource createDataSource() {
        var ds = new DriverManagerDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    @Test
    void create_insertsNewDefinitionAndReturnsIt() {
        var query = new RelationQuery(
                "R:allies_with", "allies with", "Person", "Person",
                "Two characters who are allies", "certain", "ref1",
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), Optional.empty());

        var def = store.create(query);

        assertThat(def.id()).isNotNull();
        assertThat(def.definitionKey()).isEqualTo("R:allies_with");
        assertThat(def.displayName()).isEqualTo("allies with");
        assertThat(def.description()).isEqualTo("Two characters who are allies");
        assertThat(def.signatures()).isNotEmpty();
        assertThat(def.signatures().getFirst()).isEqualTo(new RelationKindSignature("Person", "Person"));
        assertThat(def.rawNameVariants()).containsExactly("allies with");
        assertThat(def.created()).isNotNull();
        assertThat(def.updated()).isNotNull();
        assertThat(def.lastSeen()).isNotNull();
    }

    @Test
    void findByDefinitionKey_returnsCreatedDefinition() {
        var query = new RelationQuery(
                "R:allies_with", "allies with", "Person", "Person",
                "Two characters who are allies", "certain", "ref1",
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), Optional.empty());

        var created = store.create(query);

        var found = store.findByDefinitionKey("R:allies_with");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(created.id());
        assertThat(found.get().definitionKey()).isEqualTo("R:allies_with");
        assertThat(found.get().displayName()).isEqualTo("allies with");
        assertThat(found.get().description()).isEqualTo("Two characters who are allies");
        assertThat(found.get().signatures()).containsExactly(new RelationKindSignature("Person", "Person"));
        assertThat(found.get().rawNameVariants()).containsExactly("allies with");
    }

    @Test
    void findById_returnsCreatedDefinition() {
        var query = new RelationQuery(
                "R:enemies_with", "enemies with", "Person", "Person",
                "Two characters who are enemies", "certain", "ref2",
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), Optional.empty());

        var created = store.create(query);

        var found = store.findById(created.id());

        assertThat(found).isPresent();
        assertThat(found.get().definitionKey()).isEqualTo("R:enemies_with");
        assertThat(found.get().displayName()).isEqualTo("enemies with");
        assertThat(found.get().description()).isEqualTo("Two characters who are enemies");
        assertThat(found.get().signatures()).containsExactly(new RelationKindSignature("Person", "Person"));
        assertThat(found.get().rawNameVariants()).containsExactly("enemies with");
    }

    @Test
    void create_isIdempotentForSameDefinitionKey() {
        var query1 = new RelationQuery(
                "R:married_to", "married to", "Person", "Person",
                "Spouses", "certain", "ref3",
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), Optional.empty());

        var first = store.create(query1);

        var query2 = new RelationQuery(
                "R:married_to", "married to", "Person", "Person",
                "Different description that should be ignored", "certain", "ref4",
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), Optional.empty());

        var second = store.create(query2);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.definitionKey()).isEqualTo("R:married_to");
        assertThat(second.displayName()).isEqualTo("married to");
        assertThat(second.description()).isEqualTo("Spouses");
    }

    @Test
    void create_enrichesWithSignatureAndVariant() {
        var query = new RelationQuery(
                "R:mentors", "mentored by", "Mentor", "Student",
                "A mentoring relationship", "likely", "ref5",
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), Optional.empty());

        var def = store.create(query);

        assertThat(def.signatures()).containsExactly(new RelationKindSignature("Mentor", "Student"));
        assertThat(def.rawNameVariants()).containsExactly("mentored by");
    }

    @Test
    void create_withoutKindOrRawName_doesNotEnrich() {
        var query = new RelationQuery(
                "R:generic", null, null, null,
                "A generic relation with no kinds", null, null,
                null, null, Optional.empty());

        var def = store.create(query);

        assertThat(def.signatures()).isEmpty();
        assertThat(def.rawNameVariants()).isEmpty();
        assertThat(def.displayName()).isEqualTo("R:generic");
    }

    @Test
    void findByDefinitionKey_returnsEmptyForUnknownKey() {
        var result = store.findByDefinitionKey("R:nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void findById_returnsEmptyForUnknownId() {
        var result = store.findById(RelationCatalogId.random());

        assertThat(result).isEmpty();
    }
}
