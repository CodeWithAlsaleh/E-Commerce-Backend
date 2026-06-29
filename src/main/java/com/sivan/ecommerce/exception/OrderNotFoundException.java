package com.sivan.ecommerce.exception;

public class OrderNotFoundException extends ResourceConflictException {

    public OrderNotFoundException(String message) {
        super(message);
    }

    public OrderNotFoundException(Throwable cause) {
        super(cause);
    }

    public OrderNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
