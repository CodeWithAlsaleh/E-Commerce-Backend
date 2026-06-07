package com.sivan.ecommerce.exception;

public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }

    public ResourceConflictException(Throwable cause) {
        super(cause);
    }

    public ResourceConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
