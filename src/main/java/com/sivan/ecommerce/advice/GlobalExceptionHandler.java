package com.sivan.ecommerce.advice;

import com.sivan.ecommerce.exception.CustomerAlreadyExistsException;
import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.exception.ResourceNotFoundException;
import com.sivan.ecommerce.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleException(MethodArgumentNotValidException exception) {

        String errorMessage = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> {
                    // If the error is a type mismatch (like String to Enum)
                    if (error.isBindingFailure())
                        return error.getField() + ": Invalid value format";

                    // Otherwise, it's a standard validation error (like @Size)
                    return error.getField() + ": " + error.getDefaultMessage();
                }).collect(Collectors.joining(", "));

        return buildError(HttpStatus.BAD_REQUEST, errorMessage);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleException(HttpMessageNotReadableException exception) {
        return buildError(HttpStatus.BAD_REQUEST, "Invalid input format");
    }

    // ==================== Custom exceptions ====================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleException(ResourceNotFoundException exception) {
        return buildError(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidDataException.class)
    public ResponseEntity<ErrorResponse> handleException(InvalidDataException exception) {
        return buildError(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(CustomerAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleException(CustomerAlreadyExistsException exception) {
        return buildError(HttpStatus.CONFLICT, exception.getMessage());
    }

    private ResponseEntity<ErrorResponse> buildError(HttpStatus httpStatus, String message) {
        ErrorResponse error = new ErrorResponse();

        error.setStatus(httpStatus.value());
        error.setMessage(message);
        error.setTimeStamp(System.currentTimeMillis());

        return new ResponseEntity<>(error, httpStatus);
    }
}
