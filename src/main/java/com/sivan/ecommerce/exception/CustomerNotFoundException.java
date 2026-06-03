package com.sivan.ecommerce.exception;

public class CustomerNotFoundException extends ResourceNotFoundException {

    public CustomerNotFoundException(String message) {
        super(message);
    }

    public CustomerNotFoundException(Throwable cause) {
        super(cause);
    }

    public CustomerNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
