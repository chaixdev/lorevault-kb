package com.lorevault.catalog.internal;

import com.lorevault.catalog.RelationCatalogId;
import com.lorevault.catalog.RelationQuery;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpRelationCatalogServiceTest {

    private final NoOpRelationCatalogService disabled = new NoOpRelationCatalogService();

    @Test
    void resolve_throwsUnsupportedOperationException() {
        var query = new RelationQuery(
                "R:allies_with", "allies with", "Person", "Person",
                "Two characters who are allies", "certain", "ref1",
                UUID.randomUUID(), UUID.randomUUID(), Optional.empty());

        assertThatThrownBy(() -> disabled.resolve(query))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Catalog module is disabled");
    }

    @Test
    void findByKey_throwsUnsupportedOperationException() {
        var id = RelationCatalogId.random();

        assertThatThrownBy(() -> disabled.findByKey(id))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Catalog module is disabled");
    }

    @Test
    void findByDefinitionKey_throwsUnsupportedOperationException() {
        assertThatThrownBy(() -> disabled.findByDefinitionKey("R:allies_with"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Catalog module is disabled");
    }
}
