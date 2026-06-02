package com.lorevault.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lorevault.ai.models")
public record LlmClientProperties(
    String nlpSmallModelId,
    String nlpBigModelId
) {
    public LlmClientProperties {
        if (nlpSmallModelId == null) nlpSmallModelId = "openai/gpt-oss-120b";
        if (nlpBigModelId == null) nlpBigModelId = "openai/gpt-oss-120b";
    }
}
