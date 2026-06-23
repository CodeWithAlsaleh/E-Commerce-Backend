package com.sivan.ecommerce.exception;

public class CartItemNotFoundException extends ResourceNotFoundException {

    public CartItemNotFoundException(String message) {
        super(message);
    }

    public CartItemNotFoundException(Throwable cause) {
        super(cause);
    }

    public CartItemNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
