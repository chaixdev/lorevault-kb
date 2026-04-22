package com.lorevault.api.web.command.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lorevault.api.library.domain.Universe;
import com.lorevault.api.library.domain.Series;
import com.lorevault.api.library.domain.Book;
import com.lorevault.api.library.application.LibraryResult;
import com.lorevault.api.library.application.LibraryService;
import com.lorevault.api.testutil.TestIds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @MockitoBean
    private LibraryService catalogService;

    @Test
    @DisplayName("POST /create-universe should create new universe successfully")
    void shouldCreateNewUniverseSuccessfully() throws Exception {
        CreateUniverseRequest request = new CreateUniverseRequest("Cosmere");
        Universe universe = new Universe(TestIds.UNIVERSE_ID, "Cosmere", "cosmere", LocalDateTime.now(), LocalDateTime.now());
        LibraryResult<Universe> result = new LibraryResult<>(universe, true);

        when(catalogService.createUniverse(eq("Cosmere"))).thenReturn(result);

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
        Universe universe = new Universe(TestIds.UNIVERSE_ID, "Cosmere", "cosmere", LocalDateTime.now(), LocalDateTime.now());
        LibraryResult<Universe> result = new LibraryResult<>(universe, false);

        when(catalogService.createUniverse(eq("Cosmere"))).thenReturn(result);

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
        when(catalogService.createUniverse(eq("   ")))
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
        Series series = new Series(TestIds.SERIES_ID, TestIds.UNIVERSE_ID, "Cosmere", "Stormlight Archive", LocalDateTime.now(), LocalDateTime.now());
        LibraryResult<Series> result = new LibraryResult<>(series, true);

        when(catalogService.createSeries(eq(TestIds.UNIVERSE_ID), eq("Stormlight Archive"))).thenReturn(result);

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

        when(catalogService.createSeries(eq(nonExistentId), eq("Some Series")))
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
        Book book = new Book(TestIds.BOOK_ID, TestIds.UNIVERSE_ID, null, "Cosmere", null, null, "Warbreaker", LocalDateTime.now(), LocalDateTime.now());
        LibraryResult<Book> result = new LibraryResult<>(book, true);

        when(catalogService.createBook(eq(TestIds.UNIVERSE_ID), org.mockito.ArgumentMatchers.<UUID>isNull(), eq("Warbreaker"), org.mockito.ArgumentMatchers.<Integer>isNull())).thenReturn(result);

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
        Book book = new Book(TestIds.BOOK_ID, TestIds.UNIVERSE_ID, TestIds.SERIES_ID, "Cosmere", "Stormlight Archive", 1, "The Way of Kings", LocalDateTime.now(), LocalDateTime.now());
        LibraryResult<Book> result = new LibraryResult<>(book, true);

        when(catalogService.createBook(eq(TestIds.UNIVERSE_ID), eq(TestIds.SERIES_ID), eq("The Way of Kings"), eq(1))).thenReturn(result);

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

        when(catalogService.createBook(eq(nonExistentId), eq(null), eq("Some Book"), eq(null)))
                .thenThrow(new IllegalArgumentException("Universe not found: " + nonExistentId));

        mockMvc.perform(post("/api/command/library/create-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
