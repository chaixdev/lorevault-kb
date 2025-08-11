package com.lorevault.api.application.port;

/**
 * Exception thrown when scene detection operations fail.
 */
public class SceneDetectionException extends RuntimeException {
    
    public SceneDetectionException(String message) {
        super(message);
    }
    
    public SceneDetectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
