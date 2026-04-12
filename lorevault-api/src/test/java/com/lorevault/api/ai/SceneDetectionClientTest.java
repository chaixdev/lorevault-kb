package com.lorevault.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.config.LoreVaultModelsProperties;
import com.lorevault.api.config.LoreVaultPromptProperties;
import com.lorevault.api.ingestion.LlmCallLoggingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.retry.support.RetryTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SceneDetectionClientTest {

    @Mock
    private ChatClient nlpSmallChatClient;

    @Mock
    private ChatClient nlpBigChatClient;

    @Mock
    private PromptRepository promptRepository;

    @Mock
    private LoreVaultPromptProperties promptProperties;

    @Mock
    private LoreVaultModelsProperties modelProperties;

    @Mock
    private LlmCallLoggingService llmLog;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    private SceneDetectionClient client;

    @BeforeEach
    void setUp() {
        client = new SceneDetectionClient(
                nlpSmallChatClient,
                nlpBigChatClient,
                promptRepository,
                promptProperties,
                modelProperties,
                RetryTemplate.builder().maxAttempts(1).build(),
                llmLog,
                new ObjectMapper()
        );
    }

    @Test
    void detectScenesPass2Triad_shouldPersistSerializedStructuredResponseBody() {
        UUID jobId = UUID.randomUUID();
        var response = new TriadOrchestrationService.TriadStructuredResult(
                "timeline-marker",
                new TriadOrchestrationService.TriadRelation("before", "certain", "evidence-1"),
                new TriadOrchestrationService.TriadRelation("after", "likely", "evidence-2"),
                new TriadOrchestrationService.TriadCurrentSceneEntities(
                        List.of(new TriadOrchestrationService.TriadIndividualExtraction(
                                List.of("Kaladin", "Stormblessed"),
                                "tall, scarred",
                                "20s",
                                "A soldier with a spear"
                        ))
                )
        );

        when(promptRepository.get("scene-detection-pass2-user"))
                .thenReturn(new org.springframework.ai.chat.prompt.PromptTemplate("{curr_text}"));
        when(promptProperties.getSceneDetectionPass2Model()).thenReturn("nlp-small");
        when(promptProperties.getSceneDetectionPass2Path()).thenReturn("prompts/scene-detection-pass2.txt");
        when(nlpSmallChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(eq(TriadOrchestrationService.TriadStructuredResult.class))).thenReturn(response);

        client.detectScenesPass2Triad(jobId, "system prompt", Map.of("curr_text", "chapter text"), TriadOrchestrationService.TriadStructuredResult.class);

        ArgumentCaptor<String> responseBodyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> outputTokensCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(llmLog).logCall(
                eq(jobId),
                eq("scene-detection-pass2"),
                eq("openai-compatible"),
                eq(null),
                eq(0.1),
                eq(0.9),
                eq(6000),
                eq("prompts/scene-detection-pass2.txt"),
                eq("system prompt"),
                eq("chapter text"),
                responseBodyCaptor.capture(),
                anyLong(),
                eq(4),
                outputTokensCaptor.capture()
        );

        assertThat(responseBodyCaptor.getValue()).contains("\"timelineMarker\":\"timeline-marker\"");
        assertThat(responseBodyCaptor.getValue()).contains("\"currentSceneEntities\"");
        assertThat(responseBodyCaptor.getValue()).contains("\"activity\":\"A soldier with a spear\"");
        assertThat(responseBodyCaptor.getValue()).doesNotContain("[structured-response:");
        assertThat(outputTokensCaptor.getValue()).isGreaterThan(0);
    }
}
