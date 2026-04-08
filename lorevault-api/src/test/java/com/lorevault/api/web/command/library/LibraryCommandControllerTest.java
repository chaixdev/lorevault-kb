package com.lorevault.api.web.command.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.support.*;
import com.lorevault.api.library.LibraryService;
import com.lorevault.api.testutil.TestIds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LibraryCommandController.class)
@DisplayName("LibraryCommandController")
class LibraryCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LibraryService catalogService;

    @Test
    @DisplayName("POST /create-universe should create new universe successfully")
    void shouldCreateNewUniverseSuccessfully() throws Exception {
        CreateUniverseRequest request = new CreateUniverseRequest("Cosmere");
        CreateUniverseResponse response = CreateUniverseResponse.newlyCreated(
                TestIds.UNIVERSE_ID,
                "Cosmere",
                "cosmere",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(catalogService.createUniverse(any(CreateUniverseRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/command/library/create-universe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.universeId").value(TestIds.UNIVERSE_ID.toString()))
                .andExpect(jsonPath("$.name").value("Cosmere"))
                .andExpect(jsonPath("$.slug").value("cosmere"))
                .andExpect(jsonPath("$.created").value(true));
    }

    @Test
    @DisplayName("POST /create-universe should return existing universe")
    void shouldReturnExistingUniverse() throws Exception {
        CreateUniverseRequest request = new CreateUniverseRequest("Cosmere");
        CreateUniverseResponse response = CreateUniverseResponse.existing(
                TestIds.UNIVERSE_ID,
                "Cosmere",
                "cosmere",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(catalogService.createUniverse(any(CreateUniverseRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/command/library/create-universe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.universeId").value(TestIds.UNIVERSE_ID.toString()))
                .andExpect(jsonPath("$.created").value(false));
    }

    @Test
    @DisplayName("POST /create-universe should return 400 for invalid request")
    void shouldReturn400ForInvalidUniverseRequest() throws Exception {
        CreateUniverseRequest request = new CreateUniverseRequest("   ");
        when(catalogService.createUniverse(any(CreateUniverseRequest.class)))
                .thenThrow(new IllegalArgumentException("Universe name cannot be null or blank"));

        mockMvc.perform(post("/api/command/library/create-universe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /create-universe should return 400 for missing name")
    void shouldReturn400ForMissingUniverseName() throws Exception {
        CreateUniverseRequest request = new CreateUniverseRequest();

        mockMvc.perform(post("/api/command/library/create-universe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /create-series should create new series successfully")
    void shouldCreateNewSeriesSuccessfully() throws Exception {
        CreateSeriesRequest request = new CreateSeriesRequest(TestIds.UNIVERSE_ID, "Stormlight Archive");
        CreateSeriesResponse response = CreateSeriesResponse.newlyCreated(
                TestIds.SERIES_ID,
                TestIds.UNIVERSE_ID,
                "Cosmere",
                "Stormlight Archive",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(catalogService.createSeries(any(CreateSeriesRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/command/library/create-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seriesId").value(TestIds.SERIES_ID.toString()))
                .andExpect(jsonPath("$.universeId").value(TestIds.UNIVERSE_ID.toString()))
                .andExpect(jsonPath("$.name").value("Stormlight Archive"))
                .andExpect(jsonPath("$.created").value(true));
    }

    @Test
    @DisplayName("POST /create-series should return 400 when universe not found")
    void shouldReturn400WhenUniverseNotFoundForSeries() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        CreateSeriesRequest request = new CreateSeriesRequest(nonExistentId, "Some Series");

        when(catalogService.createSeries(any(CreateSeriesRequest.class)))
                .thenThrow(new IllegalArgumentException("Universe not found: " + nonExistentId));

        mockMvc.perform(post("/api/command/library/create-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /create-book should create standalone book successfully")
    void shouldCreateStandaloneBookSuccessfully() throws Exception {
        CreateBookRequest request = CreateBookRequest.standalone(TestIds.UNIVERSE_ID, "Warbreaker");
        CreateBookResponse response = CreateBookResponse.newlyCreated(
                TestIds.BOOK_ID,
                TestIds.UNIVERSE_ID,
                "Cosmere",
                null,
                null,
                "Warbreaker",
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(catalogService.createBook(any(CreateBookRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/command/library/create-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(TestIds.BOOK_ID.toString()))
                .andExpect(jsonPath("$.universeId").value(TestIds.UNIVERSE_ID.toString()))
                .andExpect(jsonPath("$.seriesId").isEmpty())
                .andExpect(jsonPath("$.title").value("Warbreaker"))
                .andExpect(jsonPath("$.created").value(true));
    }

    @Test
    @DisplayName("POST /create-book should create series book successfully")
    void shouldCreateSeriesBookSuccessfully() throws Exception {
        CreateBookRequest request = CreateBookRequest.inSeries(TestIds.UNIVERSE_ID, TestIds.SERIES_ID, "The Way of Kings", 1);
        CreateBookResponse response = CreateBookResponse.newlyCreated(
                TestIds.BOOK_ID,
                TestIds.UNIVERSE_ID,
                "Cosmere",
                TestIds.SERIES_ID,
                "Stormlight Archive",
                "The Way of Kings",
                1,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(catalogService.createBook(any(CreateBookRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/command/library/create-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(TestIds.BOOK_ID.toString()))
                .andExpect(jsonPath("$.seriesId").value(TestIds.SERIES_ID.toString()))
                .andExpect(jsonPath("$.title").value("The Way of Kings"))
                .andExpect(jsonPath("$.bookNumber").value(1));
    }

    @Test
    @DisplayName("POST /create-book should return 400 when universe not found")
    void shouldReturn400WhenUniverseNotFoundForBook() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        CreateBookRequest request = CreateBookRequest.standalone(nonExistentId, "Some Book");

        when(catalogService.createBook(any(CreateBookRequest.class)))
                .thenThrow(new IllegalArgumentException("Universe not found: " + nonExistentId));

        mockMvc.perform(post("/api/command/library/create-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
