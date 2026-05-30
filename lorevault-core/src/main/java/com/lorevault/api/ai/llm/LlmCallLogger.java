package com.lorevault.api.ai.llm;

import com.lorevault.api.orchestration.pipeline.StageKey;
import java.util.UUID;

public interface LlmCallLogger {

    void logCall(
            UUID jobId,
            StageKey stage,
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
