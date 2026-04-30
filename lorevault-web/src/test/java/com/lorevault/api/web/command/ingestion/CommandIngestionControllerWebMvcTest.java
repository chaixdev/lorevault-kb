package com.lorevault.api.web.command.ingestion;

import com.lorevault.api.ingestion.submission.IngestionService;
import com.lorevault.api.ingestion.submission.IngestionSubmissionResult;
import com.lorevault.api.web.command.ingestion.builder.CoordinatesBuilder;
import com.lorevault.api.web.command.ingestion.extractor.FileContentExtractor;
import com.lorevault.api.web.command.ingestion.response.ErrorResponseFactory;
import com.lorevault.api.web.command.ingestion.validation.FileUploadValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(controllers = CommandIngestionController.class)
class CommandIngestionControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    IngestionService ingestionService;

    @MockitoBean
    FileUploadValidator fileUploadValidator;

    @MockitoBean
    CoordinatesBuilder coordinatesBuilder;

    @MockitoBean
    ErrorResponseFactory errorResponseFactory;

    @MockitoBean
    FileContentExtractor fileContentExtractor;

    @AfterEach
    void tearDown() {
        reset(ingestionService, fileUploadValidator, coordinatesBuilder, errorResponseFactory, fileContentExtractor);
    }

    @Test
        void submitFile_success_returns202Accepted() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "chapter.txt", "text/plain", "Some valid content".getBytes());

        when(fileUploadValidator.validateFile(any())).thenReturn(FileUploadValidator.ValidationResult.success());
                when(coordinatesBuilder.validateChapterNumber(any())).thenReturn(CoordinatesBuilder.CoordinateValidationResult.success());

        FileContentExtractor.ContentExtractionResult extractionResult = FileContentExtractor.ContentExtractionResult.success("Some valid content", "chapter.txt");
        when(fileContentExtractor.extractFileContent(any())).thenReturn(extractionResult);

        when(coordinatesBuilder.determineFinalTitle(any(), any())).thenReturn("My Chapter Title");
        when(coordinatesBuilder.validateTitleLength(anyString())).thenReturn(CoordinatesBuilder.CoordinateValidationResult.success());

        UUID bookId = UUID.randomUUID();
        Integer chapterNumber = 1;
        String chapterTitle = "My Chapter Title";
        String fileContent = "Some valid content";

        SubmitChapterRequest builtRequest = mock(SubmitChapterRequest.class);
        when(builtRequest.getBookId()).thenReturn(bookId);
        when(builtRequest.getChapterNumber()).thenReturn(chapterNumber);
        when(builtRequest.getChapterTitle()).thenReturn(chapterTitle);
        when(builtRequest.getChapterText()).thenReturn(fileContent);

        when(coordinatesBuilder.buildSubmitRequest(any(), any(), any(), any())).thenReturn(builtRequest);

        when(ingestionService.submitChapter(any(UUID.class), anyInt(), anyString(), anyString()))
                .thenReturn(new IngestionSubmissionResult(UUID.randomUUID(), UUID.randomUUID()));

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Chapter ingestion started successfully");
        body.put("jobId", "test-job-id");
        body.put("status", "ACCEPTED");
        when(errorResponseFactory.createIngestionSuccessResponse(anyString())).thenReturn(ResponseEntity.accepted().body(body));

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/command/ingest")
                        .file(file)
                        .param("bookId", bookId.toString())
                        .param("chapterNumber", chapterNumber.toString())
                        .param("chapterTitle", chapterTitle)
        )
                .andExpect(MockMvcResultMatchers.status().isAccepted())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void submitFile_missingFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "chapter.txt", "text/plain", new byte[0]);

        when(errorResponseFactory.createMissingFileError()).thenReturn(ResponseEntity.badRequest().body(null));

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/command/ingest")
                        .file(emptyFile)
                        .param("bookId", UUID.randomUUID().toString())
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
                        .param("bookId", UUID.randomUUID().toString())
                        .param("chapterNumber", "1")
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
        void submitFile_coordinateValidationError_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "chapter.txt", "text/plain", "content".getBytes());

        when(fileUploadValidator.validateFile(any())).thenReturn(FileUploadValidator.ValidationResult.success());

                CoordinatesBuilder.CoordinateValidationResult numFailure = CoordinatesBuilder.CoordinateValidationResult.failure(
                                "INVALID_CHAPTER_NUMBER", "Chapter number must be a positive integer");
                when(coordinatesBuilder.validateChapterNumber(any())).thenReturn(numFailure);
                when(errorResponseFactory.createCoordinateValidationError(eq(numFailure)))
                .thenReturn(ResponseEntity.badRequest().body(null));

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/command/ingest")
                        .file(file)
                                                .param("bookId", UUID.randomUUID().toString())
                                                .param("chapterNumber", "0")
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void submitFile_contentExtractionFails_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "chapter.txt", "text/plain", "content".getBytes());

        when(fileUploadValidator.validateFile(any())).thenReturn(FileUploadValidator.ValidationResult.success());
        when(coordinatesBuilder.validateChapterNumber(any())).thenReturn(CoordinatesBuilder.CoordinateValidationResult.success());

        IOException io = new IOException("read error");
        FileContentExtractor.ContentExtractionResult failure = FileContentExtractor.ContentExtractionResult.failure("chapter.txt", "Failed", io);
        when(fileContentExtractor.extractFileContent(any())).thenReturn(failure);
        when(errorResponseFactory.createFileReadingError(eq("chapter.txt"), eq(io)))
                .thenReturn(ResponseEntity.badRequest().body(null));

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/command/ingest")
                        .file(file)
                        .param("bookId", UUID.randomUUID().toString())
                        .param("chapterNumber", "1")
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void submitFile_titleTooLong_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "chapter.txt", "text/plain", "content".getBytes());

        when(fileUploadValidator.validateFile(any())).thenReturn(FileUploadValidator.ValidationResult.success());
        when(coordinatesBuilder.validateChapterNumber(any())).thenReturn(CoordinatesBuilder.CoordinateValidationResult.success());

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
                        .param("bookId", UUID.randomUUID().toString())
                        .param("chapterNumber", "1")
                        .param("chapterTitle", longTitle)
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void submitFile_ingestionServiceThrows_returns500() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "chapter.txt", "text/plain", "Some valid content".getBytes());

        when(fileUploadValidator.validateFile(any())).thenReturn(FileUploadValidator.ValidationResult.success());
        when(coordinatesBuilder.validateChapterNumber(any())).thenReturn(CoordinatesBuilder.CoordinateValidationResult.success());

        FileContentExtractor.ContentExtractionResult extractionResult = FileContentExtractor.ContentExtractionResult.success("Some valid content", "chapter.txt");
        when(fileContentExtractor.extractFileContent(any())).thenReturn(extractionResult);

        when(coordinatesBuilder.determineFinalTitle(any(), any())).thenReturn("Title");
        when(coordinatesBuilder.validateTitleLength(anyString())).thenReturn(CoordinatesBuilder.CoordinateValidationResult.success());

        SubmitChapterRequest builtRequest = mock(SubmitChapterRequest.class);
        when(builtRequest.getBookId()).thenReturn(UUID.randomUUID());
        when(builtRequest.getChapterNumber()).thenReturn(1);
        when(builtRequest.getChapterTitle()).thenReturn("Title");
        when(builtRequest.getChapterText()).thenReturn("Some valid content");
        when(coordinatesBuilder.buildSubmitRequest(any(), any(), any(), any())).thenReturn(builtRequest);

        RuntimeException boom = new RuntimeException("boom");
        when(ingestionService.submitChapter(any(UUID.class), anyInt(), anyString(), anyString())).thenThrow(boom);
        when(errorResponseFactory.createIngestionServiceError(eq(boom))).thenReturn(ResponseEntity.internalServerError().body(null));

        mockMvc.perform(
                MockMvcRequestBuilders.multipart("/api/command/ingest")
                        .file(file)
                        .param("bookId", UUID.randomUUID().toString())
                        .param("chapterNumber", "1")
        )
                .andExpect(MockMvcResultMatchers.status().isInternalServerError());
    }
}
