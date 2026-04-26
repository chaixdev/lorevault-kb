package com.lorevault.api.ai.domain;

import java.util.UUID;

public interface LlmCallLogger {

    void logCall(
            UUID jobId,
            String step,
            String provider,
            String model,
            Double temperature,
            Double topP,
            Integer maxTokens,
            String promptTemplateId,
            String renderedPrompt,
            String inputBody,
            String responseBody,
            long latencyMs,
            Integer inputTokensEst,
            Integer outputTokensEst
    );
}
