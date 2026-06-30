package com.sivan.ecommerce.advice;

import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.exception.ResourceConflictException;
import com.sivan.ecommerce.exception.ResourceNotFoundException;
import com.sivan.ecommerce.response.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleException(MethodArgumentTypeMismatchException exception) {
        return buildError(HttpStatus.BAD_REQUEST, "Invalid URL parameter: '" + exception.getValue() + "' is not a valid format");
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ErrorResponse> handleException(MissingPathVariableException exception) {
        return buildError(HttpStatus.BAD_REQUEST, "The required 'path variable' is missing from the URL path");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleException(NoResourceFoundException exception) {
        return buildError(HttpStatus.NOT_FOUND, "The requested API endpoint does not exist. Please verify the URL path and HTTP method.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleException(MissingRequestHeaderException exception) {
        return buildError(HttpStatus.BAD_REQUEST, "Required header is missing: " + exception.getHeaderName());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleException(ConstraintViolationException exception) {

        // Extract and join all validation messages separated by a comma
        String errorMessage = exception.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));

        return buildError(HttpStatus.BAD_REQUEST, errorMessage);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleException(HttpRequestMethodNotSupportedException exception) {
        String errorMessage = "The %s method is not supported for this endpoint"
                .formatted(exception.getMethod());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(Objects.requireNonNull(exception.getSupportedHttpMethods()).toArray(new HttpMethod[0]))
                .body(new ErrorResponse(
                        HttpStatus.METHOD_NOT_ALLOWED.value(),
                        errorMessage,
                        System.currentTimeMillis()
                ));
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

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ErrorResponse> handleException(ResourceConflictException exception) {
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
