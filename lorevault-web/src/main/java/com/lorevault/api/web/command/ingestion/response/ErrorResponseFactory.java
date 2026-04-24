package com.lorevault.api.web.command.ingestion.response;

import com.lorevault.api.web.ErrorResponse;
import com.lorevault.api.web.command.ingestion.builder.CoordinatesBuilder;
import com.lorevault.api.web.command.ingestion.validation.FileUploadValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for creating standardized error responses.
 * Handles error formatting, status code determination, and response structure.
 * Extracted from ContentIngestionController to centralize error handling.
 */
@Component
public class ErrorResponseFactory {

    /**
     * Create error response for file validation failures
     */
    public ResponseEntity<ErrorResponse> createFileValidationError(FileUploadValidator.ValidationResult result) {
        Map<String, Object> details = new HashMap<>();
        
        // Add error details if present
        if (result.getErrorDetails() != null) {
            if (result.getErrorDetails() instanceof FileUploadValidator.FileTypeError error) {
                details.put("fileType", Map.of(
                    "received", error.receivedType(),
                    "supported", error.supportedTypes()
                ));
            } else if (result.getErrorDetails() instanceof FileUploadValidator.FileSizeError error) {
                details.put("fileSize", Map.of(
                    "actual", error.fileSize(),
                    "maximum", error.maxSize()
                ));
            }
        }

        ErrorResponse error = ErrorResponse.builder()
            .code(result.getErrorCode())
            .message(result.getErrorMessage())
            .details("httpStatus", HttpStatus.BAD_REQUEST.value())
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Create error response for coordinate validation failures
     */
    public ResponseEntity<ErrorResponse> createCoordinateValidationError(CoordinatesBuilder.CoordinateValidationResult result) {
        ErrorResponse error = ErrorResponse.builder()
            .code(result.getErrorCode())
            .message(result.getErrorMessage())
            .details("httpStatus", HttpStatus.BAD_REQUEST.value())
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Create error response for missing file
     */
    public ResponseEntity<ErrorResponse> createMissingFileError() {
        ErrorResponse error = ErrorResponse.builder()
            .code("MISSING_FILE")
            .message("No file provided in request")
            .details("httpStatus", HttpStatus.BAD_REQUEST.value())
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Create error response for file reading errors
     */
    public ResponseEntity<ErrorResponse> createFileReadingError(String filename, Exception cause) {
        ErrorResponse error = ErrorResponse.builder()
            .code("FILE_READING_ERROR")
            .message("Failed to read file content")
            .details("filename", filename)
            .details("cause", cause.getClass().getSimpleName())
            .details("httpStatus", HttpStatus.BAD_REQUEST.value())
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Create error response for ingestion service failures
     */
    public ResponseEntity<ErrorResponse> createIngestionServiceError(Exception cause) {
        ErrorResponse error = ErrorResponse.builder()
            .code("INGESTION_SERVICE_ERROR")
            .message("Chapter ingestion failed due to internal error")
            .details("cause", cause.getClass().getSimpleName())
            .details("message", cause.getMessage())
            .details("httpStatus", HttpStatus.INTERNAL_SERVER_ERROR.value())
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.internalServerError().body(error);
    }

    /**
     * Create success response for successful ingestion
     */
    public ResponseEntity<Map<String, Object>> createIngestionSuccessResponse(String jobId) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Chapter ingestion started successfully");
        response.put("jobId", jobId);
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", "ACCEPTED");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Create error response for unexpected exceptions
     */
    public ResponseEntity<ErrorResponse> createUnexpectedError(Exception cause) {
        ErrorResponse error = ErrorResponse.builder()
            .code("UNEXPECTED_ERROR")
            .message("An unexpected error occurred")
            .details("cause", cause.getClass().getSimpleName())
            .details("httpStatus", HttpStatus.INTERNAL_SERVER_ERROR.value())
            .timestamp(LocalDateTime.now())
            .build();

        return ResponseEntity.internalServerError().body(error);
    }
}
