package com.sivan.ecommerce.controller.product;

import tools.jackson.databind.ObjectMapper;
import com.sivan.ecommerce.config.SecurityConfig;
import com.sivan.ecommerce.dto.product.ProductFilterDTO;
import com.sivan.ecommerce.dto.product.ProductRequestDTO;
import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import com.sivan.ecommerce.exception.CategoryNotFoundException;
import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.exception.ProductNotFoundException;
import com.sivan.ecommerce.service.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
                    id.toString(), VALID_TITLE, VALID_DESCRIPTION,
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
                        expectedId.toString(), VALID_TITLE, null,
                        VALID_QUANTITY, VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productService.createProduct(any(ProductRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(PRODUCTS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").value(expectedId.toString()))
                        .andExpect(jsonPath("$.description").value(nullValue()));
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

            @Test
            @DisplayName("Should return 401 when invalid credentials are provided")
            void shouldReturn401_whenInvalidCredentials() throws Exception {
                mockMvc.perform(post(PRODUCTS_URL)
                                .with(httpBasic("wrong@email.com", "WrongPassword1!"))
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
                        expectedId.toString(), "Abc", VALID_DESCRIPTION,
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
                        expectedId.toString(), maxTitle, VALID_DESCRIPTION,
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
                        expectedId.toString(), VALID_TITLE, VALID_DESCRIPTION,
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
                        expectedId.toString(), VALID_TITLE, exactly40,
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
                    id.toString(), VALID_TITLE, VALID_DESCRIPTION,
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
                        productId.toString(), VALID_TITLE, null,
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

    // ==================== getProducts() ====================
    @Nested
    @DisplayName("getProducts()")
    class GetProducts {

        // ======================== Helpers ========================

        /**
         * Builds a single-item page of {@link ProductResponseDTO} for mock returns.
         */
        private Page<ProductResponseDTO> singleProductPage() {
            ProductResponseDTO product = new ProductResponseDTO(
                    UUID.randomUUID().toString(), VALID_TITLE, VALID_DESCRIPTION,
                    VALID_QUANTITY, VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );
            return new PageImpl<>(List.of(product), PageRequest.of(0, 10), 1);
        }

        /**
         * Builds an empty page for mock returns.
         */
        private Page<ProductResponseDTO> emptyPage() {
            return new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        }

        // ==================== SUCCESS CASES (200) ====================

        @Nested
        @DisplayName("Success cases — 200 OK")
        class SuccessCases {

            @Test
            @DisplayName("Should return 200 and a page of products with no filters applied")
            void shouldReturn200_whenNoFiltersApplied() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray())
                        .andExpect(jsonPath("$.content", hasSize(1)))
                        .andExpect(jsonPath("$.content[0].title").value(VALID_TITLE))
                        .andExpect(jsonPath("$.content[0].description").value(VALID_DESCRIPTION))
                        .andExpect(jsonPath("$.content[0].quantity").value(VALID_QUANTITY))
                        .andExpect(jsonPath("$.content[0].price").value(VALID_PRICE))
                        .andExpect(jsonPath("$.content[0].currencyCode").value(VALID_CURRENCY))
                        .andExpect(jsonPath("$.content[0].imageUrl").value(VALID_IMAGE_URL));

                verify(productService).getProducts(any(ProductFilterDTO.class), any(Pageable.class));
            }

            @Test
            @DisplayName("Should return 200 with all filter query parameters applied")
            void shouldReturn200_whenAllFiltersApplied() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("title", "Headphones")
                                .param("minPrice", "1000")
                                .param("maxPrice", "5000")
                                .param("category", "Electronics"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray())
                        .andExpect(jsonPath("$.content", hasSize(1)));

                verify(productService).getProducts(any(ProductFilterDTO.class), any(Pageable.class));
            }

            @Test
            @DisplayName("Should return 200 with only title filter applied")
            void shouldReturn200_whenOnlyTitleFilterApplied() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("title", "Wireless"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray())
                        .andExpect(jsonPath("$.content", hasSize(1)));
            }

            @Test
            @DisplayName("Should return 200 with only price range filters applied")
            void shouldReturn200_whenOnlyPriceRangeApplied() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("minPrice", "100")
                                .param("maxPrice", "9999"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray())
                        .andExpect(jsonPath("$.content", hasSize(1)));
            }

            @Test
            @DisplayName("Should return 200 with empty content when no products match filters")
            void shouldReturn200_withEmptyContent_whenNoProductsMatch() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(emptyPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("title", "NonExistentProduct"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray())
                        .andExpect(jsonPath("$.content", hasSize(0)))
                        .andExpect(jsonPath("$.totalElements").value(0));
            }

            @Test
            @DisplayName("Should return 200 with pagination metadata in response")
            void shouldReturn200_withPaginationMetadata() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("page", "0")
                                .param("size", "10"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray())
                        .andExpect(jsonPath("$.totalElements").isNumber())
                        .andExpect(jsonPath("$.totalPages").isNumber())
                        .andExpect(jsonPath("$.size").value(10))
                        .andExpect(jsonPath("$.number").value(0));
            }

            @Test
            @DisplayName("Should return 200 with valid sort parameter (price ascending)")
            void shouldReturn200_whenSortByPriceAsc() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("sort", "price,asc"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());
            }

            @Test
            @DisplayName("Should return 200 with valid sort parameter (title descending)")
            void shouldReturn200_whenSortByTitleDesc() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("sort", "title,desc"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());
            }

            @Test
            @DisplayName("Should return 200 without authentication (permitAll endpoint)")
            void shouldReturn200_withoutAuthentication() throws Exception {
                // Arrange — no @WithMockUser, proving permitAll() works
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL))
                        .andExpect(status().isOk());
            }

            @Test
            @DisplayName("Should call productService.getProducts exactly once")
            void shouldCallServiceExactlyOnce() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act
                mockMvc.perform(get(PRODUCTS_URL))
                        .andExpect(status().isOk());

                // Assert
                verify(productService).getProducts(any(ProductFilterDTO.class), any(Pageable.class));
                verifyNoMoreInteractions(productService);
            }
        }

        // ==================== VALIDATION FAILURES (400) ====================

        @Nested
        @DisplayName("Validation failures — 400 Bad Request")
        class ValidationFailures {

            // ---------- title ----------

            @Test
            @DisplayName("Should return 400 when search is shorter than 3 characters")
            void shouldReturn400_whenTitleTooShort() throws Exception {
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("search", "AB"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when search is blank")
            void shouldReturn400_whenTitleIsBlank() throws Exception {
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("search", "                   "))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when search exceeds 255 characters")
            void shouldReturn400_whenTitleTooLong() throws Exception {
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("search", "A".repeat(256)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            // ---------- minPrice ----------

            @Test
            @DisplayName("Should return 400 when minPrice is negative")
            void shouldReturn400_whenMinPriceIsNegative() throws Exception {
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("minPrice", "-1"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            // ---------- maxPrice ----------

            @Test
            @DisplayName("Should return 400 when maxPrice is negative")
            void shouldReturn400_whenMaxPriceIsNegative() throws Exception {
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("maxPrice", "-1"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            // ---------- category ----------

            @Test
            @DisplayName("Should return 400 when category is blank")
            void shouldReturn400_whenCategoryIsBlank() throws Exception {
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("category", "      "))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when category exceeds 255 characters")
            void shouldReturn400_whenCategoryTooLong() throws Exception {
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("category", "C".repeat(256)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(productService);
            }

            // ---------- type mismatch ----------

            @Test
            @DisplayName("Should return 400 when minPrice is not a number")
            void shouldReturn400_whenMinPriceIsNotANumber() throws Exception {
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("minPrice", "abc"))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when maxPrice is not a number")
            void shouldReturn400_whenMaxPriceIsNotANumber() throws Exception {
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("maxPrice", "xyz"))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(productService);
            }
        }

        // ==================== BUSINESS RULE FAILURES (400 via InvalidDataException) ====================

        @Nested
        @DisplayName("Business rule failures — 400 Bad Request (via service)")
        class BusinessRuleFailures {

            @Test
            @DisplayName("Should return 400 when minPrice is greater than maxPrice")
            void shouldReturn400_whenMinPriceGreaterThanMaxPrice() throws Exception {
                // Arrange — the service enforces this rule
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenThrow(new InvalidDataException("Minimum price must be less than or equal to maximum price"));

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("minPrice", "5000")
                                .param("maxPrice", "1000"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Minimum price must be less than or equal to maximum price"));
            }

            @Test
            @DisplayName("Should return 400 when sorting by a disallowed field")
            void shouldReturn400_whenSortByDisallowedField() throws Exception {
                // Arrange — the service enforces allowed sort fields ("title", "price")
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenThrow(new InvalidDataException("Sorting by 'description' is not allowed"));

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("sort", "description,asc"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Sorting by 'description' is not allowed"));
            }

            @Test
            @DisplayName("Should return 400 when sorting by 'quantity' (disallowed)")
            void shouldReturn400_whenSortByQuantity() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenThrow(new InvalidDataException("Sorting by 'quantity' is not allowed"));

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("sort", "quantity,asc"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Sorting by 'quantity' is not allowed"));
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
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL))
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
            @DisplayName("Should return paginated response with all Spring Data Page fields")
            void shouldReturnPaginatedResponseStructure() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray())
                        .andExpect(jsonPath("$.content[0].id").exists())
                        .andExpect(jsonPath("$.content[0].title").exists())
                        .andExpect(jsonPath("$.content[0].description").exists())
                        .andExpect(jsonPath("$.content[0].quantity").exists())
                        .andExpect(jsonPath("$.content[0].price").exists())
                        .andExpect(jsonPath("$.content[0].currencyCode").exists())
                        .andExpect(jsonPath("$.content[0].imageUrl").exists())
                        .andExpect(jsonPath("$.totalElements").exists())
                        .andExpect(jsonPath("$.totalPages").exists())
                        .andExpect(jsonPath("$.size").exists())
                        .andExpect(jsonPath("$.number").exists());
            }

            @Test
            @DisplayName("Error response should contain status, message, and timeStamp fields")
            void shouldReturnErrorResponseStructure() throws Exception {
                // Arrange — trigger a business rule error
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenThrow(new InvalidDataException("Minimum price must be less than or equal to maximum price"));

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("minPrice", "5000")
                                .param("maxPrice", "1000"))
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
            @DisplayName("Should return 200 when title is exactly 3 characters (boundary minimum)")
            void shouldReturn200_whenTitleIsExactlyMinLength() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("title", "Abc"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());
            }

            @Test
            @DisplayName("Should return 200 when title is exactly 255 characters (boundary maximum)")
            void shouldReturn200_whenTitleIsExactlyMaxLength() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("title", "T".repeat(255)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());
            }

            @Test
            @DisplayName("Should return 200 when category is exactly 255 characters (boundary maximum)")
            void shouldReturn200_whenCategoryIsExactlyMaxLength() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("category", "C".repeat(255)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());
            }

            @Test
            @DisplayName("Should return 200 when minPrice is zero (boundary minimum)")
            void shouldReturn200_whenMinPriceIsZero() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("minPrice", "0"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());
            }

            @Test
            @DisplayName("Should return 200 when maxPrice is zero (boundary minimum)")
            void shouldReturn200_whenMaxPriceIsZero() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("maxPrice", "0"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());
            }

            @Test
            @DisplayName("Should return 200 when minPrice equals maxPrice")
            void shouldReturn200_whenMinPriceEqualsMaxPrice() throws Exception {
                // Arrange
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleProductPage());

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL)
                                .param("minPrice", "5000")
                                .param("maxPrice", "5000"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());
            }

            @Test
            @DisplayName("Should return 200 with multiple products in the page")
            void shouldReturn200_withMultipleProducts() throws Exception {
                // Arrange
                ProductResponseDTO product1 = new ProductResponseDTO(
                        UUID.randomUUID().toString(), "Product One", VALID_DESCRIPTION,
                        VALID_QUANTITY, 2999L, VALID_CURRENCY, VALID_IMAGE_URL
                );
                ProductResponseDTO product2 = new ProductResponseDTO(
                        UUID.randomUUID().toString(), "Product Two", VALID_DESCRIPTION,
                        VALID_QUANTITY, 4999L, VALID_CURRENCY, VALID_IMAGE_URL
                );
                Page<ProductResponseDTO> multiPage = new PageImpl<>(
                        List.of(product1, product2), PageRequest.of(0, 10), 2
                );
                when(productService.getProducts(any(ProductFilterDTO.class), any(Pageable.class)))
                        .thenReturn(multiPage);

                // Act & Assert
                mockMvc.perform(get(PRODUCTS_URL))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(2)))
                        .andExpect(jsonPath("$.content[0].title").value("Product One"))
                        .andExpect(jsonPath("$.content[1].title").value("Product Two"))
                        .andExpect(jsonPath("$.totalElements").value(2));
            }
        }
    }

    // ==================== linkCategoryToProduct() ====================
    @Nested
    @DisplayName("linkCategoryToProduct()")
    class LinkCategoryToProduct {

        // ======================== Constants ========================

        private static final String LINK_CATEGORY_URL = "/products/{productId}/categories/{categoryId}";

        // ==================== SUCCESS CASES (204) ====================

        @Nested
        @DisplayName("Success cases — 204 No Content")
        class SuccessCases {

            @Test
            @DisplayName("Should return 204 when admin links a valid category to a valid product")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn204_whenAdminLinksValidCategoryToProduct() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                doNothing().when(productService).linkCategoryToProduct(eq(productId), eq(categoryId));

                // Act & Assert
                mockMvc.perform(put(LINK_CATEGORY_URL, productId, categoryId))
                        .andExpect(status().isNoContent());

                verify(productService).linkCategoryToProduct(eq(productId), eq(categoryId));
            }

            @Test
            @DisplayName("Should return empty body when link is successful")
            @WithMockUser(roles = "ADMIN")
            void shouldReturnEmptyBody_whenLinkIsSuccessful() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                doNothing().when(productService).linkCategoryToProduct(eq(productId), eq(categoryId));

                // Act & Assert
                mockMvc.perform(put(LINK_CATEGORY_URL, productId, categoryId))
                        .andExpect(status().isNoContent())
                        .andExpect(jsonPath("$").doesNotExist());
            }

            @Test
            @DisplayName("Should call productService.linkCategoryToProduct exactly once")
            @WithMockUser(roles = "ADMIN")
            void shouldCallServiceExactlyOnce() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                doNothing().when(productService).linkCategoryToProduct(eq(productId), eq(categoryId));

                // Act
                mockMvc.perform(put(LINK_CATEGORY_URL, productId, categoryId))
                        .andExpect(status().isNoContent());

                // Assert
                verify(productService).linkCategoryToProduct(eq(productId), eq(categoryId));
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
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();

                mockMvc.perform(put(LINK_CATEGORY_URL, productId, categoryId))
                        .andExpect(status().isUnauthorized());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 401 when invalid credentials are provided")
            void shouldReturn401_whenInvalidCredentials() throws Exception {
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();

                mockMvc.perform(put(LINK_CATEGORY_URL, productId, categoryId)
                                .with(httpBasic("wrong@email.com", "WrongPassword1!")))
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
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();

                mockMvc.perform(put(LINK_CATEGORY_URL, productId, categoryId))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 403 when user has no roles at all")
            @WithMockUser(roles = {})
            void shouldReturn403_whenUserHasNoRoles() throws Exception {
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();

                mockMvc.perform(put(LINK_CATEGORY_URL, productId, categoryId))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(productService);
            }
        }

        // ==================== NOT FOUND CASES (404) ====================

        @Nested
        @DisplayName("Not found cases — 404 Not Found")
        class NotFoundCases {

            @Test
            @DisplayName("Should return 404 when product does not exist")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn404_whenProductNotFound() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                doThrow(new ProductNotFoundException("Product not found"))
                        .when(productService).linkCategoryToProduct(eq(productId), eq(categoryId));

                // Act & Assert
                mockMvc.perform(put(LINK_CATEGORY_URL, productId, categoryId))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").value("Product not found"));
            }

            @Test
            @DisplayName("Should return 404 when category does not exist")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn404_whenCategoryNotFound() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                doThrow(new CategoryNotFoundException("Category not found"))
                        .when(productService).linkCategoryToProduct(eq(productId), eq(categoryId));

                // Act & Assert
                mockMvc.perform(put(LINK_CATEGORY_URL, productId, categoryId))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").value("Category not found"));
            }
        }

        // ==================== INVALID PATH VARIABLE (400) ====================

        @Nested
        @DisplayName("Invalid path variable — 400 Bad Request")
        class InvalidPathVariable {

            @Test
            @DisplayName("Should return 400 when productId is not a valid UUID")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenProductIdIsNotValidUuid() throws Exception {
                String invalidProductId = "not-a-uuid";
                UUID categoryId = UUID.randomUUID();

                mockMvc.perform(put("/products/{productId}/categories/{categoryId}", invalidProductId, categoryId))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Invalid URL parameter: '" + invalidProductId + "' is not a valid format"));

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when categoryId is not a valid UUID")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenCategoryIdIsNotValidUuid() throws Exception {
                UUID productId = UUID.randomUUID();
                String invalidCategoryId = "not-a-uuid";

                mockMvc.perform(put("/products/{productId}/categories/{categoryId}", productId, invalidCategoryId))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Invalid URL parameter: '" + invalidCategoryId + "' is not a valid format"));

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when both productId and categoryId are not valid UUIDs")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenBothIdsAreNotValidUuids() throws Exception {
                String invalidProductId = "abc-123";
                String invalidCategoryId = "xyz-456";

                mockMvc.perform(put("/products/{productId}/categories/{categoryId}", invalidProductId, invalidCategoryId))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400));

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when productId is a plain number instead of UUID")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenProductIdIsNumeric() throws Exception {
                String numericId = "12345";
                UUID categoryId = UUID.randomUUID();

                mockMvc.perform(put("/products/{productId}/categories/{categoryId}", numericId, categoryId))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Invalid URL parameter: '" + numericId + "' is not a valid format"));

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when categoryId is a plain number instead of UUID")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenCategoryIdIsNumeric() throws Exception {
                UUID productId = UUID.randomUUID();
                String numericId = "67890";

                mockMvc.perform(put("/products/{productId}/categories/{categoryId}", productId, numericId))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Invalid URL parameter: '" + numericId + "' is not a valid format"));

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
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                doThrow(new RuntimeException("Database connection lost"))
                        .when(productService).linkCategoryToProduct(eq(productId), eq(categoryId));

                // Act & Assert
                mockMvc.perform(put(LINK_CATEGORY_URL, productId, categoryId))
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
            @DisplayName("Error response should contain status, message, and timeStamp fields when product not found")
            @WithMockUser(roles = "ADMIN")
            void shouldReturnErrorResponseStructure_whenProductNotFound() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                doThrow(new ProductNotFoundException("Product not found"))
                        .when(productService).linkCategoryToProduct(eq(productId), eq(categoryId));

                // Act & Assert
                mockMvc.perform(put(LINK_CATEGORY_URL, productId, categoryId))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").isNotEmpty())
                        .andExpect(jsonPath("$.timeStamp").isNumber());
            }

            @Test
            @DisplayName("Error response should contain status, message, and timeStamp fields when category not found")
            @WithMockUser(roles = "ADMIN")
            void shouldReturnErrorResponseStructure_whenCategoryNotFound() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                doThrow(new CategoryNotFoundException("Category not found"))
                        .when(productService).linkCategoryToProduct(eq(productId), eq(categoryId));

                // Act & Assert
                mockMvc.perform(put(LINK_CATEGORY_URL, productId, categoryId))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").isNotEmpty())
                        .andExpect(jsonPath("$.timeStamp").isNumber());
            }
        }
    }

    // ==================== deleteProduct() ====================
    @Nested
    @DisplayName("deleteProduct()")
    class DeleteProduct {

        // ==================== SUCCESS CASES (204) ====================

        @Nested
        @DisplayName("Success cases — 204 No Content")
        class SuccessCases {

            @Test
            @DisplayName("Should return 204 when admin deletes an existing product")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn204_whenAdminDeletesExistingProduct() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                doNothing().when(productService).deleteProduct(eq(productId));

                // Act & Assert
                mockMvc.perform(delete(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isNoContent());

                verify(productService).deleteProduct(eq(productId));
            }

            @Test
            @DisplayName("Should return empty body when delete is successful")
            @WithMockUser(roles = "ADMIN")
            void shouldReturnEmptyBody_whenDeleteIsSuccessful() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                doNothing().when(productService).deleteProduct(eq(productId));

                // Act & Assert
                mockMvc.perform(delete(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isNoContent())
                        .andExpect(jsonPath("$").doesNotExist());
            }

            @Test
            @DisplayName("Should call productService.deleteProduct exactly once")
            @WithMockUser(roles = "ADMIN")
            void shouldCallServiceExactlyOnce() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                doNothing().when(productService).deleteProduct(eq(productId));

                // Act
                mockMvc.perform(delete(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isNoContent());

                // Assert
                verify(productService).deleteProduct(eq(productId));
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
                UUID productId = UUID.randomUUID();

                mockMvc.perform(delete(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isUnauthorized());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 401 when invalid credentials are provided")
            void shouldReturn401_whenInvalidCredentials() throws Exception {
                UUID productId = UUID.randomUUID();

                mockMvc.perform(delete(PRODUCT_BY_ID_URL, productId)
                                .with(httpBasic("wrong@email.com", "WrongPassword1!")))
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
                UUID productId = UUID.randomUUID();

                mockMvc.perform(delete(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 403 when user has no roles at all")
            @WithMockUser(roles = {})
            void shouldReturn403_whenUserHasNoRoles() throws Exception {
                UUID productId = UUID.randomUUID();

                mockMvc.perform(delete(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(productService);
            }
        }

        // ==================== NOT FOUND CASES (404) ====================

        @Nested
        @DisplayName("Not found cases — 404 Not Found")
        class NotFoundCases {

            @Test
            @DisplayName("Should return 404 when product does not exist")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn404_whenProductNotFound() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                doThrow(new ProductNotFoundException("Product not found"))
                        .when(productService).deleteProduct(eq(productId));

                // Act & Assert
                mockMvc.perform(delete(PRODUCT_BY_ID_URL, productId))
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
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenProductIdIsNotValidUuid() throws Exception {
                String pathVariable = "not-a-uuid";

                mockMvc.perform(delete("/products/{productId}", pathVariable))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Invalid URL parameter: '" + pathVariable + "' is not a valid format"));

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when productId is a plain number instead of UUID")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenProductIdIsNumeric() throws Exception {
                String pathVariable = "12345";

                mockMvc.perform(delete("/products/{productId}", pathVariable))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Invalid URL parameter: '" + pathVariable + "' is not a valid format"));

                verifyNoInteractions(productService);
            }

            @Test
            @DisplayName("Should return 400 when productId is an empty string")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenProductIdIsEmpty() throws Exception {
                mockMvc.perform(delete("/products/{productId}", " "))
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
            @WithMockUser(roles = "ADMIN")
            void shouldReturn500_whenServiceThrowsRuntimeException() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                doThrow(new RuntimeException("Database connection lost"))
                        .when(productService).deleteProduct(eq(productId));

                // Act & Assert
                mockMvc.perform(delete(PRODUCT_BY_ID_URL, productId))
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
            @DisplayName("Error response should contain status, message, and timeStamp fields when product not found")
            @WithMockUser(roles = "ADMIN")
            void shouldReturnErrorResponseStructure_whenProductNotFound() throws Exception {
                // Arrange
                UUID productId = UUID.randomUUID();
                doThrow(new ProductNotFoundException("Product not found"))
                        .when(productService).deleteProduct(eq(productId));

                // Act & Assert
                mockMvc.perform(delete(PRODUCT_BY_ID_URL, productId))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").isNotEmpty())
                        .andExpect(jsonPath("$.timeStamp").isNumber());
            }
        }
    }
}
