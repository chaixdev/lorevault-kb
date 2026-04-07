package com.lorevault.api.integration.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.LoreVaultApiApplication;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jContentPersistenceAdapter;
import com.lorevault.api.domain.ingestion.IngestionStatus;
import com.lorevault.api.dto.ingestion.JobStatusResponse;
import com.lorevault.api.event.ChapterIngestionEvent;
import com.lorevault.api.testing.TestImages;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for event publishing behavior after service consolidation.
 * Validates that async event processing and publishing works identically to pre-refactor state.
 * 
 * Tests:
 * - ChapterIngestionEvent publishing during ingestion workflow
 * - Event timing and payload validation 
 * - Async processing event behavior
 * - Event listener integration
 */
@SpringBootTest(
    classes = LoreVaultApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureWebMvc
@Tag("integration")
@Tag("events")
class EventPublishingIntegrationTest {

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
    private Neo4jContentPersistenceAdapter contentPersistencePort;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Autowired
    private TestEventListener testEventListener;

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
            This is a test chapter for event publishing validation.
            It contains sufficient content to trigger the complete ingestion pipeline.
            The content includes multiple scenes and should generate embedding events.
            """;
        
        // Reset test event listener
        testEventListener.reset();
    }

    @Test
    @DisplayName("ChapterIngestionEvent: Published During Complete Workflow")
    void chapterIngestionEventPublishing() throws Exception {
        // Submit chapter for ingestion
        MockMultipartFile testFile = new MockMultipartFile(
            "file", "event-test-chapter.txt", "text/plain", testChapterContent.getBytes()
        );

        MvcResult result = mockMvc.perform(
            multipart("/api/command/ingest")
                .file(testFile)
                .param("bookId", testBookId.toString())
                .param("chapterNumber", "1")
                .param("chapterTitle", "Event Test Chapter")
        )
        .andExpect(status().isAccepted())
        .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        String jobId = (String) response.get("jobId");

        // Wait for job completion
        await().atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                MvcResult statusResult = mockMvc.perform(get("/api/query/jobs/{jobId}", jobId))
                    .andExpect(status().isOk()).andReturn();
                
                JobStatusResponse status = objectMapper.readValue(
                    statusResult.getResponse().getContentAsString(), JobStatusResponse.class);
                
                assertThat(status.getCurrentStatus()).isEqualTo(IngestionStatus.COMPLETE);
            });

        // Wait for event publishing to complete
        boolean eventReceived = testEventListener.awaitChapterIngestionEvent(5, TimeUnit.SECONDS);
        
        assertThat(eventReceived).isTrue();

        // Validate event details
        ChapterIngestionEvent event = testEventListener.getLastChapterIngestionEvent();
        
        assertAll("ChapterIngestionEvent validation",
            () -> assertThat(event).isNotNull(),
            () -> assertThat(event.getChapterId()).isNotNull(),
            () -> assertThat(event.getJobId()).isEqualTo(UUID.fromString(jobId))
        );
    }

    @Test
    @DisplayName("Event Timing: Events Published at Correct Workflow Stages")
    void eventTimingValidation() throws Exception {
        MockMultipartFile testFile = new MockMultipartFile(
            "file", "timing-test.txt", "text/plain", testChapterContent.getBytes()
        );

        long submissionTime = System.currentTimeMillis();

        MvcResult result = mockMvc.perform(
            multipart("/api/command/ingest")
                .file(testFile)
                .param("bookId", testBookId.toString())
                .param("chapterNumber", "1")
                .param("chapterTitle", "Timing Test Chapter")
        )
        .andExpect(status().isAccepted())
        .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        String jobId = (String) response.get("jobId");

        // Wait for completion and event
        await().atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                MvcResult statusResult = mockMvc.perform(get("/api/query/jobs/{jobId}", jobId))
                    .andExpect(status().isOk()).andReturn();
                
                JobStatusResponse status = objectMapper.readValue(
                    statusResult.getResponse().getContentAsString(), JobStatusResponse.class);
                
                assertThat(status.getCurrentStatus()).isEqualTo(IngestionStatus.COMPLETE);
            });

        long completionTime = System.currentTimeMillis();

        // Validate that event was published during the workflow
        boolean eventReceived = testEventListener.awaitChapterIngestionEvent(5, TimeUnit.SECONDS);
        assertThat(eventReceived).isTrue();

        ChapterIngestionEvent event = testEventListener.getLastChapterIngestionEvent();
        long eventTime = event.getTimestamp();  // Event timestamp as long

        // Event timestamp should be between submission and completion
        assertThat(eventTime).isGreaterThanOrEqualTo(submissionTime);
        assertThat(eventTime).isLessThanOrEqualTo(completionTime);
    }

    @Test
    @DisplayName("Multiple Events: Concurrent Job Event Publishing")
    void concurrentEventPublishing() throws Exception {
        int numberOfJobs = 3;
        
        // Submit multiple jobs concurrently
        for (int i = 1; i <= numberOfJobs; i++) {
            String content = testChapterContent + " Chapter " + i + " unique content.";
            MockMultipartFile testFile = new MockMultipartFile(
                "file", "concurrent-" + i + ".txt", "text/plain", content.getBytes()
            );

            mockMvc.perform(
                multipart("/api/command/ingest")
                    .file(testFile)
                    .param("bookId", testBookId.toString())
                    .param("chapterNumber", String.valueOf(i))
                    .param("chapterTitle", "Concurrent Chapter " + i)
            )
            .andExpect(status().isAccepted());
        }

        // Wait for all events to be published
        boolean allEventsReceived = testEventListener.awaitMultipleChapterIngestionEvents(
            numberOfJobs, 60, TimeUnit.SECONDS);
        
        assertThat(allEventsReceived).isTrue();

        // Validate that we received the expected number of events
        int eventCount = testEventListener.getChapterIngestionEventCount();
        assertThat(eventCount).isEqualTo(numberOfJobs);

        // Validate that all events have unique chapter IDs and job IDs
        var events = testEventListener.getAllChapterIngestionEvents();
        var chapterIds = events.stream().map(ChapterIngestionEvent::getChapterId).toList();
        var jobIds = events.stream().map(ChapterIngestionEvent::getJobId).toList();
        
        assertThat(chapterIds).hasSize(numberOfJobs).doesNotHaveDuplicates();
        assertThat(jobIds).hasSize(numberOfJobs).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Event Payload: Complete and Accurate Event Data")
    void eventPayloadValidation() throws Exception {
        MockMultipartFile testFile = new MockMultipartFile(
            "file", "payload-test.txt", "text/plain", testChapterContent.getBytes()
        );

        MvcResult result = mockMvc.perform(
            multipart("/api/command/ingest")
                .file(testFile)
                .param("bookId", testBookId.toString())
                .param("chapterNumber", "5")
                .param("chapterTitle", "Payload Validation Chapter")
        )
        .andExpect(status().isAccepted())
        .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        String jobId = (String) response.get("jobId");

        // Wait for completion and event
        await().atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                MvcResult statusResult = mockMvc.perform(get("/api/query/jobs/{jobId}", jobId))
                    .andExpect(status().isOk()).andReturn();
                
                JobStatusResponse status = objectMapper.readValue(
                    statusResult.getResponse().getContentAsString(), JobStatusResponse.class);
                
                assertThat(status.getCurrentStatus()).isEqualTo(IngestionStatus.COMPLETE);
            });

        boolean eventReceived = testEventListener.awaitChapterIngestionEvent(5, TimeUnit.SECONDS);
        assertThat(eventReceived).isTrue();

        ChapterIngestionEvent event = testEventListener.getLastChapterIngestionEvent();

        // Get actual chapter from database for comparison
        JobStatusResponse finalStatus = objectMapper.readValue(
            mockMvc.perform(get("/api/query/jobs/{jobId}", jobId))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(), 
            JobStatusResponse.class);

        UUID actualChapterId = finalStatus.getChapterId();
        var chapter = contentPersistencePort.findChapterById(actualChapterId);
        
        assertThat(chapter).isPresent();

        // Validate event payload matches actual database state
        assertAll("Event payload accuracy",
            () -> assertThat(event.getChapterId()).isEqualTo(actualChapterId),
            () -> assertThat(event.getJobId()).isEqualTo(UUID.fromString(jobId))
        );
    }

    @Test
    @DisplayName("Event Resilience: Event Publishing Under Error Conditions")
    void eventPublishingResilience() throws Exception {
        // Test with invalid data that might cause processing issues
        String problematicContent = ""; // Empty content might cause issues
        
        MockMultipartFile testFile = new MockMultipartFile(
            "file", "resilience-test.txt", "text/plain", problematicContent.getBytes()
        );

        // This should fail validation before any events are published
        mockMvc.perform(
            multipart("/api/command/ingest")
                .file(testFile)
                .param("bookId", testBookId.toString())
                .param("chapterNumber", "1")
                .param("chapterTitle", "Resilience Test")
        )
        .andExpect(status().isBadRequest());

        // Wait a bit to ensure no events were published
        Thread.sleep(2000);

        // Verify no events were published for failed submission
        int eventCount = testEventListener.getChapterIngestionEventCount();
        assertThat(eventCount).isEqualTo(0);
    }

    /**
     * Test event listener component to capture and validate published events
     */
    @Component
    static class TestEventListener {
        
        private final CountDownLatch chapterIngestionLatch = new CountDownLatch(1);
        private CountDownLatch multipleEventsLatch;
        private final AtomicReference<ChapterIngestionEvent> lastChapterIngestionEvent = new AtomicReference<>();
        private final java.util.List<ChapterIngestionEvent> allChapterIngestionEvents = 
            new java.util.concurrent.CopyOnWriteArrayList<>();

        @EventListener
        public void handleChapterIngestionEvent(ChapterIngestionEvent event) {
            lastChapterIngestionEvent.set(event);
            allChapterIngestionEvents.add(event);
            chapterIngestionLatch.countDown();
            
            if (multipleEventsLatch != null) {
                multipleEventsLatch.countDown();
            }
        }

        public boolean awaitChapterIngestionEvent(long timeout, TimeUnit unit) throws InterruptedException {
            return chapterIngestionLatch.await(timeout, unit);
        }

        public boolean awaitMultipleChapterIngestionEvents(int expectedCount, long timeout, TimeUnit unit) 
                throws InterruptedException {
            multipleEventsLatch = new CountDownLatch(expectedCount);
            return multipleEventsLatch.await(timeout, unit);
        }

        public ChapterIngestionEvent getLastChapterIngestionEvent() {
            return lastChapterIngestionEvent.get();
        }

        public java.util.List<ChapterIngestionEvent> getAllChapterIngestionEvents() {
            return new java.util.ArrayList<>(allChapterIngestionEvents);
        }

        public int getChapterIngestionEventCount() {
            return allChapterIngestionEvents.size();
        }

        public void reset() {
            lastChapterIngestionEvent.set(null);
            allChapterIngestionEvents.clear();
            // Note: Cannot reset CountDownLatch, but tests should use separate instances
        }
    }
}