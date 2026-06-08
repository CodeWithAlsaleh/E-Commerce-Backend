package com.sivan.ecommerce.controller.cart;

import tools.jackson.databind.ObjectMapper;
import com.sivan.ecommerce.config.SecurityConfig;
import com.sivan.ecommerce.dto.cart.CartItemRequestDTO;
import com.sivan.ecommerce.dto.cart.CartItemResponseDTO;
import com.sivan.ecommerce.exception.CustomerNotFoundException;
import com.sivan.ecommerce.exception.InsufficientStockException;
import com.sivan.ecommerce.exception.ProductNotFoundException;
import com.sivan.ecommerce.service.cart.CartItemService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests for {@link CartRestController}.
 *
 * <p>Uses {@code @WebMvcTest} to load only the web layer (controller + security + validation).
 * The {@link CartItemService} is mocked — no database or full Spring context involved.</p>
 *
 * <p>{@code POST /cart/items} requires {@code ROLE_USER} in SecurityConfig,
 * so authentication and authorization tests are included.</p>
 */
@WebMvcTest(CartRestController.class)
@Import(SecurityConfig.class)
@DisplayName("CartRestController")
class CartRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartItemService cartItemService;

    // ======================== Constants ========================

    private static final String CART_ITEMS_URL = "/cart/items";

    private static final UUID VALID_PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final int VALID_QUANTITY = 2;
    private static final String VALID_PRODUCT_TITLE = "Wireless Bluetooth Headphones";

    // ==================== createCartItem() ====================
    @Nested
    @DisplayName("createCartItem()")
    class CreateCartItem {

        // ======================== Helpers ========================

        private CartItemRequestDTO validRequest() {
            return new CartItemRequestDTO(VALID_PRODUCT_ID, VALID_QUANTITY);
        }

        private CartItemResponseDTO validResponse() {
            return new CartItemResponseDTO(VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY);
        }

        // ==================== SUCCESS CASES (201) ====================

        @Nested
        @DisplayName("Success cases — 201 Created")
        class SuccessCases {

            @Test
            @DisplayName("Should return 201 and correct JSON when authenticated USER creates a valid cart item")
            @WithMockUser(roles = "USER")
            void shouldReturn201_whenValidCartItemCreation() throws Exception {
                // Arrange
                when(cartItemService.createCartItem(any(CartItemRequestDTO.class)))
                        .thenReturn(validResponse());

                // Act & Assert
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.productId").value(VALID_PRODUCT_ID.toString()))
                        .andExpect(jsonPath("$.productTitle").value(VALID_PRODUCT_TITLE))
                        .andExpect(jsonPath("$.quantity").value(VALID_QUANTITY));

                verify(cartItemService).createCartItem(any(CartItemRequestDTO.class));
            }

            @Test
            @DisplayName("Should call cartItemService.createCartItem exactly once")
            @WithMockUser(roles = "USER")
            void shouldCallServiceExactlyOnce() throws Exception {
                // Arrange
                when(cartItemService.createCartItem(any(CartItemRequestDTO.class)))
                        .thenReturn(validResponse());

                // Act
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated());

                // Assert
                verify(cartItemService).createCartItem(any(CartItemRequestDTO.class));
                verifyNoMoreInteractions(cartItemService);
            }

            @Test
            @DisplayName("Should return 201 when quantity is exactly 1 (minimum valid value)")
            @WithMockUser(roles = "USER")
            void shouldReturn201_whenQuantityIsExactlyOne() throws Exception {
                // Arrange
                CartItemRequestDTO request = new CartItemRequestDTO(VALID_PRODUCT_ID, 1);
                CartItemResponseDTO response = new CartItemResponseDTO(VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, 1);
                when(cartItemService.createCartItem(any(CartItemRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.quantity").value(1));
            }
        }

        // ==================== AUTHENTICATION FAILURES (401) ====================

        @Nested
        @DisplayName("Authentication failures — 401 Unauthorized")
        class AuthenticationFailures {

            @Test
            @DisplayName("Should return 401 when no credentials are provided (anonymous)")
            void shouldReturn401_whenNoCredentials() throws Exception {
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isUnauthorized());

                verifyNoInteractions(cartItemService);
            }

            @Test
            @DisplayName("Should return 401 when invalid credentials are provided")
            void shouldReturn401_whenInvalidCredentials() throws Exception {
                mockMvc.perform(post(CART_ITEMS_URL)
                                .with(httpBasic("wrong@email.com", "WrongPassword1!"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isUnauthorized());

                verifyNoInteractions(cartItemService);
            }
        }

        // ==================== AUTHORIZATION FAILURES (403) ====================

        @Nested
        @DisplayName("Authorization failures — 403 Forbidden")
        class AuthorizationFailures {

            @Test
            @DisplayName("Should return 403 when authenticated user has ROLE_SYSTEM (not USER)")
            @WithMockUser(roles = "SYSTEM")
            void shouldReturn403_whenRoleIsAdmin() throws Exception {
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(cartItemService);
            }

            @Test
            @DisplayName("Should return 403 when user has no roles at all")
            @WithMockUser(roles = {})
            void shouldReturn403_whenUserHasNoRoles() throws Exception {
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(cartItemService);
            }
        }

        // ==================== VALIDATION FAILURES (400) ====================

        @Nested
        @DisplayName("Validation failures — 400 Bad Request")
        class ValidationFailures {

            // ---------- productId ----------

            @Test
            @DisplayName("Should return 400 when productId is null")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenProductIdIsNull() throws Exception {
                CartItemRequestDTO request = new CartItemRequestDTO(null, VALID_QUANTITY);

                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(cartItemService);
            }

            @Test
            @DisplayName("Should return 400 when productId is an invalid UUID format")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenProductIdIsInvalidUUID() throws Exception {
                String badJson = """
                        {
                            "productId": "not-a-valid-uuid",
                            "quantity": 2
                        }
                        """;

                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badJson))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(cartItemService);
            }

            // ---------- quantity ----------

            @Test
            @DisplayName("Should return 400 when quantity is null")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenQuantityIsNull() throws Exception {
                CartItemRequestDTO request = new CartItemRequestDTO(VALID_PRODUCT_ID, null);

                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(cartItemService);
            }

            @Test
            @DisplayName("Should return 400 when quantity is zero (below @Min(1))")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenQuantityIsZero() throws Exception {
                CartItemRequestDTO request = new CartItemRequestDTO(VALID_PRODUCT_ID, 0);

                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(cartItemService);
            }

            @Test
            @DisplayName("Should return 400 when quantity is negative")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenQuantityIsNegative() throws Exception {
                CartItemRequestDTO request = new CartItemRequestDTO(VALID_PRODUCT_ID, -5);

                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(cartItemService);
            }
        }

        // ==================== NOT FOUND CASES (404) ====================

        @Nested
        @DisplayName("Not found cases — 404 Not Found")
        class NotFoundCases {

            @Test
            @DisplayName("Should return 404 when product does not exist")
            @WithMockUser(roles = "USER")
            void shouldReturn404_whenProductNotFound() throws Exception {
                // Arrange
                when(cartItemService.createCartItem(any(CartItemRequestDTO.class)))
                        .thenThrow(new ProductNotFoundException("Product not found"));

                // Act & Assert
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").value("Product not found"));
            }

            @Test
            @DisplayName("Should return 404 when customer profile is not found in database")
            @WithMockUser(roles = "USER")
            void shouldReturn404_whenCustomerNotFound() throws Exception {
                // Arrange
                when(cartItemService.createCartItem(any(CartItemRequestDTO.class)))
                        .thenThrow(new CustomerNotFoundException("Profile not found"));

                // Act & Assert
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").value("Profile not found"));
            }
        }

        // ==================== CONFLICT CASES (409) ====================

        @Nested
        @DisplayName("Conflict cases — 409 Conflict")
        class ConflictCases {

            @Test
            @DisplayName("Should return 409 when requested quantity exceeds available stock")
            @WithMockUser(roles = "USER")
            void shouldReturn409_whenInsufficientStock() throws Exception {
                // Arrange
                when(cartItemService.createCartItem(any(CartItemRequestDTO.class)))
                        .thenThrow(new InsufficientStockException("Requested quantity is not available in stock"));

                // Act & Assert
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.status").value(409))
                        .andExpect(jsonPath("$.message").value("Requested quantity is not available in stock"));
            }
        }

        // ==================== SERVICE EXCEPTION HANDLING ====================

        @Nested
        @DisplayName("Service exception handling")
        class ServiceExceptionHandling {

            @Test
            @DisplayName("Should return 500 when service throws an unexpected RuntimeException")
            @WithMockUser(roles = "USER")
            void shouldReturn500_whenServiceThrowsRuntimeException() throws Exception {
                // Arrange
                when(cartItemService.createCartItem(any(CartItemRequestDTO.class)))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.status").value(500))
                        .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
            }

            @Test
            @DisplayName("Should return 500 when service throws an unexpected IllegalStateException")
            @WithMockUser(roles = "USER")
            void shouldReturn500_whenServiceThrowsIllegalStateException() throws Exception {
                // Arrange
                when(cartItemService.createCartItem(any(CartItemRequestDTO.class)))
                        .thenThrow(new IllegalStateException("Database connection lost"));

                // Act & Assert
                mockMvc.perform(post(CART_ITEMS_URL)
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
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenJsonIsMalformed() throws Exception {
                String malformedJson = "{ \"productId\": \"11111111-1111-1111-1111-111111111111\", \"quantity\": }";

                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(malformedJson))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(cartItemService);
            }

            @Test
            @DisplayName("Should return 400 when request body is missing entirely")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenRequestBodyIsMissing() throws Exception {
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(cartItemService);
            }

            @Test
            @DisplayName("Should return 400 when request body is an empty JSON object")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenRequestBodyIsEmptyObject() throws Exception {
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(cartItemService);
            }

            @Test
            @DisplayName("Should return 400 when quantity is a string instead of a number")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenQuantityIsWrongType() throws Exception {
                String badJson = """
                        {
                            "productId": "11111111-1111-1111-1111-111111111111",
                            "quantity": "not-a-number"
                        }
                        """;

                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badJson))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(cartItemService);
            }

            @Test
            @DisplayName("Should return 400 when productId is a number instead of a UUID string")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenProductIdIsWrongType() throws Exception {
                String badJson = """
                        {
                            "productId": 12345,
                            "quantity": 2
                        }
                        """;

                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badJson))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(cartItemService);
            }
        }

        // ==================== JSON RESPONSE STRUCTURE ====================

        @Nested
        @DisplayName("JSON response structure validation")
        class JsonResponseStructure {

            @Test
            @DisplayName("Should return all expected fields in the success response")
            @WithMockUser(roles = "USER")
            void shouldReturnAllFieldsInResponse() throws Exception {
                // Arrange
                when(cartItemService.createCartItem(any(CartItemRequestDTO.class)))
                        .thenReturn(validResponse());

                // Act & Assert
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.productId").exists())
                        .andExpect(jsonPath("$.productTitle").exists())
                        .andExpect(jsonPath("$.quantity").exists());
            }

            @Test
            @DisplayName("Error response should contain status, message, and timeStamp fields")
            @WithMockUser(roles = "USER")
            void shouldReturnErrorResponseStructure() throws Exception {
                // Arrange — trigger a validation error
                CartItemRequestDTO request = new CartItemRequestDTO(null, VALID_QUANTITY);

                // Act & Assert
                mockMvc.perform(post(CART_ITEMS_URL)
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
            @DisplayName("Should return 201 when quantity is a large valid number (Integer.MAX_VALUE)")
            @WithMockUser(roles = "USER")
            void shouldReturn201_whenQuantityIsVeryLarge() throws Exception {
                // Arrange
                CartItemRequestDTO request = new CartItemRequestDTO(VALID_PRODUCT_ID, Integer.MAX_VALUE);
                CartItemResponseDTO response = new CartItemResponseDTO(
                        VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, Integer.MAX_VALUE
                );
                when(cartItemService.createCartItem(any(CartItemRequestDTO.class))).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.quantity").value(Integer.MAX_VALUE));
            }

            @Test
            @DisplayName("Should ignore unknown fields in the request body and still succeed")
            @WithMockUser(roles = "USER")
            void shouldIgnoreUnknownFieldsInRequest() throws Exception {
                // Arrange
                when(cartItemService.createCartItem(any(CartItemRequestDTO.class)))
                        .thenReturn(validResponse());

                String jsonWithExtraFields = """
                        {
                            "productId": "11111111-1111-1111-1111-111111111111",
                            "quantity": 2,
                            "unknownField": "should be ignored"
                        }
                        """;

                // Act & Assert
                mockMvc.perform(post(CART_ITEMS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonWithExtraFields))
                        .andExpect(status().isCreated());
            }
        }
    }
}
