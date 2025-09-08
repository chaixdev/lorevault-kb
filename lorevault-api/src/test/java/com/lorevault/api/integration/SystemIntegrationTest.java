package com.lorevault.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.LoreVaultApiApplication;
import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.*;
import com.lorevault.api.domain.ingestion.IngestionJob;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.dto.ask.AskDtos.*;
import com.lorevault.api.dto.ingestion.*;
import com.lorevault.api.dto.search.SemanticSearchDtos.*;
import com.lorevault.api.dto.shared.PublicationCoordinates;
import com.lorevault.api.service.ingestion.IngestionService;
import com.lorevault.api.service.search.SemanticSearchService;
import com.lorevault.api.service.ask.RagService;
import com.lorevault.api.testing.TestImages;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive system integration tests for LoreVault after service consolidation.
 * Validates that all end-to-end workflows function identically to pre-refactor state.
 * 
 * Tests the complete CQRS pipeline:
 * - Command: POST /api/command/ingest (file upload)
 * - Query: GET /api/query/jobs/{id} (job status monitoring) 
 * - Query: POST /api/query/ask/vector (semantic search)
 * - Query: POST /api/query/ask/rag (RAG question answering)
 */
@SpringBootTest(
    classes = LoreVaultApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureWebMvc
@Tag("integration")
class SystemIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(TestImages.NEO4J_IMAGE)
            .withAdminPassword("test123")
            .withReuse(true);

    @DynamicPropertySource
    static void setNeo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4jContainer::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "test123");
    }

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private IngestionService ingestionService;
    
    @Autowired
    private SemanticSearchService semanticSearchService;
    
    @Autowired
    private RagService ragService;
    
    @Autowired
    private ContentPersistencePort contentPersistencePort;

    @MockitoBean
    @Qualifier("nlpSmall")
    private ChatClient nlpSmallChatClient;

    @MockitoBean
    @Qualifier("nlpBig")
    private ChatClient nlpBigChatClient;

    private UUID testBookId;
    private String testChapterContent;

    @BeforeEach
    void setUp() {
        testBookId = UUID.randomUUID();
        testChapterContent = """
            This is the opening scene of our test chapter. Our protagonist walks through the bustling marketplace, 
            observing the merchants hawking their wares and the crowds of people moving between the stalls.
            
            The second scene shifts to a quieter setting. The protagonist enters a small tavern where 
            they meet with a mysterious contact who provides crucial information about the quest ahead.
            
            In the final scene, the protagonist prepares for the journey by gathering supplies and saying 
            farewell to trusted allies. The chapter ends with them setting out on the road at dawn.
            """;
    }

    @Test
    @DisplayName("Complete Ingestion Workflow: File Upload → Processing → Completion")
    void completeIngestionWorkflow() throws Exception {
        // Step 1: Submit chapter file for ingestion
        MockMultipartFile testFile = new MockMultipartFile(
            "file",
            "test-chapter.txt", 
            "text/plain",
            testChapterContent.getBytes()
        );

        MvcResult ingestionResult = mockMvc.perform(
            multipart("/api/command/ingest")
                .file(testFile)
                .param("bookId", testBookId.toString())
                .param("chapterNumber", "1")
                .param("chapterTitle", "Test Chapter")
        )
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").exists())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andReturn();

        // Extract job ID from response
        String responseBody = ingestionResult.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
        String jobId = (String) responseMap.get("jobId");
        
        assertThat(jobId).isNotNull();

        // Step 2: Monitor job status until completion
        await().atMost(30, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                MvcResult statusResult = mockMvc.perform(
                    get("/api/query/jobs/{jobId}", jobId)
                )
                .andExpect(status().isOk())
                .andReturn();

                String statusBody = statusResult.getResponse().getContentAsString();
                JobStatusResponse jobStatus = objectMapper.readValue(statusBody, JobStatusResponse.class);
                
                // Job should eventually complete successfully
                assertThat(jobStatus.getCurrentStatus()).isIn(
                    IngestionStatus.COMPLETE, 
                    IngestionStatus.EMBEDDING_CHUNKS,
                    IngestionStatus.DETECTING_SCENES,
                    IngestionStatus.EMBEDDING_CHUNKS
                );
            });

        // Step 3: Verify final job completion
        MvcResult finalStatusResult = mockMvc.perform(
            get("/api/query/jobs/{jobId}", jobId)
        )
        .andExpect(status().isOk())
        .andReturn();

        String finalStatusBody = finalStatusResult.getResponse().getContentAsString();
        JobStatusResponse finalStatus = objectMapper.readValue(finalStatusBody, JobStatusResponse.class);
        
        assertAll("Final job status validation",
            () -> assertThat(finalStatus.getCurrentStatus()).isEqualTo(IngestionStatus.COMPLETE),
            () -> assertThat(finalStatus.getJobId()).isEqualTo(UUID.fromString(jobId)),
            () -> assertThat(finalStatus.getChapterId()).isNotNull()
        );

        // Step 4: Verify chapter and content were created
        UUID chapterId = finalStatus.getChapterId();
        Optional<Chapter> chapter = contentPersistencePort.findChapterById(chapterId);
        
        assertThat(chapter).isPresent();
        assertThat(chapter.get().getRawText()).isEqualTo(testChapterContent);

        // Step 5: Verify scenes were detected and persisted
        List<Scene> scenes = contentPersistencePort.findScenesByChapterId(chapterId);
        assertThat(scenes).isNotEmpty();
        
        // Step 6: Verify chunks were created from scenes
        List<Chunk> chunks = contentPersistencePort.findChunksByChapterId(chapterId);
        assertThat(chunks).isNotEmpty();
        
        // Step 7: Verify embeddings were generated (at least some chunks should have embeddings)
        List<Chunk> chunksWithEmbeddings = chunks.stream()
            .filter(chunk -> chunk.getEmbedding() != null && chunk.getEmbedding().length > 0)
            .toList();
        assertThat(chunksWithEmbeddings).isNotEmpty();
    }

    @Test
    @DisplayName("Semantic Search Workflow: Query Processing and Results")
    void semanticSearchWorkflow() throws Exception {
        // Prerequisites: Create test chapter with chunks and embeddings
        UUID chapterId = createTestChapterWithEmbeddings();

        // Step 1: Perform semantic search via API
        SemanticSearchRequest searchRequest = new SemanticSearchRequest();
        searchRequest.setQuery("marketplace merchants");
        searchRequest.setTopK(5);

        MvcResult searchResult = mockMvc.perform(
            post("/api/query/ask/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(searchRequest))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results").isArray())
        .andExpect(jsonPath("$.metadata.processingTimeMs").isNumber())
        .andReturn();

        String searchBody = searchResult.getResponse().getContentAsString();
        SemanticSearchResponse searchResponse = objectMapper.readValue(searchBody, SemanticSearchResponse.class);

        assertAll("Search response validation",
            () -> assertThat(searchResponse.getResults()).isNotEmpty(),
            () -> assertThat(searchResponse.getMetadata().getProcessingTimeMs()).isGreaterThan(0),
            () -> assertThat(searchResponse.getResults().get(0).getScore()).isGreaterThan(0.0)
        );

        // Step 2: Verify search results contain expected content
        List<SearchResultDto> results = searchResponse.getResults();
        boolean foundMarketplaceContent = results.stream()
            .anyMatch(result -> result.getSnippet().toLowerCase().contains("marketplace") || 
                               result.getSnippet().toLowerCase().contains("merchant"));
        
        assertThat(foundMarketplaceContent).isTrue();
    }

    @Test
    @DisplayName("RAG Question Answering Workflow: Question Processing and Citation")
    void ragQuestionAnsweringWorkflow() throws Exception {
        // Prerequisites: Create test chapter with chunks and embeddings
        UUID chapterId = createTestChapterWithEmbeddings();

        // Step 1: Ask a question using RAG
        AskRequest askRequest = new AskRequest();
        askRequest.setQuestion("What does the protagonist do in the marketplace?");
        askRequest.setTopK(3);

        MvcResult ragResult = mockMvc.perform(
            post("/api/query/ask/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(askRequest))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").isString())
        .andExpect(jsonPath("$.citations").isArray())
        .andExpect(jsonPath("$.metadata.processingTimeMs").isNumber())
        .andReturn();

        String ragBody = ragResult.getResponse().getContentAsString();
        AskResponse ragResponse = objectMapper.readValue(ragBody, AskResponse.class);

        assertAll("RAG response validation",
            () -> assertThat(ragResponse.getAnswer()).isNotBlank(),
            () -> assertThat(ragResponse.getCitations()).isNotEmpty(),
            () -> assertThat(ragResponse.getMetadata().getProcessingTimeMs()).isGreaterThan(0)
        );

        // Step 2: Verify citations reference actual content
        List<CitationDto> citations = ragResponse.getCitations();
        for (CitationDto citation : citations) {
            assertAll("Citation validation",
                () -> assertThat(citation.getChunkId()).isNotNull(),
                () -> assertThat(citation.getSnippet()).isNotBlank(),
                () -> assertThat(citation.getScore()).isGreaterThan(0.0)
            );
        }
    }

    @Test
    @DisplayName("Error Handling: Invalid Input and System Failures")
    void errorHandlingScenarios() throws Exception {
        // Test 1: Missing file upload
        mockMvc.perform(
            multipart("/api/command/ingest")
                .param("bookId", UUID.randomUUID().toString())
                .param("chapterNumber", "1")
                .param("chapterTitle", "Test")
        )
        .andExpect(status().isBadRequest());

        // Test 2: Invalid book ID format
        MockMultipartFile testFile = new MockMultipartFile(
            "file", "test.txt", "text/plain", "content".getBytes()
        );

        mockMvc.perform(
            multipart("/api/command/ingest")
                .file(testFile)
                .param("bookId", "invalid-uuid")
                .param("chapterNumber", "1")
                .param("chapterTitle", "Test")
        )
        .andExpect(status().isBadRequest());

        // Test 3: Non-existent job status query
        String nonExistentJobId = UUID.randomUUID().toString();
        mockMvc.perform(
            get("/api/query/jobs/{jobId}", nonExistentJobId)
        )
        .andExpect(status().isNotFound());

        // Test 4: Empty search query
        SemanticSearchRequest emptySearchRequest = new SemanticSearchRequest();
        emptySearchRequest.setQuery("");
        emptySearchRequest.setTopK(5);

        mockMvc.perform(
            post("/api/query/ask/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptySearchRequest))
        )
        .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Duplicate Chapter Handling: Idempotency and Conflict Resolution")
    void duplicateChapterHandling() throws Exception {
        MockMultipartFile testFile = new MockMultipartFile(
            "file", "duplicate-test.txt", "text/plain", testChapterContent.getBytes()
        );

        // Step 1: Submit original chapter
        MvcResult firstSubmission = mockMvc.perform(
            multipart("/api/command/ingest")
                .file(testFile)
                .param("bookId", testBookId.toString())
                .param("chapterNumber", "1")
                .param("chapterTitle", "Original Chapter")
        )
        .andExpect(status().isAccepted())
        .andReturn();

        String firstResponseBody = firstSubmission.getResponse().getContentAsString();
        Map<String, Object> firstResponse = objectMapper.readValue(firstResponseBody, Map.class);
        String firstJobId = (String) firstResponse.get("jobId");

        // Step 2: Submit identical chapter (should detect duplicate)
        MvcResult secondSubmission = mockMvc.perform(
            multipart("/api/command/ingest")
                .file(testFile)
                .param("bookId", testBookId.toString())
                .param("chapterNumber", "1")
                .param("chapterTitle", "Duplicate Chapter")
        )
        .andExpect(status().isAccepted())
        .andReturn();

        String secondResponseBody = secondSubmission.getResponse().getContentAsString();
        Map<String, Object> secondResponse = objectMapper.readValue(secondResponseBody, Map.class);
        String secondJobId = (String) secondResponse.get("jobId");

        // Both submissions should be accepted but may handle duplicates differently
        assertThat(firstJobId).isNotNull();
        assertThat(secondJobId).isNotNull();

        // Wait for both jobs to complete
        await().atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                // Check first job
                MvcResult firstStatus = mockMvc.perform(get("/api/query/jobs/{jobId}", firstJobId))
                    .andExpect(status().isOk()).andReturn();
                JobStatusResponse firstJobStatus = objectMapper.readValue(
                    firstStatus.getResponse().getContentAsString(), JobStatusResponse.class);
                
                // Check second job  
                MvcResult secondStatus = mockMvc.perform(get("/api/query/jobs/{jobId}", secondJobId))
                    .andExpect(status().isOk()).andReturn();
                JobStatusResponse secondJobStatus = objectMapper.readValue(
                    secondStatus.getResponse().getContentAsString(), JobStatusResponse.class);

                // Both jobs should complete (regardless of duplicate handling strategy)
                assertThat(firstJobStatus.getCurrentStatus()).isEqualTo(IngestionStatus.COMPLETE);
                assertThat(secondJobStatus.getCurrentStatus()).isEqualTo(IngestionStatus.COMPLETE);
            });
    }

    @Test
    @DisplayName("Concurrency: Multiple Simultaneous Chapter Submissions")
    void concurrentChapterSubmissions() throws Exception {
        int concurrentSubmissions = 3;
        List<String> jobIds = new ArrayList<>();

        // Submit multiple chapters concurrently
        for (int i = 0; i < concurrentSubmissions; i++) {
            String chapterContent = "Chapter " + (i + 1) + " content: " + testChapterContent;
            MockMultipartFile testFile = new MockMultipartFile(
                "file", "chapter-" + (i + 1) + ".txt", "text/plain", chapterContent.getBytes()
            );

            MvcResult result = mockMvc.perform(
                multipart("/api/command/ingest")
                    .file(testFile)
                    .param("bookId", testBookId.toString())
                    .param("chapterNumber", String.valueOf(i + 1))
                    .param("chapterTitle", "Chapter " + (i + 1))
            )
            .andExpect(status().isAccepted())
            .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            jobIds.add((String) response.get("jobId"));
        }

        // Wait for all jobs to complete
        await().atMost(60, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                for (String jobId : jobIds) {
                    MvcResult statusResult = mockMvc.perform(get("/api/query/jobs/{jobId}", jobId))
                        .andExpect(status().isOk()).andReturn();
                    
                    JobStatusResponse status = objectMapper.readValue(
                        statusResult.getResponse().getContentAsString(), JobStatusResponse.class);
                    
                    assertThat(status.getCurrentStatus()).isEqualTo(IngestionStatus.COMPLETE);
                }
            });

        // Verify all chapters were created successfully
        for (String jobId : jobIds) {
            MvcResult statusResult = mockMvc.perform(get("/api/query/jobs/{jobId}", jobId))
                .andExpect(status().isOk()).andReturn();
            
            JobStatusResponse status = objectMapper.readValue(
                statusResult.getResponse().getContentAsString(), JobStatusResponse.class);
            
            UUID chapterId = status.getChapterId();
            Optional<Chapter> chapter = contentPersistencePort.findChapterById(chapterId);
            
            assertThat(chapter).isPresent();
            assertThat(chapter.get().getRawText()).contains("Chapter");
        }
    }

    @Test
    @DisplayName("Performance Baseline: Processing Time Validation")
    void performanceBaseline() throws Exception {
        long startTime = System.currentTimeMillis();

        // Create larger content for performance testing
        String largeContent = testChapterContent.repeat(5); // 5x larger content
        
        MockMultipartFile largeFile = new MockMultipartFile(
            "file", "large-chapter.txt", "text/plain", largeContent.getBytes()
        );

        // Submit large chapter
        MvcResult result = mockMvc.perform(
            multipart("/api/command/ingest")
                .file(largeFile)
                .param("bookId", testBookId.toString())
                .param("chapterNumber", "1")
                .param("chapterTitle", "Large Performance Test Chapter")
        )
        .andExpect(status().isAccepted())
        .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        String jobId = (String) response.get("jobId");

        // Wait for completion and measure total time
        await().atMost(120, TimeUnit.SECONDS) // Allow more time for larger content
            .untilAsserted(() -> {
                MvcResult statusResult = mockMvc.perform(get("/api/query/jobs/{jobId}", jobId))
                    .andExpect(status().isOk()).andReturn();
                
                JobStatusResponse status = objectMapper.readValue(
                    statusResult.getResponse().getContentAsString(), JobStatusResponse.class);
                
                assertThat(status.getCurrentStatus()).isEqualTo(IngestionStatus.COMPLETE);
            });

        long totalTime = System.currentTimeMillis() - startTime;

        // Performance assertions - should complete within reasonable time bounds
        assertThat(totalTime).isLessThan(120000); // Max 2 minutes for large content

        // Verify search performance on the larger dataset
        SemanticSearchRequest searchRequest = new SemanticSearchRequest();
        searchRequest.setQuery("protagonist marketplace");
        searchRequest.setTopK(10);

        long searchStartTime = System.currentTimeMillis();
        
        mockMvc.perform(
            post("/api/query/ask/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(searchRequest))
        )
        .andExpect(status().isOk());

        long searchTime = System.currentTimeMillis() - searchStartTime;
        
        // Search should be fast even with larger dataset
        assertThat(searchTime).isLessThan(5000); // Max 5 seconds for search
    }

    /**
     * Helper method to create a test chapter with chunks and embeddings for search tests
     */
    private UUID createTestChapterWithEmbeddings() {
        // Create test coordinates and chapter
        PublicationCoordinates coordinates = new PublicationCoordinates();
        coordinates.setUniverse("Test Universe");
        coordinates.setSeries("Test Series");  
        coordinates.setBookTitle("Test Book");
        coordinates.setBookNumber(1);
        coordinates.setChapterTitle("Test Chapter for Search");
        coordinates.setChapterNumber(1);

        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setCoordinates(coordinates);
        chapter.setRawText(testChapterContent);
        chapter.setCreatedAt(LocalDateTime.now());

        contentPersistencePort.createChapter(chapter);

        // Create test chunks with mock embeddings
        List<Chunk> chunks = List.of(
            createChunkWithEmbedding(chapter, 1, "protagonist walks through the bustling marketplace"),
            createChunkWithEmbedding(chapter, 2, "enters a small tavern where they meet mysterious contact"),
            createChunkWithEmbedding(chapter, 3, "protagonist prepares for the journey gathering supplies")
        );

        contentPersistencePort.addChunksToChapter(chapter.getId(), chunks);

        return chapter.getId();
    }

    private Chunk createChunkWithEmbedding(Chapter chapter, int sequenceNumber, String text) {
        Chunk chunk = new Chunk();
        chunk.setId(UUID.randomUUID());
        chunk.setChapter(chapter);
        chunk.setText(text);
        chunk.setChunkNumberInChapter(sequenceNumber);
        chunk.setStartCharInChapter((sequenceNumber - 1) * 100);
        chunk.setEndCharInChapter(sequenceNumber * 100);
        chunk.setCreatedAt(LocalDateTime.now());
        
        // Create mock embedding vector (simulate real embeddings)
        double[] mockEmbedding = new double[384]; // Common embedding dimension
        for (int i = 0; i < mockEmbedding.length; i++) {
            mockEmbedding[i] = Math.random() * 2 - 1; // Random values between -1 and 1
        }
        chunk.setEmbedding(mockEmbedding);
        
        return chunk;
    }
}