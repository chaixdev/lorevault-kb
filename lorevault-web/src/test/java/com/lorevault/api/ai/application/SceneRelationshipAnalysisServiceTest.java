package com.lorevault.api.ai.application;
import com.lorevault.api.ai.llm.LlmClient;
import com.lorevault.api.ingestion.triad.TriadAnalysisException;
import com.lorevault.api.ai.infrastructure.PromptRepository;

import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.scene.Scene;
import com.lorevault.api.ingestion.triad.TriadAnalysisModels;
import com.lorevault.api.ingestion.triad.SceneRelationshipAnalysisService;
import com.lorevault.api.ingestion.triad.TriadBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TriadOrchestrationService")
class SceneRelationshipAnalysisServiceTest {

    @Mock
    private TriadBuilderService triadBuilderService;
    
    @Mock
    private LlmClient llmClient;

    @Mock
    private PromptRepository promptRepository;

    private SceneRelationshipAnalysisService sceneRelationshipAnalysisService;

    private final UUID testJobId = UUID.randomUUID();
    private final UUID testChapterId = UUID.randomUUID();
    private final UUID scene1Id = UUID.randomUUID();
    private final UUID scene2Id = UUID.randomUUID();
    private final UUID scene3Id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sceneRelationshipAnalysisService = new SceneRelationshipAnalysisService(
            triadBuilderService,
                llmClient,
            promptRepository
        );
    }

    @Test
    @DisplayName("Should analyze each triad and return relationship results")
    void shouldAnalyzeEachTriadAndReturnRelationshipResults() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = createTestTriads();
        
        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);
        when(llmClient.detectSceneAnalysisTriad(any(), any(), any(), eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)))
                .thenReturn(createMockTriadResult());

        List<TriadAnalysisModels.SceneRelationshipAnalysis> result =
            sceneRelationshipAnalysisService.analyzeChapterTriads(testJobId, testChapter);

        assertThat(result).hasSize(2);
        verify(llmClient, times(2)).detectSceneAnalysisTriad(
                eq(testJobId),
                eq("mock system prompt"),
                any(),
                eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)
        );
    }

    @Test
    @DisplayName("Should call triad start callback with per-triad status properties")
    void shouldCallTriadStartCallbackWithPerTriadStatusProperties() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = createTestTriads();

        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);
        when(llmClient.detectSceneAnalysisTriad(any(), any(), any(), eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)))
                .thenReturn(createMockTriadResult());

        @SuppressWarnings("unchecked")
        Consumer<Map<String, Object>> onTriadStart = mock(Consumer.class);

        sceneRelationshipAnalysisService.analyzeChapterTriadsWithIndividuals(testJobId, testChapter, onTriadStart);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> triadStatusCaptor = ArgumentCaptor.forClass(Map.class);
        verify(onTriadStart, times(2)).accept(triadStatusCaptor.capture());

        List<Map<String, Object>> statusMaps = triadStatusCaptor.getAllValues();
        assertThat(statusMaps.get(0)).containsEntry("triadIndex", 0);
        assertThat(statusMaps.get(0)).containsEntry("prevSceneIndex", null);
        assertThat(statusMaps.get(0)).containsEntry("currentSceneIndex", 0);
        assertThat(statusMaps.get(0)).containsEntry("nextSceneIndex", 1);

        assertThat(statusMaps.get(1)).containsEntry("triadIndex", 1);
        assertThat(statusMaps.get(1)).containsEntry("prevSceneIndex", 0);
        assertThat(statusMaps.get(1)).containsEntry("currentSceneIndex", 1);
        assertThat(statusMaps.get(1)).containsEntry("nextSceneIndex", 2);
    }

    @Test
    @DisplayName("Should handle empty triads list gracefully")
    void shouldHandleEmptyTriadsListGracefully() {
        Chapter testChapter = createTestChapter();
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(List.of());

        List<TriadAnalysisModels.SceneRelationshipAnalysis> result =
            sceneRelationshipAnalysisService.analyzeChapterTriads(testJobId, testChapter);

        assertThat(result).isEmpty();
        verify(llmClient, never()).detectSceneAnalysisTriad(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should call SceneDetectionClient for triad analysis")
    void shouldCallSceneDetectionClientForTriadAnalysis() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createSingleTriad());
        
        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);
        when(llmClient.detectSceneAnalysisTriad(any(), any(), any(), eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)))
                .thenReturn(createMockTriadResult());

        sceneRelationshipAnalysisService.analyzeChapterTriads(testJobId, testChapter);

        verify(llmClient).detectSceneAnalysisTriad(
                eq(testJobId),
                eq("mock system prompt"),
                any(),
                eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)
        );
    }

    @Test
    @DisplayName("Should include proper user variables for triad LLM call")
    void shouldIncludeProperUserVariablesForTriadLlmCall() {
        Chapter testChapter = createTestChapterWithText();
        List<TriadBuilderService.SceneTriad> triads = List.of(createSingleTriad());
        
        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);
        when(llmClient.detectSceneAnalysisTriad(any(), any(), any(), eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)))
                .thenReturn(createMockTriadResult());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> userVarsCaptor = ArgumentCaptor.forClass(Map.class);

        sceneRelationshipAnalysisService.analyzeChapterTriads(testJobId, testChapter);

        verify(llmClient).detectSceneAnalysisTriad(
                eq(testJobId),
                eq("mock system prompt"),
                userVarsCaptor.capture(),
                eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)
        );
        
        Map<String, Object> userVars = userVarsCaptor.getValue();
        assertThat(userVars).containsKeys(
            "prev_context_summary", "prev_time_indicators", "prev_break_reason", "prev_text",
            "curr_context_summary", "curr_time_indicators", "curr_break_reason", "curr_text",
            "next_context_summary", "next_time_indicators", "next_break_reason", "next_text"
        );
    }

    @Test
    @DisplayName("Should fail when required previousToCurrent relation is missing")
    void shouldFailWhenPreviousToCurrentRelationMissing() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createTriadWithPreviousAndCurrent());

        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);

        SceneRelationshipAnalysisService.TriadStructuredResult invalid =
                new SceneRelationshipAnalysisService.TriadStructuredResult("marker", null,
                        new SceneRelationshipAnalysisService.TriadRelation("BEFORE", "Explicit", "evidence"));

        when(llmClient.detectSceneAnalysisTriad(any(), any(), any(), eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)))
                .thenReturn(invalid);

        assertThatThrownBy(() -> sceneRelationshipAnalysisService.analyzeChapterTriads(testJobId, testChapter))
                .isInstanceOf(TriadAnalysisException.class)
                .hasMessageContaining("omitted required relation 'previousToCurrent'");

        verify(llmClient, times(2)).detectSceneAnalysisTriad(
                eq(testJobId),
                eq("mock system prompt"),
                any(),
                eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)
        );
    }

    @Test
    @DisplayName("Should retry semantic triad validation failures before succeeding")
    void shouldRetrySemanticTriadValidationFailuresBeforeSucceeding() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createTriadWithPreviousAndCurrent());

        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);

        SceneRelationshipAnalysisService.TriadStructuredResult invalid =
                new SceneRelationshipAnalysisService.TriadStructuredResult("marker", null,
                        new SceneRelationshipAnalysisService.TriadRelation("R:temporal.before", "Explicit", "evidence"));
        SceneRelationshipAnalysisService.TriadStructuredResult valid =
                new SceneRelationshipAnalysisService.TriadStructuredResult(
                        "marker",
                        new SceneRelationshipAnalysisService.TriadRelation("R:temporal.before", "Explicit", "retry evidence"),
                        null
                );

        when(llmClient.detectSceneAnalysisTriad(any(), any(), any(), eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)))
                .thenReturn(invalid)
                .thenReturn(valid);

        List<TriadAnalysisModels.SceneRelationshipAnalysis> result =
                sceneRelationshipAnalysisService.analyzeChapterTriads(testJobId, testChapter);

        assertThat(result).singleElement().satisfies(analysis -> {
            assertThat(analysis.prevToCurrType()).isEqualTo("R:temporal.before");
            assertThat(analysis.prevToCurrEvidence()).isEqualTo("retry evidence");
        });
        verify(llmClient, times(2)).detectSceneAnalysisTriad(
                eq(testJobId),
                eq("mock system prompt"),
                any(),
                eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)
        );
    }

    @Test
    @DisplayName("Should normalize legacy meets relation to canonical before")
    void shouldNormalizeLegacyMeetsRelationToCanonicalBefore() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createTriadWithPreviousAndCurrent());

        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);

        SceneRelationshipAnalysisService.TriadStructuredResult legacy =
                new SceneRelationshipAnalysisService.TriadStructuredResult(
                        "marker",
                        new SceneRelationshipAnalysisService.TriadRelation("R:temporal.meets", "Explicit", "evidence"),
                        null
                );

        when(llmClient.detectSceneAnalysisTriad(any(), any(), any(), eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)))
                .thenReturn(legacy);

        List<TriadAnalysisModels.SceneRelationshipAnalysis> result =
                sceneRelationshipAnalysisService.analyzeChapterTriads(testJobId, testChapter);

        assertThat(result).singleElement().satisfies(analysis -> {
            assertThat(analysis.prevToCurrType()).isEqualTo("R:temporal.before");
            assertThat(analysis.currVsPrevInverted()).isEqualTo("R:temporal.after");
        });
    }

    @Test
    @DisplayName("Should preserve during as distinct relation in practical vocabulary")
    void shouldPreserveDuringAsDistinctRelation() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createTriadWithPreviousAndCurrent());

        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);

        SceneRelationshipAnalysisService.TriadStructuredResult parsed =
                new SceneRelationshipAnalysisService.TriadStructuredResult(
                        "marker",
                        new SceneRelationshipAnalysisService.TriadRelation("R:temporal.during", "Explicit", "evidence"),
                        null
                );

        when(llmClient.detectSceneAnalysisTriad(any(), any(), any(), eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)))
                .thenReturn(parsed);

        List<TriadAnalysisModels.SceneRelationshipAnalysis> result =
                sceneRelationshipAnalysisService.analyzeChapterTriads(testJobId, testChapter);

        assertThat(result).singleElement().satisfies(analysis -> {
            assertThat(analysis.prevToCurrType()).isEqualTo("R:temporal.during");
            assertThat(analysis.currVsPrevInverted()).isEqualTo("R:temporal.contains");
        });
    }

    @Test
    @DisplayName("Should fail when relation type is outside ADR010 practical vocabulary")
    void shouldFailWhenRelationTypeIsOutsideAdr010PracticalVocabulary() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createTriadWithPreviousAndCurrent());

        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);

        SceneRelationshipAnalysisService.TriadStructuredResult invalid =
                new SceneRelationshipAnalysisService.TriadStructuredResult(
                        "marker",
                        new SceneRelationshipAnalysisService.TriadRelation("IMMEDIATE_SUCCESSION", "Explicit", "evidence"),
                        null
                );

        when(llmClient.detectSceneAnalysisTriad(any(), any(), any(), eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)))
                .thenReturn(invalid);

        assertThatThrownBy(() -> sceneRelationshipAnalysisService.analyzeChapterTriads(testJobId, testChapter))
                .isInstanceOf(TriadAnalysisException.class)
                .hasMessageContaining("unsupported temporalType 'IMMEDIATE_SUCCESSION'");
    }

    @Test
    @DisplayName("Should coarsen legacy equals relation to overlaps")
    void shouldCoarsenLegacyEqualsRelationToOverlaps() {
        Chapter testChapter = createTestChapter();
        List<TriadBuilderService.SceneTriad> triads = List.of(createTriadWithPreviousAndCurrent());

        PromptTemplate mockTemplate = mock(PromptTemplate.class);
        when(promptRepository.get("scene-analysis")).thenReturn(mockTemplate);
        when(mockTemplate.render(any())).thenReturn("mock system prompt");
        when(triadBuilderService.buildTriadsForChapter(testChapter)).thenReturn(triads);

        SceneRelationshipAnalysisService.TriadStructuredResult legacy =
                new SceneRelationshipAnalysisService.TriadStructuredResult(
                        "marker",
                        new SceneRelationshipAnalysisService.TriadRelation("R:temporal.equals", "Explicit", "same moment legacy output"),
                        null
                );

        when(llmClient.detectSceneAnalysisTriad(any(), any(), any(), eq(SceneRelationshipAnalysisService.TriadStructuredResult.class)))
                .thenReturn(legacy);

        List<TriadAnalysisModels.SceneRelationshipAnalysis> result =
                sceneRelationshipAnalysisService.analyzeChapterTriads(testJobId, testChapter);

        assertThat(result).singleElement().satisfies(analysis -> {
            assertThat(analysis.prevToCurrType()).isEqualTo("R:temporal.overlaps");
            assertThat(analysis.currVsPrevInverted()).isEqualTo("R:temporal.overlapped_by");
        });
    }

    private Chapter createTestChapter() {
        Chapter chapter = new Chapter();
        chapter.setId(testChapterId);
        chapter.setRawText("Sample chapter text for testing purposes.");
        return chapter;
    }

    private Chapter createTestChapterWithText() {
        Chapter chapter = new Chapter();
        chapter.setId(testChapterId);
        chapter.setRawText("This is scene one text. This is scene two text. This is scene three text.");
        return chapter;
    }

    private List<TriadBuilderService.SceneTriad> createTestTriads() {
        Scene scene1 = createScene(scene1Id, 0, 0L, 20L);
        Scene scene2 = createScene(scene2Id, 1, 21L, 40L);
        Scene scene3 = createScene(scene3Id, 2, 41L, 60L);

        return List.of(
            new TriadBuilderService.SceneTriad(null, scene1, scene2),
            new TriadBuilderService.SceneTriad(scene1, scene2, scene3)
        );
    }

    private TriadBuilderService.SceneTriad createSingleTriad() {
        Scene scene1 = createScene(scene1Id, 0, 0L, 20L);
        Scene scene2 = createScene(scene2Id, 1, 21L, 40L);
        return new TriadBuilderService.SceneTriad(null, scene1, scene2);
    }

    private TriadBuilderService.SceneTriad createTriadWithPreviousAndCurrent() {
        Scene scene0 = createScene(scene1Id, 0, 0L, 20L);
        Scene scene1 = createScene(scene2Id, 1, 21L, 40L);
        return new TriadBuilderService.SceneTriad(scene0, scene1, null);
    }

    private Scene createScene(UUID id, int index, Long start, Long end) {
        return new Scene(
                id,
                index,
                start,
                end,
                "Context summary for scene " + index,
                null,
                testChapterId,
                null,
                null,
                null,
                null,
                null
        );
    }

    private SceneRelationshipAnalysisService.TriadStructuredResult createMockTriadResult() {
        SceneRelationshipAnalysisService.TriadRelation mockPrevToCurr = new SceneRelationshipAnalysisService.TriadRelation(
            "R:temporal.before", "Explicit", "Scene transition analysis"
        );
        SceneRelationshipAnalysisService.TriadRelation mockCurrToNext = new SceneRelationshipAnalysisService.TriadRelation(
            "R:temporal.overlaps", "StronglyImplied", "Overlapping events"
        );
        
        return new SceneRelationshipAnalysisService.TriadStructuredResult(
            "Timeline marker: Chapter 1",
            mockPrevToCurr,
            mockCurrToNext
        );
    }

    // -----------------------------------------------------------------------
    // Relation claim normalization tests (private methods via reflection)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Relation Claim Normalization")
    class RelationClaimNormalizationTests {

        private Object invokePrivateMethod(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
            Method method = SceneRelationshipAnalysisService.class.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(sceneRelationshipAnalysisService, args);
        }

        // -- parseEntityRef --------------------------------------------------

        @Test
        @DisplayName("Should parse standard 'Kind: Name' format")
        void shouldParseStandardKindNameFormat() throws Exception {
            String[] result = (String[]) invokePrivateMethod("parseEntityRef",
                    new Class<?>[]{String.class}, "Individual: Frodo");
            assertThat(result).containsExactly("Individual", "Frodo");
        }

        @Test
        @DisplayName("Should parse 'Collective: Bridge Crew' as Collective kind")
        void shouldParseCollectiveKindNameFormat() throws Exception {
            String[] result = (String[]) invokePrivateMethod("parseEntityRef",
                    new Class<?>[]{String.class}, "Collective: Bridge Crew");
            assertThat(result).containsExactly("Collective", "Bridge Crew");
        }

        @Test
        @DisplayName("Should parse 'Kind:Name' without space after colon")
        void shouldParseKindNameWithoutSpaceAfterColon() throws Exception {
            String[] result = (String[]) invokePrivateMethod("parseEntityRef",
                    new Class<?>[]{String.class}, "Individual:Frodo");
            assertThat(result).containsExactly("Individual", "Frodo");
        }

        @Test
        @DisplayName("Should return null kind and full name when no kind separator present")
        void shouldReturnNullKindWhenNoSeparator() throws Exception {
            String[] result = (String[]) invokePrivateMethod("parseEntityRef",
                    new Class<?>[]{String.class}, "Frodo");
            assertThat(result[0]).isNull();
            assertThat(result[1]).isEqualTo("Frodo");
        }

        @Test
        @DisplayName("Should return null array entries for null input")
        void shouldReturnNullsForNullInput() throws Exception {
            String[] result = (String[]) invokePrivateMethod("parseEntityRef",
                    new Class<?>[]{String.class}, (Object) null);
            assertThat(result[0]).isNull();
            assertThat(result[1]).isNull();
        }

        @Test
        @DisplayName("Should return null kind for non-standard entity kind with WARN")
        void shouldReturnNullKindForNonStandardKind() throws Exception {
            String[] result = (String[]) invokePrivateMethod("parseEntityRef",
                    new Class<?>[]{String.class}, "Person: Frodo");
            assertThat(result[0]).isNull();
            assertThat(result[1]).isEqualTo("Frodo");
        }

        @Test
        @DisplayName("Should handle empty name after colon-space gracefully")
        void shouldHandleEmptyNameAfterColonSpace() throws Exception {
            String[] result = (String[]) invokePrivateMethod("parseEntityRef",
                    new Class<?>[]{String.class}, "Object: ");
            assertThat(result[0]).isNull();
            assertThat(result[1]).isNotNull();
        }

        // -- generateProvisionalRelTypeId ------------------------------------

        @Test
        @DisplayName("Should generate provisional rel type id for 'betrayed'")
        void shouldGenerateProvisionalIdForBetrayed() throws Exception {
            String result = (String) invokePrivateMethod("generateProvisionalRelTypeId",
                    new Class<?>[]{String.class}, "betrayed");
            assertThat(result).isEqualTo("R:provisional.betrayed");
        }

        @Test
        @DisplayName("Should generate provisional rel type id for 'trained under'")
        void shouldGenerateProvisionalIdForTrainedUnder() throws Exception {
            String result = (String) invokePrivateMethod("generateProvisionalRelTypeId",
                    new Class<?>[]{String.class}, "trained under");
            assertThat(result).isEqualTo("R:provisional.trained_under");
        }

        @Test
        @DisplayName("Should generate provisional rel type id for 'turned on'")
        void shouldGenerateProvisionalIdForTurnedOn() throws Exception {
            String result = (String) invokePrivateMethod("generateProvisionalRelTypeId",
                    new Class<?>[]{String.class}, "turned on");
            assertThat(result).isEqualTo("R:provisional.turned_on");
        }

        @Test
        @DisplayName("Should return null for null relation name")
        void shouldReturnNullForNullRelationName() throws Exception {
            String result = (String) invokePrivateMethod("generateProvisionalRelTypeId",
                    new Class<?>[]{String.class}, (Object) null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return unparseable fallback for all-non-alphanumeric input")
        void shouldReturnUnparseableForNonAlphanumericInput() throws Exception {
            String result = (String) invokePrivateMethod("generateProvisionalRelTypeId",
                    new Class<?>[]{String.class}, "!!!");
            assertThat(result).isEqualTo("R:provisional.unparseable");
        }

        @Test
        @DisplayName("Should generate provisional rel type id for 'member of'")
        void shouldGenerateProvisionalIdForMemberOf() throws Exception {
            String result = (String) invokePrivateMethod("generateProvisionalRelTypeId",
                    new Class<?>[]{String.class}, "member of");
            assertThat(result).isEqualTo("R:provisional.member_of");
        }

        // -- normalizeCertainty ----------------------------------------------

        @Test
        @DisplayName("Should normalize 'Explicit' certainty")
        void shouldNormalizeExplicitCertainty() throws Exception {
            String result = (String) invokePrivateMethod("normalizeCertainty",
                    new Class<?>[]{String.class}, "Explicit");
            assertThat(result).isEqualTo("Explicit");
        }

        @Test
        @DisplayName("Should normalize 'StronglyImplied' certainty")
        void shouldNormalizeStronglyImpliedCertainty() throws Exception {
            String result = (String) invokePrivateMethod("normalizeCertainty",
                    new Class<?>[]{String.class}, "StronglyImplied");
            assertThat(result).isEqualTo("StronglyImplied");
        }

        @Test
        @DisplayName("Should normalize 'WeaklyImplied' certainty")
        void shouldNormalizeWeaklyImpliedCertainty() throws Exception {
            String result = (String) invokePrivateMethod("normalizeCertainty",
                    new Class<?>[]{String.class}, "WeaklyImplied");
            assertThat(result).isEqualTo("WeaklyImplied");
        }

        @Test
        @DisplayName("Should normalize lowercase 'explicit' to 'Explicit'")
        void shouldNormalizeLowercaseExplicit() throws Exception {
            String result = (String) invokePrivateMethod("normalizeCertainty",
                    new Class<?>[]{String.class}, "explicit");
            assertThat(result).isEqualTo("Explicit");
        }

        @Test
        @DisplayName("Should return WeaklyImplied default for null certainty")
        void shouldReturnWeaklyImpliedForNull() throws Exception {
            String result = (String) invokePrivateMethod("normalizeCertainty",
                    new Class<?>[]{String.class}, (Object) null);
            assertThat(result).isEqualTo("WeaklyImplied");
        }

        @Test
        @DisplayName("Should return WeaklyImplied default for unknown certainty value")
        void shouldReturnWeaklyImpliedForUnknownValue() throws Exception {
            String result = (String) invokePrivateMethod("normalizeCertainty",
                    new Class<?>[]{String.class}, "probably");
            assertThat(result).isEqualTo("WeaklyImplied");
        }

        // -- truncate --------------------------------------------------------

        @Test
        @DisplayName("Should return null for null value")
        void shouldReturnNullForNullValue() throws Exception {
            String result = (String) invokePrivateMethod("truncate",
                    new Class<?>[]{String.class, int.class}, (Object) null, 10);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should not truncate when value is shorter than maxLength")
        void shouldNotTruncateShortValue() throws Exception {
            String result = (String) invokePrivateMethod("truncate",
                    new Class<?>[]{String.class, int.class}, "short", 10);
            assertThat(result).isEqualTo("short");
        }

        @Test
        @DisplayName("Should truncate with ellipsis when value exceeds maxLength")
        void shouldTruncateWithEllipsis() throws Exception {
            String result = (String) invokePrivateMethod("truncate",
                    new Class<?>[]{String.class, int.class}, "a very long string", 7);
            assertThat(result).isEqualTo("a very …");
        }

        @Test
        @DisplayName("Should not split surrogate pairs when truncating emoji")
        void shouldNotSplitSurrogatePairs() throws Exception {
            // "🌍🌎test" has 6 code points; truncate to first 2 code points
            String result = (String) invokePrivateMethod("truncate",
                    new Class<?>[]{String.class, int.class}, "\uD83C\uDF0D\uD83C\uDF0Etest", 2);
            // Should keep 2 code points (🌍🌎 = 2 surrogate pairs = 4 chars) + ellipsis
            assertThat(result).isEqualTo("\uD83C\uDF0D\uD83C\uDF0E…");
        }
    }

}
