package com.sivan.ecommerce.exception;

public class OrderStateConflictException extends ResourceConflictException {

    public OrderStateConflictException(String message) {
        super(message);
    }

    public OrderStateConflictException(Throwable cause) {
        super(cause);
    }

    public OrderStateConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
