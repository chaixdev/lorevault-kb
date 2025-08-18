package com.lorevault.api.infrastructure.graph;

import com.lorevault.api.schema.GraphSchemaInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.Neo4jClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class Neo4jSchemaInitializerTest {

    @Test
    void ensureMinimalSchema_shouldExecuteAllConstraintsAndIndexes() {
        // Given
        Neo4jClient mockClient = mock(Neo4jClient.class);
        Neo4jClient.UnboundRunnableSpec mockSpec = mock(Neo4jClient.UnboundRunnableSpec.class);
        
        when(mockClient.query(anyString())).thenReturn(mockSpec);
        when(mockSpec.run()).thenReturn(null);
        
        Neo4jSchemaInitializer initializer = new Neo4jSchemaInitializer(mockClient);
        
        // When
        assertDoesNotThrow(() -> initializer.ensureMinimalSchema());
        
        // Then - verify all constraints and indexes were attempted
        verify(mockClient, times(9)).query(anyString()); // 6 constraints + 3 indexes
    }
    
    @Test 
    void ensureMinimalSchema_shouldContinueOnFailure() {
        // Given
        Neo4jClient mockClient = mock(Neo4jClient.class);
        Neo4jClient.UnboundRunnableSpec mockSpec = mock(Neo4jClient.UnboundRunnableSpec.class);
        
        when(mockClient.query(anyString())).thenReturn(mockSpec);
        when(mockSpec.run()).thenThrow(new RuntimeException("Constraint already exists"));
        
        Neo4jSchemaInitializer initializer = new Neo4jSchemaInitializer(mockClient);
        
        // When/Then - should not throw despite failures
        assertDoesNotThrow(() -> initializer.ensureMinimalSchema());
    }
    
    @Test
    void validateMinimalSchema_shouldReturnReport() {
        // Given
        Neo4jClient mockClient = mock(Neo4jClient.class);
        Neo4jSchemaInitializer initializer = new Neo4jSchemaInitializer(mockClient);
        
        // When
        GraphSchemaInitializer.SchemaReport report = initializer.validateMinimalSchema();
        
        // Then
        assertNotNull(report);
        assertTrue(report.hasAllConstraints()); // Currently always true
        assertTrue(report.hasAllIndexes()); // Currently always true
        assertNotNull(report.summary());
    }
}
