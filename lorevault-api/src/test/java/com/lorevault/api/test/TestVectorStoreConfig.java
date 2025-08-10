package com.lorevault.api.test;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("vector-int")
public class TestVectorStoreConfig {

    @Bean
    @Primary
    VectorStore vectorStore() {
        return new TestVectorStore();
    }
}
