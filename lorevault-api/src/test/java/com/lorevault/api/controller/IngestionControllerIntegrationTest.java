package com.lorevault.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.dto.SubmitChapterRequest;
import com.lorevault.api.dto.SubmitChapterResponse;
import com.lorevault.api.dto.JobStatusResponse;
import com.lorevault.api.model.PublicationCoordinates;
import com.lorevault.api.test.IntegrationTestBase;
import com.lorevault.api.testutil.SampleChapterLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for IngestionController using Testcontainers PostgreSQL.
 * Tests the complete request flow including database interactions.
 */
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IngestionControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitChapter_ShouldCreateChapterAndReturnJobId() throws Exception {
        // Given
        PublicationCoordinates coordinates = new PublicationCoordinates("Middle Earth", "The Fellowship of the Ring", 1, null, 1);
        SubmitChapterRequest request = new SubmitChapterRequest();
        request.setCoordinates(coordinates);
        request.setChapterTitle("A Long Expected Party");
        request.setChapterText("When Mr. Bilbo Baggins of Bag End announced that he would shortly be celebrating his eleventy-first birthday...");

        // When
        MvcResult result = mockMvc.perform(post("/api/ingestion/chapters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();

        // Then
        String responseBody = result.getResponse().getContentAsString();
        SubmitChapterResponse response = objectMapper.readValue(responseBody, SubmitChapterResponse.class);
        
        assertThat(response.getJobId()).isNotNull();
        assertThat(response.getChapterId()).isNotNull();
        assertThat(response.getMessage()).contains("submitted successfully");
    }

    @Test
    void submitChapter_WithDuplicateContent_ShouldHandleGracefully() throws Exception {
        // Given - Submit the same chapter twice
        PublicationCoordinates coordinates = new PublicationCoordinates("Test Universe", "Test Series", 1, null, 1);
        SubmitChapterRequest request = new SubmitChapterRequest();
        request.setCoordinates(coordinates);
        request.setChapterTitle("Test Chapter");
        request.setChapterText("This is a test chapter with unique content for duplication test.");

        // When - Submit first time
        MvcResult firstResult = mockMvc.perform(post("/api/ingestion/chapters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();

        // When - Submit second time (duplicate)
        MvcResult secondResult = mockMvc.perform(post("/api/ingestion/chapters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();

        // Then
        SubmitChapterResponse firstResponse = objectMapper.readValue(firstResult.getResponse().getContentAsString(), SubmitChapterResponse.class);
        SubmitChapterResponse secondResponse = objectMapper.readValue(secondResult.getResponse().getContentAsString(), SubmitChapterResponse.class);
        
        // Should have same chapter ID but different job IDs
        assertThat(firstResponse.getChapterId()).isEqualTo(secondResponse.getChapterId());
        assertThat(firstResponse.getJobId()).isNotEqualTo(secondResponse.getJobId());
    }

    @Test
    void getJobStatus_ShouldReturnJobInformation() throws Exception {
        // Given - First submit a chapter to get a job ID
        PublicationCoordinates coordinates = new PublicationCoordinates("Test Universe", "Test Series", 1, null, 2);
        SubmitChapterRequest request = new SubmitChapterRequest();
        request.setCoordinates(coordinates);
        request.setChapterTitle("Another Test Chapter");
        request.setChapterText("Content for job status testing.");

        MvcResult submitResult = mockMvc.perform(post("/api/ingestion/chapters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();

        SubmitChapterResponse submitResponse = objectMapper.readValue(submitResult.getResponse().getContentAsString(), SubmitChapterResponse.class);

        // When - Check job status
        MvcResult statusResult = mockMvc.perform(get("/api/ingestion/jobs/{jobId}/status", submitResponse.getJobId()))
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String statusResponseBody = statusResult.getResponse().getContentAsString();
        JobStatusResponse statusResponse = objectMapper.readValue(statusResponseBody, JobStatusResponse.class);
        
        assertThat(statusResponse.getJobId()).isEqualTo(submitResponse.getJobId());
        assertThat(statusResponse.getChapterId()).isEqualTo(submitResponse.getChapterId());
        assertThat(statusResponse.getCurrentStatus()).isNotNull();
        assertThat(statusResponse.getProgressPercent()).isNotNull();
        assertThat(statusResponse.getIsComplete()).isNotNull();
    }

    @Test
    void getJobStatus_WithNonExistentJob_ShouldReturn404() throws Exception {
        // Given
        UUID nonExistentJobId = UUID.randomUUID();

        // When & Then
        mockMvc.perform(get("/api/ingestion/jobs/{jobId}/status", nonExistentJobId))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitChapter_WithInvalidRequest_ShouldReturn400() throws Exception {
        // Given - Request with missing required fields
        SubmitChapterRequest invalidRequest = new SubmitChapterRequest();
        invalidRequest.setChapterTitle(""); // Empty title
        // Missing coordinates and chapterText

        // When & Then
        mockMvc.perform(post("/api/ingestion/chapters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthCheck_ShouldReturnHealthy() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/ingestion/health"))
                .andExpect(status().isOk());
    }

    @Test
    void submitSampleChapter_KevinJenkins_ShouldProcessSuccessfully() throws Exception {
        // Given
        SubmitChapterRequest sampleChapter = SampleChapterLoader.loadSampleChapter("kevin_jenkins");
        
        // When
        MvcResult result = mockMvc.perform(post("/api/ingestion/chapters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleChapter)))
                .andExpect(status().isAccepted())
                .andReturn();

        // Then
        String responseBody = result.getResponse().getContentAsString();
        SubmitChapterResponse response = objectMapper.readValue(responseBody, SubmitChapterResponse.class);
        
        assertThat(response.getJobId()).isNotNull();
        assertThat(response.getChapterId()).isNotNull();
        assertThat(response.getMessage()).contains("submitted successfully");
        
        // Verify job status shows completion
        MvcResult statusResult = mockMvc.perform(get("/api/ingestion/jobs/{jobId}/status", response.getJobId()))
                .andExpect(status().isOk())
                .andReturn();
                
        JobStatusResponse statusResponse = objectMapper.readValue(statusResult.getResponse().getContentAsString(), JobStatusResponse.class);
        assertThat(statusResponse.getIsComplete()).isTrue();
        assertThat(statusResponse.getProgressPercent()).isEqualTo(100);
    }

    @Test
    void submitMultipleSampleChapters_ShouldHandleDeathworldersSeries() throws Exception {
        // Given
        List<SubmitChapterRequest> sampleChapters = SampleChapterLoader.loadAllSampleChapters();
        
        // When & Then - Submit all sample chapters
        for (SubmitChapterRequest chapter : sampleChapters) {
            MvcResult result = mockMvc.perform(post("/api/ingestion/chapters")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(chapter)))
                    .andExpect(status().isAccepted())
                    .andReturn();

            SubmitChapterResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), SubmitChapterResponse.class);
            
            // Verify each submission
            assertThat(response.getJobId()).isNotNull();
            assertThat(response.getChapterId()).isNotNull();
            assertThat(response.getMessage()).contains("submitted successfully");
            
            // Verify chapter title matches
            assertThat(chapter.getChapterTitle()).isNotBlank();
            assertThat(chapter.getChapterText()).hasSizeGreaterThan(100); // Realistic content size
        }
    }

    @Test
    void submitLargeChapter_ShouldHandleRealisticContent() throws Exception {
        // Given - Use the largest sample chapter (Kevin Jenkins Experience)
        SubmitChapterRequest largeChapter = SampleChapterLoader.loadSampleChapter("kevin_jenkins");
        
        // Verify it's actually a substantial chapter
        assertThat(largeChapter.getChapterText()).hasSizeGreaterThan(5000);
        
        // When
        MvcResult result = mockMvc.perform(post("/api/ingestion/chapters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(largeChapter)))
                .andExpect(status().isAccepted())
                .andReturn();

        // Then
        SubmitChapterResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), SubmitChapterResponse.class);
        
        assertThat(response.getJobId()).isNotNull();
        assertThat(response.getChapterId()).isNotNull();
        
        // Verify job completes even with large content
        MvcResult statusResult = mockMvc.perform(get("/api/ingestion/jobs/{jobId}/status", response.getJobId()))
                .andExpect(status().isOk())
                .andReturn();
                
        JobStatusResponse statusResponse = objectMapper.readValue(statusResult.getResponse().getContentAsString(), JobStatusResponse.class);
        assertThat(statusResponse.getIsComplete()).isTrue();
    }

    @Test
    void submitSampleChapter_DuplicateDetection_ShouldWork() throws Exception {
        // Given
        SubmitChapterRequest aftermath = SampleChapterLoader.loadSampleChapter("aftermath");
        
        // When - Submit the same chapter twice
        MvcResult firstResult = mockMvc.perform(post("/api/ingestion/chapters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aftermath)))
                .andExpect(status().isAccepted())
                .andReturn();

        MvcResult secondResult = mockMvc.perform(post("/api/ingestion/chapters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aftermath)))
                .andExpect(status().isAccepted())
                .andReturn();

        // Then
        SubmitChapterResponse firstResponse = objectMapper.readValue(firstResult.getResponse().getContentAsString(), SubmitChapterResponse.class);
        SubmitChapterResponse secondResponse = objectMapper.readValue(secondResult.getResponse().getContentAsString(), SubmitChapterResponse.class);
        
        // Should have same chapter ID (content deduplication) but different job IDs
        assertThat(firstResponse.getChapterId()).isEqualTo(secondResponse.getChapterId());
        assertThat(firstResponse.getJobId()).isNotEqualTo(secondResponse.getJobId());
    }
}
