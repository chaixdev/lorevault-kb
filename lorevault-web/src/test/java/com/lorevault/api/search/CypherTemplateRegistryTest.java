package com.lorevault.api.search;

import com.lorevault.api.search.domain.EntityLookupException;
import com.lorevault.api.search.infrastructure.CypherTemplateRegistry;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.neo4j.core.Neo4jClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class CypherTemplateRegistryTest {

    @Mock
    private Neo4jClient neo4jClient;

    @Mock
    private Neo4jClient.UnboundRunnableSpec querySpec;

    @Test
    void shouldThrowIllegalStateForUnknownTemplateId() {
        CypherTemplateRegistry registry = new CypherTemplateRegistry(neo4jClient);

        assertThatThrownBy(() -> registry.execute("unknown-template", Map.of(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown entity lookup template");
    }

    @Test
    void shouldThrowEntityLookupExceptionWhenBackendQueryFails() {
        CypherTemplateRegistry registry = new CypherTemplateRegistry(neo4jClient);
        when(neo4jClient.query(anyString())).thenThrow(new DataAccessResourceFailureException("neo4j down"));

        assertThatThrownBy(() -> registry.execute("individual-lookup", Map.of("normalizedName", "vin"), null))
                .isInstanceOf(EntityLookupException.class)
                .hasMessageContaining("Entity lookup query failed")
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }
}
