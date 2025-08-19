package com.lorevault.api.web.command.ingestion.validation;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * Service responsible for validating file uploads.
 * Handles file type, size, and content validation.
 * Extracted from ContentIngestionController to improve single responsibility and testability.
 */
@Component
public class FileUploadValidator {

    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList(".txt", ".md");
    private static final long MAX_FILE_SIZE_BYTES = 1048576; // 1MB
    
    /**
     * Validation result context object
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorCode;
        private final String errorMessage;
        private final Object errorDetails;

        private ValidationResult(boolean valid, String errorCode, String errorMessage, Object errorDetails) {
            this.valid = valid;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.errorDetails = errorDetails;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null, null, null);
        }

        public static ValidationResult failure(String errorCode, String errorMessage) {
            return new ValidationResult(false, errorCode, errorMessage, null);
        }

        public static ValidationResult failure(String errorCode, String errorMessage, Object details) {
            return new ValidationResult(false, errorCode, errorMessage, details);
        }

        public boolean isValid() { return valid; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public Object getErrorDetails() { return errorDetails; }
    }

    /**
     * Validate file upload including type, size, and content checks
     */
    public ValidationResult validateFile(MultipartFile file) {
        // Check if file exists
        if (file == null || file.isEmpty()) {
            return ValidationResult.failure("EMPTY_FILE", "File contains no content");
        }

        // Validate file type
        ValidationResult typeResult = validateFileType(file);
        if (!typeResult.isValid()) {
            return typeResult;
        }

        // Validate file size
        ValidationResult sizeResult = validateFileSize(file);
        if (!sizeResult.isValid()) {
            return sizeResult;
        }

        return ValidationResult.success();
    }

    /**
     * Validate file type against supported extensions
     */
    public ValidationResult validateFileType(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return ValidationResult.failure("INVALID_FILENAME", "File must have a valid filename");
        }
        
        String extension = getFileExtension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            return ValidationResult.failure(
                "INVALID_FILE_TYPE", 
                "Only .txt and .md files are supported",
                new FileTypeError(SUPPORTED_EXTENSIONS, extension)
            );
        }

        return ValidationResult.success();
    }

    /**
     * Validate file size against maximum limit
     */
    public ValidationResult validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return ValidationResult.failure(
                "FILE_TOO_LARGE",
                "File exceeds maximum size limit of 1MB",
                new FileSizeError(file.getSize(), MAX_FILE_SIZE_BYTES)
            );
        }

        return ValidationResult.success();
    }

    /**
     * Extract file extension (including the dot)
     */
    public String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    // Error detail classes for structured error information
    public static class FileTypeError {
        private final List<String> supportedTypes;
        private final String receivedType;

        public FileTypeError(List<String> supportedTypes, String receivedType) {
            this.supportedTypes = supportedTypes;
            this.receivedType = receivedType;
        }

        public List<String> getSupportedTypes() { return supportedTypes; }
        public String getReceivedType() { return receivedType; }
    }

    public static class FileSizeError {
        private final long fileSize;
        private final long maxSize;

        public FileSizeError(long fileSize, long maxSize) {
            this.fileSize = fileSize;
            this.maxSize = maxSize;
        }

        public long getFileSize() { return fileSize; }
        public long getMaxSize() { return maxSize; }
    }
}
