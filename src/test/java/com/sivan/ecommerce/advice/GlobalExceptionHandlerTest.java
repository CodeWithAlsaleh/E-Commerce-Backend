package com.sivan.ecommerce.advice;

import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.exception.ResourceConflictException;
import com.sivan.ecommerce.exception.ResourceNotFoundException;
import com.sivan.ecommerce.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 * Tests each @ExceptionHandler method in isolation — no Spring context needed.
 * Verifies HTTP status codes, error messages, and response structure.
 */
@DisplayName("GlobalExceptionHandler — exception handling")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ==================== Exception.class (500) ====================

    @Nested
    @DisplayName("Generic Exception → 500")
    class GenericExceptionHandler {

        @Test
        @DisplayName("Should return 500 with generic message for unexpected Exception")
        void shouldReturn500_withGenericMessage() {
            // Arrange
            Exception exception = new Exception("Something unexpected happened");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(500, response.getBody().getStatus());
            assertEquals("An unexpected error occurred.", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should return 500 with generic message for RuntimeException")
        void shouldReturn500_forRuntimeException() {
            // Arrange
            Exception exception = new RuntimeException("Database crashed");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertNotNull(response.getBody());
            assertEquals(500, response.getBody().getStatus());
            assertEquals("An unexpected error occurred.", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should include timeStamp in error response")
        void shouldIncludeTimeStamp() {
            // Arrange
            long beforeTime = System.currentTimeMillis();
            Exception exception = new Exception("test");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);
            long afterTime = System.currentTimeMillis();

            // Assert
            assertNotNull(response.getBody());
            assertTrue(response.getBody().getTimeStamp() >= beforeTime);
            assertTrue(response.getBody().getTimeStamp() <= afterTime);
        }

        @Test
        @DisplayName("Should never expose the original exception message to the caller")
        void shouldNeverExposeOriginalExceptionMessage() {
            // Arrange
            Exception exception = new Exception("SQL syntax error near 'DROP TABLE users'");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertNotNull(response.getBody());
            assertFalse(response.getBody().getMessage().contains("SQL"));
            assertFalse(response.getBody().getMessage().contains("DROP TABLE"));
            assertEquals("An unexpected error occurred.", response.getBody().getMessage());
        }
    }

    // ==================== MethodArgumentNotValidException (400) ====================

    @Nested
    @DisplayName("MethodArgumentNotValidException → 400")
    class ValidationExceptionHandler {

        @Test
        @DisplayName("Should return 400 with field error message for single validation error")
        void shouldReturn400_withSingleFieldError() {
            // Arrange
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(
                    new Object(), "customerRequestDTO"
            );
            bindingResult.addError(new FieldError(
                    "customerRequestDTO", "email", null,
                    false, null, null, "must not be blank"
            ));
            MethodArgumentNotValidException exception =
                    new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(400, response.getBody().getStatus());
            assertEquals("email: must not be blank", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should join multiple field errors with comma separator")
        void shouldJoinMultipleFieldErrors_withComma() {
            // Arrange
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(
                    new Object(), "customerRequestDTO"
            );
            bindingResult.addError(new FieldError(
                    "customerRequestDTO", "firstName", null,
                    false, null, null, "must not be blank"
            ));
            bindingResult.addError(new FieldError(
                    "customerRequestDTO", "email", null,
                    false, null, null, "must be a valid email"
            ));
            MethodArgumentNotValidException exception =
                    new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertNotNull(response.getBody());
            String message = response.getBody().getMessage();
            assertTrue(message.contains("firstName: must not be blank"));
            assertTrue(message.contains("email: must be a valid email"));
            assertTrue(message.contains(", "), "Errors should be comma-separated");
        }

        @Test
        @DisplayName("Should return 'Invalid value format' for binding failures (type mismatch)")
        void shouldReturnInvalidValueFormat_forBindingFailure() {
            // Arrange
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(
                    new Object(), "productRequestDTO"
            );
            // A binding failure (e.g., String → Integer mismatch)
            bindingResult.addError(new FieldError(
                    "productRequestDTO", "quantity", "not-a-number",
                    true, null, null, "Failed to convert"
            ));
            MethodArgumentNotValidException exception =
                    new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertNotNull(response.getBody());
            assertEquals("quantity: Invalid value format", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should handle mix of binding failures and validation errors")
        void shouldHandleMixOfBindingAndValidationErrors() {
            // Arrange
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(
                    new Object(), "productRequestDTO"
            );
            // Binding failure
            bindingResult.addError(new FieldError(
                    "productRequestDTO", "category", "INVALID_ENUM",
                    true, null, null, "Failed to convert"
            ));
            // Standard validation error
            bindingResult.addError(new FieldError(
                    "productRequestDTO", "name", null,
                    false, null, null, "must not be blank"
            ));
            MethodArgumentNotValidException exception =
                    new MethodArgumentNotValidException(null, bindingResult);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertNotNull(response.getBody());
            String message = response.getBody().getMessage();
            assertTrue(message.contains("category: Invalid value format"));
            assertTrue(message.contains("name: must not be blank"));
        }
    }

    // ==================== HttpMessageNotReadableException (400) ====================

    @Nested
    @DisplayName("HttpMessageNotReadableException → 400")
    class HttpMessageNotReadableHandler {

        @Test
        @DisplayName("Should return 400 with 'Invalid input format' message")
        void shouldReturn400_withInvalidInputFormatMessage() {
            // Arrange
            HttpMessageNotReadableException exception =
                    new HttpMessageNotReadableException("Could not read JSON",
                            mock(HttpInputMessage.class));

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(400, response.getBody().getStatus());
            assertEquals("Invalid input format", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should not expose internal deserialization details")
        void shouldNotExposeInternalDetails() {
            // Arrange
            HttpMessageNotReadableException exception =
                    new HttpMessageNotReadableException(
                            "JSON parse error: Cannot deserialize value of type `java.lang.Integer` from String",
                            mock(HttpInputMessage.class));

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertNotNull(response.getBody());
            assertFalse(response.getBody().getMessage().contains("Cannot deserialize"));
            assertEquals("Invalid input format", response.getBody().getMessage());
        }
    }

    // ==================== MethodArgumentTypeMismatchException (400) ====================

    @Nested
    @DisplayName("MethodArgumentTypeMismatchException → 400")
    class MethodArgumentTypeMismatchHandler {

        @Test
        @DisplayName("Should return 400 with descriptive message including invalid value")
        void shouldReturn400_withInvalidValueInMessage() {
            // Arrange
            MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                    "abc", Long.class, "id", null, new NumberFormatException("For input string: \"abc\"")
            );

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(400, response.getBody().getStatus());
            assertEquals("Invalid URL parameter: 'abc' is not a valid format", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should include the rejected value in the error message")
        void shouldIncludeRejectedValue() {
            // Arrange
            MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                    "not-a-uuid", java.util.UUID.class, "productId", null,
                    new IllegalArgumentException("Invalid UUID string")
            );

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertNotNull(response.getBody());
            assertTrue(response.getBody().getMessage().contains("not-a-uuid"));
        }

        @Test
        @DisplayName("Should include timeStamp in 400 error response")
        void shouldIncludeTimeStamp_in400Response() {
            // Arrange
            long beforeTime = System.currentTimeMillis();
            MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                    "xyz", Integer.class, "page", null, new NumberFormatException()
            );

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);
            long afterTime = System.currentTimeMillis();

            // Assert
            assertNotNull(response.getBody());
            assertTrue(response.getBody().getTimeStamp() >= beforeTime);
            assertTrue(response.getBody().getTimeStamp() <= afterTime);
        }
    }

    // ==================== MissingPathVariableException (400) ====================

    @Nested
    @DisplayName("MissingPathVariableException → 400")
    class MissingPathVariableHandler {

        @Test
        @DisplayName("Should return 400 with descriptive missing path variable message")
        void shouldReturn400_withMissingPathVariableMessage() throws NoSuchMethodException {
            // Arrange
            MissingPathVariableException exception = new MissingPathVariableException(
                    "id",
                    new org.springframework.core.MethodParameter(
                            this.getClass().getDeclaredMethod("shouldReturn400_withMissingPathVariableMessage"), -1
                    )
            );

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(400, response.getBody().getStatus());
            assertEquals("The required 'path variable' is missing from the URL path",
                    response.getBody().getMessage());
        }
    }

    // ==================== NoResourceFoundException (404) ====================

    @Nested
    @DisplayName("NoResourceFoundException → 404")
    class NoResourceFoundHandler {

        @Test
        @DisplayName("Should return 404 with API endpoint not found message")
        void shouldReturn404_withEndpointNotFoundMessage() {
            // Arrange
            NoResourceFoundException exception =
                    new NoResourceFoundException(HttpMethod.GET, "/api/nonexistent", "No static resource api/nonexistent");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(404, response.getBody().getStatus());
            assertEquals("The requested API endpoint does not exist. Please verify the URL path and HTTP method.",
                    response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should return the same message regardless of HTTP method")
        void shouldReturnSameMessage_regardlessOfHttpMethod() {
            // Arrange
            NoResourceFoundException getException =
                    new NoResourceFoundException(HttpMethod.GET, "/api/unknown", "No static resource api/unknown");
            NoResourceFoundException postException =
                    new NoResourceFoundException(HttpMethod.POST, "/api/unknown", "No static resource api/unknown");

            // Act
            ResponseEntity<ErrorResponse> getResponse = handler.handleException(getException);
            ResponseEntity<ErrorResponse> postResponse = handler.handleException(postException);

            // Assert
            assertNotNull(getResponse.getBody());
            assertNotNull(postResponse.getBody());
            assertEquals(getResponse.getBody().getMessage(), postResponse.getBody().getMessage());
            assertEquals(404, getResponse.getBody().getStatus());
            assertEquals(404, postResponse.getBody().getStatus());
        }
    }

    // ==================== ResourceNotFoundException (404) ====================

    @Nested
    @DisplayName("ResourceNotFoundException → 404")
    class ResourceNotFoundExceptionHandler {

        @Test
        @DisplayName("Should return 404 with the exception's custom message")
        void shouldReturn404_withCustomMessage() {
            // Arrange
            ResourceNotFoundException exception =
                    new ResourceNotFoundException("Product not found with id: 99");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(404, response.getBody().getStatus());
            assertEquals("Product not found with id: 99", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should preserve the exact exception message in the response")
        void shouldPreserveExactExceptionMessage() {
            // Arrange
            String customMessage = "Customer with email test@example.com was not found";
            ResourceNotFoundException exception = new ResourceNotFoundException(customMessage);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertNotNull(response.getBody());
            assertEquals(customMessage, response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should include timeStamp in 404 error response")
        void shouldIncludeTimeStamp_in404Response() {
            // Arrange
            long beforeTime = System.currentTimeMillis();
            ResourceNotFoundException exception =
                    new ResourceNotFoundException("Not found");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);
            long afterTime = System.currentTimeMillis();

            // Assert
            assertNotNull(response.getBody());
            assertTrue(response.getBody().getTimeStamp() >= beforeTime);
            assertTrue(response.getBody().getTimeStamp() <= afterTime);
        }
    }

    // ==================== InvalidDataException (400) ====================

    @Nested
    @DisplayName("InvalidDataException → 400")
    class InvalidDataExceptionHandler {

        @Test
        @DisplayName("Should return 400 with the exception's custom message")
        void shouldReturn400_withCustomMessage() {
            // Arrange
            InvalidDataException exception = new InvalidDataException("Price must be positive");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(400, response.getBody().getStatus());
            assertEquals("Price must be positive", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should preserve the exact exception message in the response")
        void shouldPreserveExactExceptionMessage() {
            // Arrange
            String customMessage = "Quantity cannot be zero or negative";
            InvalidDataException exception = new InvalidDataException(customMessage);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertNotNull(response.getBody());
            assertEquals(customMessage, response.getBody().getMessage());
        }
    }

    // ==================== ResourceConflictException (409) ====================

    @Nested
    @DisplayName("ResourceConflictException → 409")
    class ResourceConflictExceptionHandler {

        @Test
        @DisplayName("Should return 409 with the exception's message")
        void shouldReturn409_withExceptionMessage() {
            // Arrange
            ResourceConflictException exception =
                    new ResourceConflictException("Email account already exists");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(409, response.getBody().getStatus());
            assertEquals("Email account already exists", response.getBody().getMessage());
        }

        @Test
        @DisplayName("Should include timeStamp in 409 error response")
        void shouldIncludeTimeStamp_in409Response() {
            // Arrange
            long beforeTime = System.currentTimeMillis();
            ResourceConflictException exception =
                    new ResourceConflictException("Duplicate resource");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);
            long afterTime = System.currentTimeMillis();

            // Assert
            assertNotNull(response.getBody());
            assertTrue(response.getBody().getTimeStamp() >= beforeTime);
            assertTrue(response.getBody().getTimeStamp() <= afterTime);
        }

        @Test
        @DisplayName("Should preserve the exact exception message in the response")
        void shouldPreserveExactExceptionMessage() {
            // Arrange
            String customMessage = "Product with SKU 'ABC-123' already exists";
            ResourceConflictException exception = new ResourceConflictException(customMessage);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertNotNull(response.getBody());
            assertEquals(customMessage, response.getBody().getMessage());
        }
    }

    // ==================== Error Response Structure ====================

    @Nested
    @DisplayName("ErrorResponse structure validation")
    class ErrorResponseStructure {

        @Test
        @DisplayName("All handlers should return ErrorResponse with status, message, and timeStamp")
        void allHandlersShouldReturnConsistentStructure() {
            // Test each handler produces a valid ErrorResponse

            // 500
            ResponseEntity<ErrorResponse> response500 = handler.handleException(new Exception("test"));
            assertValidErrorResponse(response500, 500);

            // 400 — InvalidData
            ResponseEntity<ErrorResponse> response400 =
                    handler.handleException(new InvalidDataException("bad data"));
            assertValidErrorResponse(response400, 400);

            // 404 — ResourceNotFound
            ResponseEntity<ErrorResponse> response404 =
                    handler.handleException(new ResourceNotFoundException("not found"));
            assertValidErrorResponse(response404, 404);

            // 409 — ResourceConflict
            ResponseEntity<ErrorResponse> response409 =
                    handler.handleException(new ResourceConflictException("duplicate"));
            assertValidErrorResponse(response409, 409);
        }

        @Test
        @DisplayName("HTTP status in ResponseEntity should match status field in ErrorResponse body")
        void httpStatusShouldMatchBodyStatus() {
            // 500
            ResponseEntity<ErrorResponse> response500 = handler.handleException(new Exception("err"));
            assertNotNull(response500.getBody());
            assertEquals(response500.getStatusCode().value(), response500.getBody().getStatus());

            // 400
            ResponseEntity<ErrorResponse> response400 =
                    handler.handleException(new InvalidDataException("err"));
            assertNotNull(response400.getBody());
            assertEquals(response400.getStatusCode().value(), response400.getBody().getStatus());

            // 404
            ResponseEntity<ErrorResponse> response404 =
                    handler.handleException(new ResourceNotFoundException("err"));
            assertNotNull(response404.getBody());
            assertEquals(response404.getStatusCode().value(), response404.getBody().getStatus());

            // 409
            ResponseEntity<ErrorResponse> response409 =
                    handler.handleException(new ResourceConflictException("err"));
            assertNotNull(response409.getBody());
            assertEquals(response409.getStatusCode().value(), response409.getBody().getStatus());
        }

        private void assertValidErrorResponse(ResponseEntity<ErrorResponse> response, int expectedStatus) {
            assertNotNull(response.getBody());
            assertEquals(expectedStatus, response.getBody().getStatus());
            assertNotNull(response.getBody().getMessage());
            assertFalse(response.getBody().getMessage().isBlank(), "Message should not be blank");
            assertTrue(response.getBody().getTimeStamp() > 0, "TimeStamp should be set");
        }
    }
}
