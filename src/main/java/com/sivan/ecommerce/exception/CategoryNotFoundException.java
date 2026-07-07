package com.sivan.ecommerce.exception;

public class CategoryNotFoundException extends ResourceNotFoundException {

    public CategoryNotFoundException(String message) {
        super(message);
    }

    public CategoryNotFoundException(Throwable cause) {
        super(cause);
    }

    public CategoryNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
