package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.domain.shared.PublicationCoordinates;
import com.lorevault.api.dto.ingestion.SubmitChapterRequest;
import com.lorevault.api.dto.ingestion.SubmitChapterResponse;
import com.lorevault.api.service.ingestion.IngestionService;
import com.lorevault.api.web.command.ingestion.builder.CoordinatesBuilder;
import com.lorevault.api.web.command.ingestion.extractor.FileContentExtractor;
import com.lorevault.api.web.command.ingestion.response.ErrorResponseFactory;
import com.lorevault.api.web.command.ingestion.validation.FileUploadValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = CommandIngestionController.class)
class CommandIngestionControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    IngestionService ingestionService;

    @MockBean
    FileUploadValidator fileUploadValidator;

    @MockBean
    CoordinatesBuilder coordinatesBuilder;

    @MockBean
    ErrorResponseFactory errorResponseFactory;

    @MockBean
    FileContentExtractor fileContentExtractor;

    @AfterEach
    void tearDown() {
        reset(ingestionService, fileUploadValidator, coordinatesBuilder, errorResponseFactory, fileContentExtractor);
    }

    @Test
    void submitFile_success_returns202Accepted() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "chapter.txt", "text/plain", "Some valid content".getBytes());

        when(fileUploadValidator.validateFile(any())).thenReturn(FileUploadValidator.ValidationResult.success());
        when(coordinatesBuilder.validateCoordinates(anyString(), any(), any(), any(), any())).thenReturn(CoordinatesBuilder.CoordinateValidationResult.success());

        FileContentExtractor.ContentExtractionResult extractionResult = FileContentExtractor.ContentExtractionResult.success("Some valid content", "chapter.txt");
        when(fileContentExtractor.extractFileContent(any())).thenReturn(extractionResult);

        when(coordinatesBuilder.determineFinalTitle(any(), any())).thenReturn("My Chapter Title");
        when(coordinatesBuilder.validateTitleLength(anyString())).thenReturn(CoordinatesBuilder.CoordinateValidationResult.success());

        PublicationCoordinates coords = new PublicationCoordinates();
        when(coordinatesBuilder.buildCoordinates(anyString(), any(), any(), any())).thenReturn(coords);

        SubmitChapterRequest builtRequest = new SubmitChapterRequest();
        when(coordinatesBuilder.buildSubmitRequest(eq(coords), eq("My Chapter Title"), eq("Some valid content"))).thenReturn(builtRequest);

        UUID jobId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        when(ingestionService.submitChapter(builtRequest)).thenReturn(SubmitChapterResponse.success(jobId, chapterId));

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Chapter ingestion started successfully");
        body.put("jobId", jobId.toString());
        body.put("status", "ACCEPTED");
        when(errorResponseFactory.createIngestionSuccessResponse(eq(jobId.toString()))).thenReturn(ResponseEntity.accepted().body(body));

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/command/ingest")
                        .file(file)
                        .param("universe", "Middle Earth")
                        .param("series", "LOTR")
                        .param("bookNumber", "1")
                        .param("chapterNumber", "1")
                        .param("title", "My Chapter Title")
        )
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void submitFile_missingFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "chapter.txt", "text/plain", new byte[0]);

        when(errorResponseFactory.createMissingFileError()).thenReturn(ResponseEntity.badRequest().body(null));

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/command/ingest")
                        .file(emptyFile)
                        .param("universe", "Middle Earth")
                        .param("bookNumber", "1")
                        .param("chapterNumber", "1")
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void submitFile_invalidFileType_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "chapter.pdf", "application/pdf", "content".getBytes());

        FileUploadValidator.ValidationResult typeFailure = FileUploadValidator.ValidationResult.failure(
                "INVALID_FILE_TYPE",
                "Only .txt and .md files are supported",
                new FileUploadValidator.FileTypeError(java.util.List.of(".txt", ".md"), ".pdf")
        );
        when(fileUploadValidator.validateFile(any())).thenReturn(typeFailure);
        when(errorResponseFactory.createFileValidationError(eq(typeFailure)))
                .thenReturn(ResponseEntity.badRequest().body(null));

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/command/ingest")
                        .file(file)
                        .param("universe", "Middle Earth")
                        .param("bookNumber", "1")
                        .param("chapterNumber", "1")
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void submitFile_coordinateValidationError_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "chapter.txt", "text/plain", "content".getBytes());

        when(fileUploadValidator.validateFile(any())).thenReturn(FileUploadValidator.ValidationResult.success());

        CoordinatesBuilder.CoordinateValidationResult coordFailure = CoordinatesBuilder.CoordinateValidationResult.failure(
                "MISSING_UNIVERSE", "Universe parameter is required");
        when(coordinatesBuilder.validateCoordinates(anyString(), any(), any(), any(), any())).thenReturn(coordFailure);
        when(errorResponseFactory.createCoordinateValidationError(eq(coordFailure)))
                .thenReturn(ResponseEntity.badRequest().body(null));

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/command/ingest")
                        .file(file)
                        .param("universe", " ")
                        .param("bookNumber", "1")
                        .param("chapterNumber", "1")
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void submitFile_contentExtractionFails_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "chapter.txt", "text/plain", "content".getBytes());

        when(fileUploadValidator.validateFile(any())).thenReturn(FileUploadValidator.ValidationResult.success());
        when(coordinatesBuilder.validateCoordinates(anyString(), any(), any(), any(), any())).thenReturn(CoordinatesBuilder.CoordinateValidationResult.success());

        IOException io = new IOException("read error");
        FileContentExtractor.ContentExtractionResult failure = FileContentExtractor.ContentExtractionResult.failure("chapter.txt", "Failed", io);
        when(fileContentExtractor.extractFileContent(any())).thenReturn(failure);
        when(errorResponseFactory.createFileReadingError(eq("chapter.txt"), eq(io)))
                .thenReturn(ResponseEntity.badRequest().body(null));

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/command/ingest")
                        .file(file)
                        .param("universe", "Middle Earth")
                        .param("bookNumber", "1")
                        .param("chapterNumber", "1")
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void submitFile_titleTooLong_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "chapter.txt", "text/plain", "content".getBytes());

        when(fileUploadValidator.validateFile(any())).thenReturn(FileUploadValidator.ValidationResult.success());
        when(coordinatesBuilder.validateCoordinates(anyString(), any(), any(), any(), any())).thenReturn(CoordinatesBuilder.CoordinateValidationResult.success());

        FileContentExtractor.ContentExtractionResult extractionResult = FileContentExtractor.ContentExtractionResult.success("Some valid content", "chapter.txt");
        when(fileContentExtractor.extractFileContent(any())).thenReturn(extractionResult);

        String longTitle = "x".repeat(501);
        when(coordinatesBuilder.determineFinalTitle(any(), any())).thenReturn(longTitle);

        CoordinatesBuilder.CoordinateValidationResult titleFailure = CoordinatesBuilder.CoordinateValidationResult.failure(
                "TITLE_TOO_LONG", "Title exceeds maximum length of 500 characters");
        when(coordinatesBuilder.validateTitleLength(eq(longTitle))).thenReturn(titleFailure);
        when(errorResponseFactory.createCoordinateValidationError(eq(titleFailure)))
                .thenReturn(ResponseEntity.badRequest().body(null));

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/command/ingest")
                        .file(file)
                        .param("universe", "Middle Earth")
                        .param("bookNumber", "1")
                        .param("chapterNumber", "1")
                        .param("title", longTitle)
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void submitFile_ingestionServiceThrows_returns500() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "chapter.txt", "text/plain", "Some valid content".getBytes());

        when(fileUploadValidator.validateFile(any())).thenReturn(FileUploadValidator.ValidationResult.success());
        when(coordinatesBuilder.validateCoordinates(anyString(), any(), any(), any(), any())).thenReturn(CoordinatesBuilder.CoordinateValidationResult.success());

        FileContentExtractor.ContentExtractionResult extractionResult = FileContentExtractor.ContentExtractionResult.success("Some valid content", "chapter.txt");
        when(fileContentExtractor.extractFileContent(any())).thenReturn(extractionResult);

        when(coordinatesBuilder.determineFinalTitle(any(), any())).thenReturn("Title");
        when(coordinatesBuilder.validateTitleLength(anyString())).thenReturn(CoordinatesBuilder.CoordinateValidationResult.success());

        PublicationCoordinates coords = new PublicationCoordinates();
        when(coordinatesBuilder.buildCoordinates(anyString(), any(), any(), any())).thenReturn(coords);
        SubmitChapterRequest builtRequest = new SubmitChapterRequest();
        when(coordinatesBuilder.buildSubmitRequest(eq(coords), eq("Title"), eq("Some valid content"))).thenReturn(builtRequest);

        RuntimeException boom = new RuntimeException("boom");
        when(ingestionService.submitChapter(eq(builtRequest))).thenThrow(boom);
        when(errorResponseFactory.createIngestionServiceError(eq(boom))).thenReturn(ResponseEntity.internalServerError().body(null));

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/command/ingest")
                        .file(file)
                        .param("universe", "Middle Earth")
                        .param("series", "LOTR")
                        .param("bookNumber", "1")
                        .param("chapterNumber", "1")
        )
                .andExpect(MockMvcResultMatchers.status().isInternalServerError());
    }
}
