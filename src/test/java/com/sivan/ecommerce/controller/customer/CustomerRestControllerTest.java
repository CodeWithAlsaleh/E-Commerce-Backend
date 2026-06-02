package com.sivan.ecommerce.controller.customer;

import tools.jackson.databind.ObjectMapper;
import com.sivan.ecommerce.config.SecurityConfig;
import com.sivan.ecommerce.dto.customer.CustomerRequestDTO;
import com.sivan.ecommerce.dto.customer.CustomerResponseDTO;
import com.sivan.ecommerce.exception.CustomerAlreadyExistsException;
import com.sivan.ecommerce.service.customer.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests for {@link CustomerRestController}.
 *
 * <p>Uses {@code @WebMvcTest} to load only the web layer (controller + security + validation).
 * The {@link CustomerService} is mocked — no database or full Spring context involved.</p>
 *
 * <p>{@code POST /customers} is {@code permitAll()} in SecurityConfig (registration endpoint),
 * so no authentication/authorization tests are needed. Tests focus on validation, conflict
 * handling, service exceptions, malformed input, and edge cases.</p>
 */
@WebMvcTest(CustomerRestController.class)
@Import(SecurityConfig.class)
@DisplayName("CustomerRestController")
class CustomerRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CustomerService customerService;

    // ======================== Constants ========================

    private static final String CUSTOMERS_URL = "/customers";

    private static final String VALID_FIRST_NAME = "John";
    private static final String VALID_LAST_NAME = "Doe";
    private static final String VALID_EMAIL = "john.doe@example.com";
    private static final String VALID_LOCATION = "New York";
    private static final String VALID_PASSWORD = "SecurePass123!";

    // ==================== createCustomer() ====================
    @Nested
    @DisplayName("createCustomer()")
    class CreateCustomer {
        // ======================== Helpers ========================

        private CustomerRequestDTO validRequest() {
            return new CustomerRequestDTO(
                    VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                    VALID_LOCATION, VALID_PASSWORD
            );
        }

        private CustomerResponseDTO validResponse(UUID id) {
            return new CustomerResponseDTO(
                    id, VALID_FIRST_NAME, VALID_LAST_NAME,
                    VALID_EMAIL, VALID_LOCATION
            );
        }

        // ==================== SUCCESS CASES (201) ====================

        @Nested
        @DisplayName("Success cases — 201 Created")
        class SuccessCases {

            @Test
            @DisplayName("Should return 201 and correct JSON when registering a valid customer")
            void shouldReturn201_whenValidCustomerRegistration() throws Exception {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                when(customerService.createCustomer(any(CustomerRequestDTO.class)))
                        .thenReturn(validResponse(expectedId));

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").value(expectedId.toString()))
                        .andExpect(jsonPath("$.firstName").value(VALID_FIRST_NAME))
                        .andExpect(jsonPath("$.lastName").value(VALID_LAST_NAME))
                        .andExpect(jsonPath("$.email").value(VALID_EMAIL))
                        .andExpect(jsonPath("$.location").value(VALID_LOCATION));

                verify(customerService).createCustomer(any(CustomerRequestDTO.class));
            }

            @Test
            @DisplayName("Should return 201 when location is null (optional field)")
            void shouldReturn201_whenLocationIsNull() throws Exception {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        null, VALID_PASSWORD
                );
                CustomerResponseDTO response = new CustomerResponseDTO(
                        expectedId, VALID_FIRST_NAME, VALID_LAST_NAME,
                        VALID_EMAIL, null
                );
                when(customerService.createCustomer(any(CustomerRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").value(expectedId.toString()))
                        // This ensures the key EXISTS and is explicitly set to NULL
                        .andExpect(jsonPath("$.location").value(nullValue()));
            }

            @Test
            @DisplayName("Should not return password in the response body")
            void shouldNotReturnPasswordInResponse() throws Exception {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                when(customerService.createCustomer(any(CustomerRequestDTO.class)))
                        .thenReturn(validResponse(expectedId));

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.password").doesNotExist());
            }

            @Test
            @DisplayName("Should call customerService.createCustomer exactly once")
            void shouldCallServiceExactlyOnce() throws Exception {
                // Arrange
                when(customerService.createCustomer(any(CustomerRequestDTO.class)))
                        .thenReturn(validResponse(UUID.randomUUID()));

                // Act
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated());

                // Assert
                verify(customerService).createCustomer(any(CustomerRequestDTO.class));
                verifyNoMoreInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 201 without authentication (permitAll endpoint)")
            void shouldReturn201_withoutAuthentication() throws Exception {
                // Arrange — no @WithMockUser, proving permitAll() works
                UUID expectedId = UUID.randomUUID();
                when(customerService.createCustomer(any(CustomerRequestDTO.class)))
                        .thenReturn(validResponse(expectedId));

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated());
            }
        }

        // ==================== VALIDATION FAILURES (400) ====================

        @Nested
        @DisplayName("Validation failures — 400 Bad Request")
        class ValidationFailures {

            // ---------- firstName ----------

            @Test
            @DisplayName("Should return 400 when firstName is null")
            void shouldReturn400_whenFirstNameIsNull() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        null, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, VALID_PASSWORD
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when firstName is blank")
            void shouldReturn400_whenFirstNameIsBlank() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        "   ", VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, VALID_PASSWORD
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when firstName exceeds 100 characters")
            void shouldReturn400_whenFirstNameTooLong() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        "A".repeat(101), VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, VALID_PASSWORD
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            // ---------- lastName ----------

            @Test
            @DisplayName("Should return 400 when lastName is null")
            void shouldReturn400_whenLastNameIsNull() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, null, VALID_EMAIL,
                        VALID_LOCATION, VALID_PASSWORD
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when lastName is blank")
            void shouldReturn400_whenLastNameIsBlank() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, "   ", VALID_EMAIL,
                        VALID_LOCATION, VALID_PASSWORD
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when lastName exceeds 100 characters")
            void shouldReturn400_whenLastNameTooLong() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, "B".repeat(101), VALID_EMAIL,
                        VALID_LOCATION, VALID_PASSWORD
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            // ---------- email ----------

            @Test
            @DisplayName("Should return 400 when email is null")
            void shouldReturn400_whenEmailIsNull() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, null,
                        VALID_LOCATION, VALID_PASSWORD
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when email is blank")
            void shouldReturn400_whenEmailIsBlank() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, "   ",
                        VALID_LOCATION, VALID_PASSWORD
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when email has invalid format")
            void shouldReturn400_whenEmailIsInvalid() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, "not-an-email",
                        VALID_LOCATION, VALID_PASSWORD
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when email exceeds 255 characters")
            void shouldReturn400_whenEmailTooLong() throws Exception {
                // Build a valid-looking email that exceeds 255 chars
                String longEmail = "a".repeat(244) + "@example.com";
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, longEmail,
                        VALID_LOCATION, VALID_PASSWORD
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            // ---------- location (optional, but has @Size if provided) ----------

            @Test
            @DisplayName("Should return 400 when location is provided but shorter than 2 characters")
            void shouldReturn400_whenLocationTooShort() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        "A", VALID_PASSWORD
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when location exceeds 255 characters")
            void shouldReturn400_whenLocationTooLong() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        "L".repeat(256), VALID_PASSWORD
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            // ---------- password ----------

            @Test
            @DisplayName("Should return 400 when password is null")
            void shouldReturn400_whenPasswordIsNull() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, null
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when password is blank")
            void shouldReturn400_whenPasswordIsBlank() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, "   "
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when password is shorter than 12 characters")
            void shouldReturn400_whenPasswordTooShort() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, "Short123!"
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when password exceeds 255 characters")
            void shouldReturn400_whenPasswordTooLong() throws Exception {
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, "P".repeat(256)
                );

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }
        }

        // ==================== CONFLICT CASES (409) ====================

        @Nested
        @DisplayName("Conflict cases — 409 Conflict")
        class ConflictCases {

            @Test
            @DisplayName("Should return 409 when email already exists")
            void shouldReturn409_whenEmailAlreadyExists() throws Exception {
                // Arrange
                when(customerService.createCustomer(any(CustomerRequestDTO.class)))
                        .thenThrow(new CustomerAlreadyExistsException("Email account already exists"));

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.status").value(409))
                        .andExpect(jsonPath("$.message").value("Email account already exists"));
            }
        }

        // ==================== SERVICE EXCEPTION HANDLING ====================

        @Nested
        @DisplayName("Service exception handling")
        class ServiceExceptionHandling {

            @Test
            @DisplayName("Should return 500 when service throws IllegalStateException (role not found)")
            void shouldReturn500_whenDefaultRoleNotFound() throws Exception {
                // Arrange — simulates the service throwing when ROLE_USER doesn't exist in DB
                when(customerService.createCustomer(any(CustomerRequestDTO.class)))
                        .thenThrow(new IllegalStateException("Default Role not found in database"));

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.status").value(500))
                        .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
            }

            @Test
            @DisplayName("Should return 500 when service throws an unexpected RuntimeException")
            void shouldReturn500_whenServiceThrowsRuntimeException() throws Exception {
                // Arrange
                when(customerService.createCustomer(any(CustomerRequestDTO.class)))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.status").value(500))
                        .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
            }
        }

        // ==================== MALFORMED INPUT ====================

        @Nested
        @DisplayName("Malformed input")
        class MalformedInput {

            @Test
            @DisplayName("Should return 400 when request body is malformed JSON")
            void shouldReturn400_whenJsonIsMalformed() throws Exception {
                String malformedJson = "{ \"firstName\": \"John\", \"email\": }";

                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(malformedJson))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when request body is missing entirely")
            void shouldReturn400_whenRequestBodyIsMissing() throws Exception {
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(customerService);
            }

            @Test
            @DisplayName("Should return 400 when request body is an empty JSON object")
            void shouldReturn400_whenRequestBodyIsEmptyObject() throws Exception {
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(customerService);
            }
        }

        // ==================== JSON RESPONSE STRUCTURE ====================

        @Nested
        @DisplayName("JSON response structure validation")
        class JsonResponseStructure {

            @Test
            @DisplayName("Should return all expected fields in the success response")
            void shouldReturnAllFieldsInResponse() throws Exception {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                when(customerService.createCustomer(any(CustomerRequestDTO.class)))
                        .thenReturn(validResponse(expectedId));

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").exists())
                        .andExpect(jsonPath("$.firstName").exists())
                        .andExpect(jsonPath("$.lastName").exists())
                        .andExpect(jsonPath("$.email").exists())
                        .andExpect(jsonPath("$.location").exists())
                        .andExpect(jsonPath("$.password").doesNotExist());
            }

            @Test
            @DisplayName("Error response should contain status, message, and timeStamp fields")
            void shouldReturnErrorResponseStructure() throws Exception {
                // Arrange — trigger a validation error
                CustomerRequestDTO request = new CustomerRequestDTO(
                        null, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, VALID_PASSWORD
                );

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty())
                        .andExpect(jsonPath("$.timeStamp").isNumber());
            }
        }

        // ==================== EDGE CASES ====================

        @Nested
        @DisplayName("Edge cases")
        class EdgeCases {

            @Test
            @DisplayName("Should return 201 when firstName is exactly 1 character (boundary minimum)")
            void shouldReturn201_whenFirstNameIsExactlyMinLength() throws Exception {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                CustomerRequestDTO request = new CustomerRequestDTO(
                        "A", VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, VALID_PASSWORD
                );
                CustomerResponseDTO response = new CustomerResponseDTO(
                        expectedId, "A", VALID_LAST_NAME,
                        VALID_EMAIL, VALID_LOCATION
                );
                when(customerService.createCustomer(any(CustomerRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.firstName").value("A"));
            }

            @Test
            @DisplayName("Should return 201 when firstName is exactly 100 characters (boundary maximum)")
            void shouldReturn201_whenFirstNameIsExactlyMaxLength() throws Exception {
                // Arrange
                String maxFirstName = "F".repeat(100);
                UUID expectedId = UUID.randomUUID();
                CustomerRequestDTO request = new CustomerRequestDTO(
                        maxFirstName, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, VALID_PASSWORD
                );
                CustomerResponseDTO response = new CustomerResponseDTO(
                        expectedId, maxFirstName, VALID_LAST_NAME,
                        VALID_EMAIL, VALID_LOCATION
                );
                when(customerService.createCustomer(any(CustomerRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated());
            }

            @Test
            @DisplayName("Should return 201 when password is exactly 12 characters (boundary minimum)")
            void shouldReturn201_whenPasswordIsExactlyMinLength() throws Exception {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, "Abcdefghijkl"
                );
                when(customerService.createCustomer(any(CustomerRequestDTO.class)))
                        .thenReturn(validResponse(expectedId));

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated());
            }

            @Test
            @DisplayName("Should return 201 when location is exactly 2 characters (boundary minimum)")
            void shouldReturn201_whenLocationIsExactlyMinLength() throws Exception {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        "NY", VALID_PASSWORD
                );
                CustomerResponseDTO response = new CustomerResponseDTO(
                        expectedId, VALID_FIRST_NAME, VALID_LAST_NAME,
                        VALID_EMAIL, "NY"
                );
                when(customerService.createCustomer(any(CustomerRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.location").value("NY"));
            }

            @Test
            @DisplayName("Should return 201 when email contains uppercase (compact constructor lowercases it)")
            void shouldReturn201_whenEmailHasUppercase() throws Exception {
                // Arrange — DTO compact constructor lowercases and trims the email
                UUID expectedId = UUID.randomUUID();
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, "John.Doe@Example.COM",
                        VALID_LOCATION, VALID_PASSWORD
                );
                CustomerResponseDTO response = new CustomerResponseDTO(
                        expectedId, VALID_FIRST_NAME, VALID_LAST_NAME,
                        "john.doe@example.com", VALID_LOCATION
                );
                when(customerService.createCustomer(any(CustomerRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated());
            }

            @Test
            @DisplayName("Should return 201 when firstName and lastName have leading/trailing spaces (compact constructor trims)")
            void shouldReturn201_whenNamesHaveWhitespace() throws Exception {
                // Arrange — the compact constructor trims firstName and lastName
                UUID expectedId = UUID.randomUUID();
                CustomerRequestDTO request = new CustomerRequestDTO(
                        "  John  ", "  Doe  ", VALID_EMAIL,
                        VALID_LOCATION, VALID_PASSWORD
                );
                CustomerResponseDTO response = new CustomerResponseDTO(
                        expectedId, "John", "Doe",
                        VALID_EMAIL, VALID_LOCATION
                );
                when(customerService.createCustomer(any(CustomerRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(CUSTOMERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated());
            }
        }
    }
}
