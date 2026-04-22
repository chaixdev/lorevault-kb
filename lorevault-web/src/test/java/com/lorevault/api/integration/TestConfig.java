package com.lorevault.api.integration;
import com.lorevault.api.ingestion.application.IngestionJobService;
import com.lorevault.api.ingestion.application.IngestionService;
import com.lorevault.api.ingestion.application.pipeline.*;
import com.lorevault.api.ingestion.application.resolution.*;
import com.lorevault.api.ingestion.application.result.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import com.lorevault.api.ai.SceneDetectionService;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Test configuration to provide mock beans for integration tests.
 */
@TestConfiguration
public class TestConfig {
    
    @Bean
    @Qualifier("nlpSmall")
    public ChatClient nlpSmallChatClient() {
        return mock(ChatClient.class);
    }

    @Bean
    @Qualifier("nlpBig")
    @Primary
    public ChatClient nlpBig() {
        return mock(ChatClient.class);
    }

    /**
     * Mock the SceneDetectionService for integration tests.
     */
    @Bean
    @Primary
    public SceneDetectionService sceneDetectionService() {
        return Mockito.mock(SceneDetectionService.class);
    }
}