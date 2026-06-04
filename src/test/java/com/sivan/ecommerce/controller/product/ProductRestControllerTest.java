package com.sivan.ecommerce.controller.product;

import tools.jackson.databind.ObjectMapper;
import com.sivan.ecommerce.config.SecurityConfig;
import com.sivan.ecommerce.dto.product.ProductRequestDTO;
import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import com.sivan.ecommerce.exception.ProductNotFoundException;
import com.sivan.ecommerce.service.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductRestController.class)
@Import(SecurityConfig.class)
@DisplayName("ProductRestController")
class ProductRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    // ======================== Constants ========================

    private static final String PRODUCTS_URL = "/products";
    private static final String PRODUCT_BY_ID_URL = "/products/{productId}";

    private static final String VALID_TITLE = "Wireless Bluetooth Headphones";
    private static final String VALID_DESCRIPTION =
            "Premium noise-cancelling headphones with 40-hour battery life and deep bass.";
    private static final int VALID_QUANTITY = 50;
    private static final long VALID_PRICE = 7999L;
    private static final String VALID_CURRENCY = "USD";
    private static final String VALID_IMAGE_URL = "https://example.com/images/headphones.png";

    // ==================== createProduct() ====================
    @Nested
    @DisplayName("createProduct()")
    class CreateProduct {
        // ======================== Helpers ========================

        private ProductRequestDTO validRequest() {
            return new ProductRequestDTO(
                    VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                    VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );
        }

        private ProductResponseDTO validResponse(UUID id) {
            return new ProductResponseDTO(
                    id, VALID_TITLE, VALID_DESCRIPTION,
                    VALID_QUANTITY, VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );
        }

        // ==================== SUCCESS CASES (201) ====================

        @Nested
        @DisplayName("Success cases — 201 Created")
        class SuccessCases {

            @Test
            @DisplayName("Should return 201 and correct JSON when admin creates a valid product")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn201_whenAdminCreatesValidProduct() throws Exception {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                when(productService.createProduct(any(ProductRequestDTO.class)))
                        .thenReturn(validResponse(expectedId));

                // Act & Assert
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").value(expectedId.toString()))
                        .andExpect(jsonPath("$.title").value(VALID_TITLE))
                        .andExpect(jsonPath("$.description").value(VALID_DESCRIPTION))
                        .andExpect(jsonPath("$.quantity").value(VALID_QUANTITY))
                        .andExpect(jsonPath("$.price").value(VALID_PRICE))
                        .andExpect(jsonPath("$.currencyCode").value(VALID_CURRENCY))
                        .andExpect(jsonPath("$.imageUrl").value(VALID_IMAGE_URL));

                verify(productService).createProduct(any(ProductRequestDTO.class));
            }

            @Test
            @DisplayName("Should return 201 when admin creates a product with null description")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn201_whenDescriptionIsNull() throws Exception {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, null, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                ProductResponseDTO response = new ProductResponseDTO(
                        expectedId, VALID_TITLE, null,
                        VALID_QUANTITY, VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productService.createProduct(any(ProductRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").value(expectedId.toString()))
                        .andExpect(jsonPath("$.description").doesNotExist());
            }

            @Test
            @DisplayName("Should call productService.createProduct exactly once")
            @WithMockUser(roles = "ADMIN")
            void shouldCallServiceExactlyOnce() throws Exception {
                // Arrange
                when(productService.createProduct(any(ProductRequestDTO.class)))
                        .thenReturn(validResponse(UUID.randomUUID()));

                // Act
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated());

                // Assert
                verify(productService).createProduct(any(ProductRequestDTO.class));
                verifyNoMoreInteractions(productService);
            }
        }

        // ==================== AUTHENTICATION FAILURES (401) ====================

        @Nested
        @DisplayName("Authentication failures — 401 Unauthorized")
        class AuthenticationFailures {

            @Test
            @DisplayName("Should return 401 when no credentials are provided (anonymous)")
            void shouldReturn401_whenNoCredentials() throws Exception {
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isUnauthorized());

                verifyNoInteractions(productService);
            }
        }

        // ==================== AUTHORIZATION FAILURES (403) ====================

        @Nested
        @DisplayName("Authorization failures — 403 Forbidden")
        class AuthorizationFailures {

            @Test
            @DisplayName("Should return 403 when authenticated user has ROLE_USER (not ADMIN)")
            @WithMockUser(roles = "USER")
            void shouldReturn403_whenUserRoleIsNotAdmin() throws Exception {
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 403 when user has no roles at all")
            @WithMockUser(roles = {})
            void shouldReturn403_whenUserHasNoRoles() throws Exception {
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(productService);
            }
        }

        // ==================== VALIDATION FAILURES (400) ====================

        @Nested
        @DisplayName("Validation failures — 400 Bad Request")
        class ValidationFailures {

            // ---------- title ----------

            @Test
            @DisplayName("Should return 400 when title is null")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenTitleIsNull() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        null, VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when title is blank")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenTitleIsBlank() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        "   ", VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when title is too short (less than 3 characters)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenTitleIsTooShort() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        "AB", VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when title exceeds 255 characters")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenTitleTooLong() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        "A".repeat(256), VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            // ---------- description ----------

            @Test
            @DisplayName("Should return 400 when description is provided but shorter than 40 characters")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenDescriptionTooShort() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, "Too short description", VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when description exceeds 5000 characters")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenDescriptionTooLong() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, "D".repeat(5001), VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            // ---------- quantity ----------

            @Test
            @DisplayName("Should return 400 when quantity is null")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenQuantityIsNull() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, null,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when quantity is negative")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenQuantityIsNegative() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, -1,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            // ---------- price ----------

            @Test
            @DisplayName("Should return 400 when price is null")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenPriceIsNull() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        null, VALID_CURRENCY, VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when price is negative")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenPriceIsNegative() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        -100L, VALID_CURRENCY, VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            // ---------- currencyCode ----------

            @Test
            @DisplayName("Should return 400 when currencyCode is null")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenCurrencyCodeIsNull() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, null, VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when currencyCode is blank")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenCurrencyCodeIsBlank() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, "   ", VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when currencyCode is not exactly 3 characters (too short)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenCurrencyCodeTooShort() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, "US", VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when currencyCode is not exactly 3 characters (too long)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenCurrencyCodeTooLong() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, "USDT", VALID_IMAGE_URL
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            // ---------- imageUrl ----------

            @Test
            @DisplayName("Should return 400 when imageUrl is null")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenImageUrlIsNull() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, null
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when imageUrl is blank")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenImageUrlIsBlank() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, "   "
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when imageUrl is not a valid URL format")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenImageUrlIsInvalid() throws Exception {
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, "not-a-url"
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when imageUrl exceeds 512 characters")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenImageUrlTooLong() throws Exception {
                // Build a URL that exceeds the 512-char limit
                String longUrl = "https://example.com/" + "a".repeat(500);
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, longUrl
                );

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }
        }

        // ==================== SERVICE EXCEPTION HANDLING ====================

        @Nested
        @DisplayName("Service exception handling")
        class ServiceExceptionHandling {

            @Test
            @DisplayName("Should return 500 when service throws an unexpected RuntimeException")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn500_whenServiceThrowsRuntimeException() throws Exception {
                // Arrange
                when(productService.createProduct(any(ProductRequestDTO.class)))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                mockMvc.perform(post(PRODUCTS_URL)
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
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenJsonIsMalformed() throws Exception {
                String malformedJson = "{ \"title\": \"Test\", \"price\": }";

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(malformedJson))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when request body is missing entirely")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenRequestBodyIsMissing() throws Exception {
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when request body is an empty JSON object")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenRequestBodyIsEmptyObject() throws Exception {
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when quantity is a string instead of a number")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenQuantityIsWrongType() throws Exception {
                String badJson = """
                        {
                            "title": "Valid Title",
                            "description": null,
                            "quantity": "not-a-number",
                            "price": 100,
                            "currencyCode": "USD",
                            "imageUrl": "https://example.com/img.png"
                        }
                        """;

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badJson))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when price is a string instead of a number")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenPriceIsWrongType() throws Exception {
                String badJson = """
                        {
                            "title": "Valid Title",
                            "description": null,
                            "quantity": 10,
                            "price": "expensive",
                            "currencyCode": "USD",
                            "imageUrl": "https://example.com/img.png"
                        }
                        """;

                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badJson))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(productService);
            }
        }

        // ==================== JSON RESPONSE STRUCTURE ====================

        @Nested
        @DisplayName("JSON response structure validation")
        class JsonResponseStructure {

            @Test
            @DisplayName("Should return all expected fields in the success response")
            @WithMockUser(roles = "ADMIN")
            void shouldReturnAllFieldsInResponse() throws Exception {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                when(productService.createProduct(any(ProductRequestDTO.class)))
                        .thenReturn(validResponse(expectedId));

                // Act & Assert — verify the response has exactly the expected fields
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").exists())
                        .andExpect(jsonPath("$.title").exists())
                        .andExpect(jsonPath("$.description").exists())
                        .andExpect(jsonPath("$.quantity").exists())
                        .andExpect(jsonPath("$.price").exists())
                        .andExpect(jsonPath("$.currencyCode").exists())
                        .andExpect(jsonPath("$.imageUrl").exists());
            }

            @Test
            @DisplayName("Error response should contain status, message, and timeStamp fields")
            @WithMockUser(roles = "ADMIN")
            void shouldReturnErrorResponseStructure() throws Exception {
                // Arrange — trigger a validation error
                ProductRequestDTO request = new ProductRequestDTO(
                        null, VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                // Act & Assert
                mockMvc.perform(post(PRODUCTS_URL)
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
            @DisplayName("Should return 201 when title is exactly 3 characters (boundary minimum)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn201_whenTitleIsExactlyMinLength() throws Exception {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                ProductRequestDTO request = new ProductRequestDTO(
                        "Abc", VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                ProductResponseDTO response = new ProductResponseDTO(
                        expectedId, "Abc", VALID_DESCRIPTION,
                        VALID_QUANTITY, VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productService.createProduct(any(ProductRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.title").value("Abc"));
            }

            @Test
            @DisplayName("Should return 201 when title is exactly 255 characters (boundary maximum)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn201_whenTitleIsExactlyMaxLength() throws Exception {
                // Arrange
                String maxTitle = "T".repeat(255);
                UUID expectedId = UUID.randomUUID();
                ProductRequestDTO request = new ProductRequestDTO(
                        maxTitle, VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                ProductResponseDTO response = new ProductResponseDTO(
                        expectedId, maxTitle, VALID_DESCRIPTION,
                        VALID_QUANTITY, VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productService.createProduct(any(ProductRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated());
            }

            @Test
            @DisplayName("Should return 201 when quantity and price are both zero (boundary)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn201_whenQuantityAndPriceAreZero() throws Exception {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, 0,
                        0L, VALID_CURRENCY, VALID_IMAGE_URL
                );
                ProductResponseDTO response = new ProductResponseDTO(
                        expectedId, VALID_TITLE, VALID_DESCRIPTION,
                        0, 0L, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productService.createProduct(any(ProductRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.quantity").value(0))
                        .andExpect(jsonPath("$.price").value(0));
            }

            @Test
            @DisplayName("Should return 201 when description is exactly 40 characters (boundary minimum)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn201_whenDescriptionIsExactlyMinLength() throws Exception {
                // Arrange
                String exactly40 = "A".repeat(40);
                UUID expectedId = UUID.randomUUID();
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, exactly40, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                ProductResponseDTO response = new ProductResponseDTO(
                        expectedId, VALID_TITLE, exactly40,
                        VALID_QUANTITY, VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productService.createProduct(any(ProductRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.description").value(exactly40));
            }
        }
    }

    // ==================== getProduct() ====================
    @Nested
    @DisplayName("getProduct()")
    class GetProduct {

        // ======================== Helpers ========================

        private ProductResponseDTO validProductResponse(UUID id) {
            return new ProductResponseDTO(
                    id, VALID_TITLE, VALID_DESCRIPTION,
                    VALID_QUANTITY, VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );
        }

        // ==================== SUCCESS CASES (200) ====================

        @Nested
        @DisplayName("Success cases — 200 OK")
        class SuccessCases {

            @Test
            @DisplayName("Should return 200 and correct JSON when product exists")
            void shouldReturn200_whenProductExists() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productService.getProduct(eq(productId)))
                        .thenReturn(validProductResponse(productId));

                // Act & Assert
                mockMvc.perform(get(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(productId.toString()))
                        .andExpect(jsonPath("$.title").value(VALID_TITLE))
                        .andExpect(jsonPath("$.description").value(VALID_DESCRIPTION))
                        .andExpect(jsonPath("$.quantity").value(VALID_QUANTITY))
                        .andExpect(jsonPath("$.price").value(VALID_PRICE))
                        .andExpect(jsonPath("$.currencyCode").value(VALID_CURRENCY))
                        .andExpect(jsonPath("$.imageUrl").value(VALID_IMAGE_URL));

                verify(productService).getProduct(eq(productId));
            }

            @Test
            @DisplayName("Should return 200 when description is null in response")
            void shouldReturn200_whenDescriptionIsNull() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                ProductResponseDTO response = new ProductResponseDTO(
                        productId, VALID_TITLE, null,
                        VALID_QUANTITY, VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productService.getProduct(eq(productId))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(get(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(productId.toString()))
                        .andExpect(jsonPath("$.description").value(nullValue()));
            }

            @Test
            @DisplayName("Should call productService.getProduct exactly once")
            void shouldCallServiceExactlyOnce() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productService.getProduct(eq(productId)))
                        .thenReturn(validProductResponse(productId));

                // Act
                mockMvc.perform(get(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isOk());

                // Assert
                verify(productService).getProduct(eq(productId));
                verifyNoMoreInteractions(productService);
            }

            @Test
            @DisplayName("Should return 200 without authentication (permitAll endpoint)")
            void shouldReturn200_withoutAuthentication() throws Exception {
                // Arrange — no @WithMockUser, proving permitAll() works
                UUID productId = UUID.randomUUID();
                when(productService.getProduct(eq(productId)))
                        .thenReturn(validProductResponse(productId));

                // Act & Assert
                mockMvc.perform(get(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isOk());
            }
        }

        // ==================== NOT FOUND CASES (404) ====================

        @Nested
        @DisplayName("Not found cases — 404 Not Found")
        class NotFoundCases {

            @Test
            @DisplayName("Should return 404 when product does not exist")
            void shouldReturn404_whenProductNotFound() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productService.getProduct(eq(productId)))
                        .thenThrow(new ProductNotFoundException("Product not found"));

                // Act & Assert
                mockMvc.perform(get(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").value("Product not found"));
            }
        }

        // ==================== INVALID PATH VARIABLE (400) ====================

        @Nested
        @DisplayName("Invalid path variable — 400 Bad Request")
        class InvalidPathVariable {

            @Test
            @DisplayName("Should return 400 when productId is not a valid UUID")
            void shouldReturn400_whenProductIdIsNotValidUuid() throws Exception {
                String pathVariable = "not-a-uuid";

                mockMvc.perform(get("/products/{productId}", pathVariable))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Invalid URL parameter: '" + pathVariable + "' is not a valid format"));

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when productId is a plain number instead of UUID")
            void shouldReturn400_whenProductIdIsNumeric() throws Exception {
                String pathVariable = "12345";

                mockMvc.perform(get("/products/{productId}", pathVariable))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Invalid URL parameter: '" + pathVariable + "' is not a valid format"));

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when productId is an empty string")
            void shouldReturn400_whenProductIdIsEmpty() throws Exception {
                mockMvc.perform(get("/products/{productId}", " "))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("The required 'path variable' is missing from the URL path"));

                verifyNoInteractions(productService);
            }
        }

        // ==================== SERVICE EXCEPTION HANDLING ====================

        @Nested
        @DisplayName("Service exception handling")
        class ServiceExceptionHandling {

            @Test
            @DisplayName("Should return 500 when service throws an unexpected RuntimeException")
            void shouldReturn500_whenServiceThrowsRuntimeException() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productService.getProduct(eq(productId)))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                mockMvc.perform(get(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.status").value(500))
                        .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
            }
        }

        // ==================== JSON RESPONSE STRUCTURE ====================

        @Nested
        @DisplayName("JSON response structure validation")
        class JsonResponseStructure {

            @Test
            @DisplayName("Should return all expected fields in the product response")
            void shouldReturnAllFieldsInResponse() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productService.getProduct(eq(productId)))
                        .thenReturn(validProductResponse(productId));

                // Act & Assert
                mockMvc.perform(get(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").exists())
                        .andExpect(jsonPath("$.title").exists())
                        .andExpect(jsonPath("$.description").exists())
                        .andExpect(jsonPath("$.quantity").exists())
                        .andExpect(jsonPath("$.price").exists())
                        .andExpect(jsonPath("$.currencyCode").exists())
                        .andExpect(jsonPath("$.imageUrl").exists());
            }

            @Test
            @DisplayName("Error response should contain status, message, and timeStamp fields")
            void shouldReturnErrorResponseStructure() throws Exception {
                // Arrange — trigger a not-found error
                UUID productId = UUID.randomUUID();
                when(productService.getProduct(eq(productId)))
                        .thenThrow(new ProductNotFoundException("Product not found"));

                // Act & Assert
                mockMvc.perform(get(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").isNotEmpty())
                        .andExpect(jsonPath("$.timeStamp").isNumber());
            }
        }
    }
}
