package com.lorevault.api.ai.infrastructure;

import com.lorevault.api.ai.llm.LlmCallLogger;
import com.lorevault.api.ai.llm.EventCorefModels;
import com.lorevault.api.ai.llm.EventMergeModels;
import com.lorevault.api.ai.llm.LlmClient;
import com.lorevault.api.ingestion.triad.SceneRelationshipAnalysisService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.config.LoreVaultModelsProperties;
import com.lorevault.api.config.LoreVaultPromptProperties;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmClientTest {

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
    private LlmCallLogger llmLog;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    private LlmClient client;

    @BeforeEach
    void setUp() {
        client = new LlmClient(
                nlpSmallChatClient,
                nlpBigChatClient,
                promptRepository,
                promptProperties,
                modelProperties,
                llmLog,
                new ObjectMapper(),
                RetryTemplate.builder().maxAttempts(1).build()
        );
    }

    @Test
    void detectSceneAnalysisTriad_shouldPersistSerializedStructuredResponseBody() {
        UUID jobId = UUID.randomUUID();
        var response = new SceneRelationshipAnalysisService.TriadStructuredResult(
                "timeline-marker",
                new SceneRelationshipAnalysisService.TriadRelation("before", "certain", "evidence-1"),
                new SceneRelationshipAnalysisService.TriadRelation("after", "likely", "evidence-2"),
                new SceneRelationshipAnalysisService.TriadCurrentSceneEntities(
                        List.of(new SceneRelationshipAnalysisService.TriadIndividualExtraction(
                                List.of("Kaladin", "Stormblessed"),
                                "tall, scarred",
                                "20s",
                                "A soldier with a spear"
                        )),
                        List.of(new SceneRelationshipAnalysisService.TriadObjectExtraction(
                                List.of("Sylspear"),
                                "spear",
                                "invested metal",
                                "combat",
                                "A spear formed from living spren"
                        )),
                        List.of(new SceneRelationshipAnalysisService.TriadLocationExtraction(
                                "Bridge Four Barracks",
                                List.of("the barracks"),
                                "building",
                                "Shattered Plains",
                                "military housing"
                        ))
                )
        );

        when(promptRepository.get("scene-analysis-user"))
                .thenReturn(new org.springframework.ai.chat.prompt.PromptTemplate("{curr_text}"));
        when(promptProperties.getSceneAnalysisModel()).thenReturn("nlp-small");
        when(promptProperties.getSceneAnalysisPath()).thenReturn("prompts/scene-analysis.txt");
        when(nlpSmallChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(eq(SceneRelationshipAnalysisService.TriadStructuredResult.class))).thenReturn(response);

        client.detectSceneAnalysisTriad(jobId, "system prompt", Map.of("curr_text", "chapter text"), SceneRelationshipAnalysisService.TriadStructuredResult.class);

        ArgumentCaptor<String> responseBodyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> outputTokensCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(llmLog).logCall(
                eq(jobId),
                eq("scene-analysis"),
                eq("openai-compatible"),
                eq(null),
                eq(0.1),
                eq(0.9),
                eq(6000),
                eq("prompts/scene-analysis.txt"),
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
        assertThat(responseBodyCaptor.getValue()).contains("\"type\":\"spear\"");
        assertThat(responseBodyCaptor.getValue()).contains("\"primaryName\":\"Bridge Four Barracks\"");
        assertThat(responseBodyCaptor.getValue()).doesNotContain("[structured-response:");
        assertThat(outputTokensCaptor.getValue()).isGreaterThan(0);
    }

    @Test
    void runEventCoref_shouldPersistStructuredResponseBodyWithEventCorefPromptPath() {
        UUID jobId = UUID.randomUUID();
        var response = new EventCorefModels.CorefWindowResponse(List.of(
                new EventCorefModels.CorefSameEventGroup(
                        List.of("mention-a", "mention-b"),
                        0.91,
                        "same battle"
                )
        ));

        when(promptRepository.get("event-coref-system"))
                .thenReturn(new org.springframework.ai.chat.prompt.PromptTemplate("system coref prompt"));
        when(promptProperties.getSceneAnalysisModel()).thenReturn("nlp-small");
        when(promptProperties.getEventCorefSystemPath()).thenReturn("classpath:prompts/event-coref-system.st");
        when(nlpSmallChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(eq(EventCorefModels.CorefWindowResponse.class))).thenReturn(response);

        client.runEventCoref(jobId, "<mentions><scene id=\"s1\"/></mentions>");

        ArgumentCaptor<String> responseBodyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> outputTokensCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(llmLog).logCall(
                eq(jobId),
                eq("event-coref"),
                eq("openai-compatible"),
                eq(null),
                eq(0.1),
                eq(0.9),
                eq(6000),
                eq("classpath:prompts/event-coref-system.st"),
                eq("system coref prompt"),
                eq("<mentions><scene id=\"s1\"/></mentions>"),
                responseBodyCaptor.capture(),
                anyLong(),
                eq(13),
                outputTokensCaptor.capture()
        );

        assertThat(responseBodyCaptor.getValue()).contains("\"sameEventGroups\"");
        assertThat(responseBodyCaptor.getValue()).contains("\"mentionIds\":[\"mention-a\",\"mention-b\"]");
        assertThat(outputTokensCaptor.getValue()).isGreaterThan(0);
    }

    @Test
    void runEventMergeVerification_shouldPersistStructuredResponseBodyWithEventMergePromptPath() {
        UUID jobId = UUID.randomUUID();
        var response = new EventMergeModels.EventMergePairResponse(
                "MERGE",
                0.86,
                "shared anchors align"
        );

        when(promptRepository.get("event-merge-system"))
                .thenReturn(new org.springframework.ai.chat.prompt.PromptTemplate("system merge prompt"));
        when(promptProperties.getEventMergeSystemPath()).thenReturn("classpath:prompts/event-merge-system.st");
        when(nlpSmallChatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(eq(EventMergeModels.EventMergePairResponse.class))).thenReturn(response);

        client.runEventMergeVerification(jobId, "<pair></pair>");

        ArgumentCaptor<String> responseBodyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> outputTokensCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(llmLog).logCall(
                eq(jobId),
                eq("event-merge"),
                eq("openai-compatible"),
                eq(null),
                eq(0.1),
                eq(0.9),
                eq(6000),
                eq("classpath:prompts/event-merge-system.st"),
                eq("system merge prompt"),
                eq("<pair></pair>"),
                responseBodyCaptor.capture(),
                anyLong(),
                eq(5),
                outputTokensCaptor.capture()
        );

        assertThat(responseBodyCaptor.getValue()).contains("\"decision\":\"MERGE\"");
        assertThat(responseBodyCaptor.getValue()).contains("\"confidence\":0.86");
    }
}
