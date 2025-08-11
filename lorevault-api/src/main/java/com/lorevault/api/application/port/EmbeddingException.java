package com.lorevault.api.application.port;

/**
 * Exception thrown when embedding operations fail.
 */
public class EmbeddingException extends RuntimeException {
    
    public EmbeddingException(String message) {
        super(message);
    }
    
    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
