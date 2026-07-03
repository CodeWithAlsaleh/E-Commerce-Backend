package com.sivan.ecommerce.exception;

public class CategoryAlreadyExistsException extends ResourceConflictException {

    public CategoryAlreadyExistsException(String message) {
        super(message);
    }

    public CategoryAlreadyExistsException(Throwable cause) {
        super(cause);
    }

    public CategoryAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
