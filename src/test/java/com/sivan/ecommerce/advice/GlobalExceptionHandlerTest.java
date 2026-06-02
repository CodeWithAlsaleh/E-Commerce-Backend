package com.sivan.ecommerce.advice;

import com.sivan.ecommerce.exception.CustomerAlreadyExistsException;
import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.*;

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
                            org.mockito.Mockito.mock(org.springframework.http.HttpInputMessage.class));

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(400, response.getBody().getStatus());
            assertEquals("Invalid input format", response.getBody().getMessage());
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
    }

    // ==================== CustomerAlreadyExistsException (409) ====================

    @Nested
    @DisplayName("CustomerAlreadyExistsException → 409")
    class CustomerAlreadyExistsExceptionHandler {

        @Test
        @DisplayName("Should return 409 with the exception's message")
        void shouldReturn409_withExceptionMessage() {
            // Arrange
            CustomerAlreadyExistsException exception =
                    new CustomerAlreadyExistsException("Email account already exists");

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
            CustomerAlreadyExistsException exception =
                    new CustomerAlreadyExistsException("Email account already exists");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleException(exception);
            long afterTime = System.currentTimeMillis();

            // Assert
            assertNotNull(response.getBody());
            assertTrue(response.getBody().getTimeStamp() >= beforeTime);
            assertTrue(response.getBody().getTimeStamp() <= afterTime);
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
            assertNotNull(response500.getBody());
            assertTrue(response500.getBody().getStatus() > 0);
            assertNotNull(response500.getBody().getMessage());
            assertTrue(response500.getBody().getTimeStamp() > 0);

            // 400 — InvalidData
            ResponseEntity<ErrorResponse> response400 =
                    handler.handleException(new InvalidDataException("bad data"));
            assertNotNull(response400.getBody());
            assertTrue(response400.getBody().getStatus() > 0);
            assertNotNull(response400.getBody().getMessage());
            assertTrue(response400.getBody().getTimeStamp() > 0);

            // 409 — CustomerAlreadyExists
            ResponseEntity<ErrorResponse> response409 =
                    handler.handleException(new CustomerAlreadyExistsException("duplicate"));
            assertNotNull(response409.getBody());
            assertTrue(response409.getBody().getStatus() > 0);
            assertNotNull(response409.getBody().getMessage());
            assertTrue(response409.getBody().getTimeStamp() > 0);
        }
    }
}
