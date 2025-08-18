package com.lorevault.api.schema;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * Unit test for SchemaBootstrapConfiguration using mocked components.
 * Tests the configuration logic without actual database operations.
 */
class SchemaBootstrapConfigurationTest {

    @Test
    void schemaBootstrapRunner_whenEnsureMode_shouldCallEnsureMinimalSchema() throws Exception {
        // Given
        GraphSchemaInitializer mockSchemaInitializer = mock(GraphSchemaInitializer.class);
        SchemaConfigurationProperties properties = new SchemaConfigurationProperties(
            SchemaConfigurationProperties.Mode.ENSURE,
            SchemaConfigurationProperties.Backend.NEO4J,
            true,
            false
        );
        
        SchemaBootstrapConfiguration config = new SchemaBootstrapConfiguration(
            mockSchemaInitializer, properties);
        
        // When
        ApplicationRunner runner = config.schemaBootstrapRunner();
        ApplicationArguments mockArgs = mock(ApplicationArguments.class);
        runner.run(mockArgs);
        
        // Then
        verify(mockSchemaInitializer).ensureMinimalSchema();
        verifyNoMoreInteractions(mockSchemaInitializer);
    }
    
    @Test
    void schemaBootstrapRunner_whenValidateMode_shouldCallValidateMinimalSchema() throws Exception {
        // Given
        GraphSchemaInitializer mockSchemaInitializer = mock(GraphSchemaInitializer.class);
        GraphSchemaInitializer.SchemaReport mockReport = 
            new GraphSchemaInitializer.SchemaReport(true, true, "All good");
        when(mockSchemaInitializer.validateMinimalSchema()).thenReturn(mockReport);
        
        SchemaConfigurationProperties properties = new SchemaConfigurationProperties(
            SchemaConfigurationProperties.Mode.VALIDATE,
            SchemaConfigurationProperties.Backend.NEO4J,
            true,
            false
        );
        
        SchemaBootstrapConfiguration config = new SchemaBootstrapConfiguration(
            mockSchemaInitializer, properties);
        
        // When
        ApplicationRunner runner = config.schemaBootstrapRunner();
        ApplicationArguments mockArgs = mock(ApplicationArguments.class);
        runner.run(mockArgs);
        
        // Then
        verify(mockSchemaInitializer).validateMinimalSchema();
        verifyNoMoreInteractions(mockSchemaInitializer);
    }
    
    @Test
    void schemaBootstrapRunner_whenNoneMode_shouldNotCallAnySchemaOperations() throws Exception {
        // Given
        GraphSchemaInitializer mockSchemaInitializer = mock(GraphSchemaInitializer.class);
        SchemaConfigurationProperties properties = new SchemaConfigurationProperties(
            SchemaConfigurationProperties.Mode.NONE,
            SchemaConfigurationProperties.Backend.NEO4J,
            true,
            false
        );
        
        SchemaBootstrapConfiguration config = new SchemaBootstrapConfiguration(
            mockSchemaInitializer, properties);
        
        // When
        ApplicationRunner runner = config.schemaBootstrapRunner();
        ApplicationArguments mockArgs = mock(ApplicationArguments.class);
        runner.run(mockArgs);
        
        // Then
        verifyNoInteractions(mockSchemaInitializer);
    }

    @Test
    void schemaBootstrapRunner_whenInitializerThrowsAndFailOnErrorFalse_shouldNotPropagate() {
        // Given
        GraphSchemaInitializer mockSchemaInitializer = mock(GraphSchemaInitializer.class);
        doThrow(new RuntimeException("Schema error")).when(mockSchemaInitializer).ensureMinimalSchema();
        
        SchemaConfigurationProperties properties = new SchemaConfigurationProperties(
            SchemaConfigurationProperties.Mode.ENSURE,
            SchemaConfigurationProperties.Backend.NEO4J,
            true,
            false // failOnError = false
        );
        
        SchemaBootstrapConfiguration config = new SchemaBootstrapConfiguration(
            mockSchemaInitializer, properties);
        
        // When/Then
        ApplicationRunner runner = config.schemaBootstrapRunner();
        ApplicationArguments mockArgs = mock(ApplicationArguments.class);
        assertDoesNotThrow(() -> runner.run(mockArgs));
    }
    
    @Test
    void schemaBootstrapRunner_whenInitializerThrowsAndFailOnErrorTrue_shouldPropagate() {
        // Given
        GraphSchemaInitializer mockSchemaInitializer = mock(GraphSchemaInitializer.class);
        doThrow(new RuntimeException("Schema error")).when(mockSchemaInitializer).ensureMinimalSchema();
        
        SchemaConfigurationProperties properties = new SchemaConfigurationProperties(
            SchemaConfigurationProperties.Mode.ENSURE,
            SchemaConfigurationProperties.Backend.NEO4J,
            true,
            true // failOnError = true
        );
        
        SchemaBootstrapConfiguration config = new SchemaBootstrapConfiguration(
            mockSchemaInitializer, properties);
        
        // When/Then
        ApplicationRunner runner = config.schemaBootstrapRunner();
        ApplicationArguments mockArgs = mock(ApplicationArguments.class);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> runner.run(mockArgs));
    }
}
