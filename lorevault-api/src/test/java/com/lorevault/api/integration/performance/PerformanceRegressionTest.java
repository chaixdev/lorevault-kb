package com.lorevault.api.integration.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.LoreVaultApiApplication;
import com.lorevault.api.application.port.ContentPersistencePort;
import com.lorevault.api.domain.content.Chapter;
import com.lorevault.api.domain.content.Chunk;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.dto.ask.AskDtos.*;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.dto.search.SemanticSearchDtos.*;
import com.lorevault.api.dto.shared.PublicationCoordinates;
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
 * Performance regression testing for LoreVault after service consolidation.
 * Validates that consolidation has not introduced performance degradation.
 * 
 * Establishes performance baselines for:
 * - Chapter ingestion throughput
 * - Scene detection processing time
 * - Search query response time  
 * - Memory usage patterns
 * - Concurrent processing capabilities
 */
@SpringBootTest(
    classes = LoreVaultApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureWebMvc
@Tag("integration")
@Tag("performance")
class PerformanceRegressionTest {

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
    private ContentPersistencePort contentPersistencePort;

    @MockitoBean
    @Qualifier("nlpSmall")
    private ChatClient nlpSmallChatClient;

    @MockitoBean
    @Qualifier("nlpBig")
    private ChatClient nlpBigChatClient;

    private static final String SMALL_CHAPTER_CONTENT = """
        This is a small test chapter with minimal content for baseline performance testing.
        It contains just enough text to trigger the chunking decision gate and create a few chunks.
        """;

    private static final String MEDIUM_CHAPTER_CONTENT = SMALL_CHAPTER_CONTENT.repeat(10);
    private static final String LARGE_CHAPTER_CONTENT = SMALL_CHAPTER_CONTENT.repeat(50);

    private UUID testBookId;

    @BeforeEach
    void setUp() {
        testBookId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Ingestion Performance: Small Chapter (<1KB)")
    void smallChapterIngestionPerformance() throws Exception {
        long startTime = System.currentTimeMillis();
        
        String jobId = submitChapterAndGetJobId(SMALL_CHAPTER_CONTENT, "Small Chapter", 1);
        
        // Wait for completion and measure time
        await().atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assertJobCompleted(jobId);
            });
            
        long totalTime = System.currentTimeMillis() - startTime;
        
        // Performance baseline: Small chapters should complete quickly
        assertThat(totalTime).isLessThan(15000); // Max 15 seconds
        
        // Verify output quality
        JobStatusResponse status = getJobStatus(jobId);
        UUID chapterId = status.getChapterId();
        
        List<Chunk> chunks = contentPersistencePort.findChunksByChapterId(chapterId);
        assertThat(chunks).isNotEmpty();
        
        System.out.printf("Small Chapter Performance: %d ms, %d chunks%n", totalTime, chunks.size());
    }

    @Test
    @DisplayName("Ingestion Performance: Medium Chapter (~10KB)")
    void mediumChapterIngestionPerformance() throws Exception {
        long startTime = System.currentTimeMillis();
        
        String jobId = submitChapterAndGetJobId(MEDIUM_CHAPTER_CONTENT, "Medium Chapter", 1);
        
        await().atMost(60, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assertJobCompleted(jobId);
            });
            
        long totalTime = System.currentTimeMillis() - startTime;
        
        // Performance baseline: Medium chapters should scale reasonably
        assertThat(totalTime).isLessThan(45000); // Max 45 seconds
        
        JobStatusResponse status = getJobStatus(jobId);
        UUID chapterId = status.getChapterId();
        
        List<Chunk> chunks = contentPersistencePort.findChunksByChapterId(chapterId);
        assertThat(chunks).isNotEmpty();
        
        System.out.printf("Medium Chapter Performance: %d ms, %d chunks%n", totalTime, chunks.size());
    }

    @Test
    @DisplayName("Ingestion Performance: Large Chapter (~50KB)")
    void largeChapterIngestionPerformance() throws Exception {
        long startTime = System.currentTimeMillis();
        
        String jobId = submitChapterAndGetJobId(LARGE_CHAPTER_CONTENT, "Large Chapter", 1);
        
        await().atMost(120, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assertJobCompleted(jobId);
            });
            
        long totalTime = System.currentTimeMillis() - startTime;
        
        // Performance baseline: Large chapters should still complete in reasonable time
        assertThat(totalTime).isLessThan(90000); // Max 90 seconds
        
        JobStatusResponse status = getJobStatus(jobId);
        UUID chapterId = status.getChapterId();
        
        List<Chunk> chunks = contentPersistencePort.findChunksByChapterId(chapterId);
        assertThat(chunks).isNotEmpty();
        
        System.out.printf("Large Chapter Performance: %d ms, %d chunks%n", totalTime, chunks.size());
    }

    @Test
    @DisplayName("Search Performance: Query Response Time")
    void searchQueryPerformance() throws Exception {
        // Setup: Create test data
        UUID chapterId = createTestChapterWithEmbeddings();
        
        SemanticSearchRequest searchRequest = new SemanticSearchRequest();
        searchRequest.setQuery("test content for performance evaluation");
        searchRequest.setTopK(10);

        // Warm up (first query may be slower due to initialization)
        mockMvc.perform(
            post("/api/query/ask/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(searchRequest))
        );

        // Measure actual search performance
        long startTime = System.currentTimeMillis();
        
        MvcResult result = mockMvc.perform(
            post("/api/query/ask/vector")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(searchRequest))
        )
        .andExpect(status().isOk())
        .andReturn();
        
        long searchTime = System.currentTimeMillis() - startTime;
        
        // Performance baseline: Search should be fast
        assertThat(searchTime).isLessThan(2000); // Max 2 seconds
        
        SemanticSearchResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), SemanticSearchResponse.class);
        
        assertThat(response.getResults()).isNotEmpty();
        System.out.printf("Search Performance: %d ms, %d results%n", 
            searchTime, response.getResults().size());
    }

    @Test
    @DisplayName("RAG Performance: Question Answering Response Time")
    void ragQueryPerformance() throws Exception {
        // Setup: Create test data
        UUID chapterId = createTestChapterWithEmbeddings();
        
        AskRequest askRequest = new AskRequest();
        askRequest.setQuestion("What is the main topic discussed in this content?");
        askRequest.setTopK(5);

        // Warm up
        mockMvc.perform(
            post("/api/query/ask/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(askRequest))
        );

        // Measure RAG performance
        long startTime = System.currentTimeMillis();
        
        MvcResult result = mockMvc.perform(
            post("/api/query/ask/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(askRequest))
        )
        .andExpect(status().isOk())
        .andReturn();
        
        long ragTime = System.currentTimeMillis() - startTime;
        
        // Performance baseline: RAG should complete within reasonable time
        assertThat(ragTime).isLessThan(5000); // Max 5 seconds
        
        AskResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), AskResponse.class);
        
        assertThat(response.getAnswer()).isNotBlank();
        assertThat(response.getCitations()).isNotEmpty();
        
        System.out.printf("RAG Performance: %d ms, answer length: %d, citations: %d%n", 
            ragTime, response.getAnswer().length(), response.getCitations().size());
    }

    @Test
    @DisplayName("Concurrent Processing: Multiple Simultaneous Jobs")
    void concurrentProcessingPerformance() throws Exception {
        int concurrentJobs = 5;
        List<String> jobIds = new ArrayList<>();
        
        long startTime = System.currentTimeMillis();
        
        // Submit multiple jobs concurrently
        for (int i = 1; i <= concurrentJobs; i++) {
            String content = MEDIUM_CHAPTER_CONTENT + " Chapter " + i + " unique content.";
            String jobId = submitChapterAndGetJobId(content, "Concurrent Chapter " + i, i);
            jobIds.add(jobId);
        }
        
        // Wait for all jobs to complete
        await().atMost(180, TimeUnit.SECONDS) // More time for concurrent processing
            .untilAsserted(() -> {
                for (String jobId : jobIds) {
                    assertJobCompleted(jobId);
                }
            });
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        // Performance baseline: Concurrent jobs should not significantly degrade individual performance
        assertThat(totalTime).isLessThan(150000); // Max 2.5 minutes for 5 concurrent jobs
        
        // Verify all jobs completed successfully
        for (String jobId : jobIds) {
            JobStatusResponse status = getJobStatus(jobId);
            assertThat(status.getCurrentStatus()).isEqualTo(IngestionStatus.COMPLETE);
        }
        
        System.out.printf("Concurrent Processing Performance: %d ms for %d jobs%n", 
            totalTime, concurrentJobs);
    }

    @Test
    @DisplayName("Throughput Test: Sequential Chapter Processing")
    void throughputPerformance() throws Exception {
        int numberOfChapters = 10;
        long startTime = System.currentTimeMillis();
        
        List<String> jobIds = new ArrayList<>();
        
        // Submit chapters sequentially and wait for each to complete
        for (int i = 1; i <= numberOfChapters; i++) {
            String content = SMALL_CHAPTER_CONTENT + " Sequential chapter " + i + ".";
            String jobId = submitChapterAndGetJobId(content, "Sequential Chapter " + i, i);
            
            // Wait for this job to complete before submitting next
            await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertJobCompleted(jobId));
                
            jobIds.add(jobId);
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        double averageTimePerChapter = (double) totalTime / numberOfChapters;
        
        // Performance baseline: Sequential processing should maintain consistent throughput
        assertThat(averageTimePerChapter).isLessThan(15000); // Max 15 seconds per chapter on average
        
        System.out.printf("Sequential Throughput: %d ms total, %.1f ms per chapter%n", 
            totalTime, averageTimePerChapter);
    }

    @Test
    @DisplayName("Memory Usage: Processing Large Dataset")
    void memoryUsagePerformance() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        
        // Measure initial memory
        System.gc(); // Suggest garbage collection
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Process several chapters to stress test memory usage
        List<String> jobIds = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String content = LARGE_CHAPTER_CONTENT + " Memory test chapter " + i + ".";
            String jobId = submitChapterAndGetJobId(content, "Memory Test Chapter " + i, i);
            jobIds.add(jobId);
        }
        
        // Wait for all to complete
        await().atMost(300, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                for (String jobId : jobIds) {
                    assertJobCompleted(jobId);
                }
            });
        
        // Measure final memory
        System.gc(); // Suggest garbage collection
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // Memory usage should be reasonable (less than 100MB increase)
        assertThat(memoryIncrease).isLessThan(100 * 1024 * 1024); // 100MB
        
        System.out.printf("Memory Usage: Initial: %d MB, Final: %d MB, Increase: %d MB%n", 
            initialMemory / (1024 * 1024), finalMemory / (1024 * 1024), memoryIncrease / (1024 * 1024));
    }

    // Helper Methods
    
    private String submitChapterAndGetJobId(String content, String title, int chapterNumber) throws Exception {
        MockMultipartFile testFile = new MockMultipartFile(
            "file", title + ".txt", "text/plain", content.getBytes()
        );

        MvcResult result = mockMvc.perform(
            multipart("/api/command/ingest")
                .file(testFile)
                .param("bookId", testBookId.toString())
                .param("chapterNumber", String.valueOf(chapterNumber))
                .param("chapterTitle", title)
        )
        .andExpect(status().isAccepted())
        .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        return (String) response.get("jobId");
    }
    
    private void assertJobCompleted(String jobId) throws Exception {
        JobStatusResponse status = getJobStatus(jobId);
        assertThat(status.getCurrentStatus()).isEqualTo(IngestionStatus.COMPLETE);
    }
    
    private JobStatusResponse getJobStatus(String jobId) throws Exception {
        MvcResult statusResult = mockMvc.perform(get("/api/query/jobs/{jobId}", jobId))
            .andExpect(status().isOk())
            .andReturn();
        
        return objectMapper.readValue(statusResult.getResponse().getContentAsString(), JobStatusResponse.class);
    }
    
    private UUID createTestChapterWithEmbeddings() {
        PublicationCoordinates coordinates = new PublicationCoordinates();
        coordinates.setUniverse("Performance Test Universe");
        coordinates.setSeries("Performance Test Series");
        coordinates.setBookTitle("Performance Test Book");
        coordinates.setBookNumber(1);
        coordinates.setChapterTitle("Performance Test Chapter");
        coordinates.setChapterNumber(1);

        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setCoordinates(coordinates);
        chapter.setRawText("Performance test content for search and RAG evaluation with meaningful text.");
        chapter.setCreatedAt(LocalDateTime.now());

        contentPersistencePort.createChapter(chapter);

        // Create test chunks with embeddings
        List<Chunk> chunks = List.of(
            createChunkWithEmbedding(chapter, 1, "Performance test content with evaluation metrics"),
            createChunkWithEmbedding(chapter, 2, "Search functionality performance measurement data"),
            createChunkWithEmbedding(chapter, 3, "RAG question answering benchmark content")
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
        
        // Create mock embedding vector
        double[] mockEmbedding = new double[384];
        for (int i = 0; i < mockEmbedding.length; i++) {
            mockEmbedding[i] = Math.random() * 2 - 1;
        }
        chunk.setEmbedding(mockEmbedding);
        
        return chunk;
    }
}