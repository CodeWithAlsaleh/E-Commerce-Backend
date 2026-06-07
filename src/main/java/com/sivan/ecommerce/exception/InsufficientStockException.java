package com.sivan.ecommerce.exception;

public class InsufficientStockException extends ResourceConflictException {

    public InsufficientStockException(String message) {
        super(message);
    }

    public InsufficientStockException(Throwable cause) {
        super(cause);
    }

    public InsufficientStockException(String message, Throwable cause) {
        super(message, cause);
    }
}
