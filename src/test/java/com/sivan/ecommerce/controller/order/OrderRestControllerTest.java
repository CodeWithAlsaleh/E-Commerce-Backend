package com.sivan.ecommerce.controller.order;

import com.sivan.ecommerce.config.SecurityConfig;
import com.sivan.ecommerce.dto.order.OrderItemResponseDTO;
import com.sivan.ecommerce.dto.order.OrderRequestDTO;
import com.sivan.ecommerce.dto.order.OrderResponseDTO;
import com.sivan.ecommerce.entity.order.Status;
import com.sivan.ecommerce.exception.CustomerNotFoundException;
import com.sivan.ecommerce.exception.InsufficientStockException;
import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.exception.ResourceConflictException;
import com.sivan.ecommerce.service.order.OrderService;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests for {@link OrderRestController}.
 *
 * <p>Uses {@code @WebMvcTest} to load only the web layer (controller + security + validation).
 * The {@link OrderService} is mocked — no database or full Spring context involved.</p>
 *
 * <p>{@code POST /orders} requires {@code ROLE_USER} in SecurityConfig,
 * so authentication and authorization tests are included.</p>
 *
 * <p>The endpoint also requires an {@code Idempotency-Key} request header
 * annotated with {@code @NotBlank} (enabled by {@code @Validated} on the controller),
 * so header-level validation tests are included.</p>
 */
@WebMvcTest(OrderRestController.class)
@Import(SecurityConfig.class)
@DisplayName("OrderRestController")
class OrderRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    // ======================== Constants ========================

    private static final String ORDERS_URL = "/orders";

    private static final String VALID_IDEMPOTENCY_KEY = "idem-key-12345";
    private static final String VALID_SHIPPING_ADDRESS = "123 Main St, New York, NY 10001";

    private static final UUID VALID_ORDER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID VALID_PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final long VALID_TOTAL_PRICE = 7500L;
    private static final int VALID_QUANTITY = 3;
    private static final long VALID_LOCKED_PRICE = 2500L;
    private static final String VALID_PRODUCT_TITLE = "Wireless Headphones";

    // ==================== placeOrder() ====================
    @Nested
    @DisplayName("placeOrder()")
    class PlaceOrder {

        // ======================== Helpers ========================

        private OrderRequestDTO validRequest() {
            return new OrderRequestDTO(VALID_SHIPPING_ADDRESS);
        }

        private OrderResponseDTO validResponse() {
            OrderItemResponseDTO orderItem = new OrderItemResponseDTO(
                    VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY, VALID_LOCKED_PRICE
            );
            return new OrderResponseDTO(
                    VALID_ORDER_ID, Status.PENDING, VALID_TOTAL_PRICE,
                    VALID_SHIPPING_ADDRESS, Instant.now(), Set.of(orderItem)
            );
        }

        // ==================== SUCCESS CASES (201) ====================

        @Nested
        @DisplayName("Success cases — 201 Created")
        class SuccessCases {

            @Test
            @DisplayName("Should return 201 and correct JSON when authenticated USER places a valid order")
            @WithMockUser(roles = "USER")
            void shouldReturn201_whenValidOrderPlacement() throws Exception {
                // Arrange
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenReturn(validResponse());

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").value(VALID_ORDER_ID.toString()))
                        .andExpect(jsonPath("$.status").value("PENDING"))
                        .andExpect(jsonPath("$.totalPrice").value(VALID_TOTAL_PRICE))
                        .andExpect(jsonPath("$.shippingAddress").value(VALID_SHIPPING_ADDRESS))
                        .andExpect(jsonPath("$.orderItems").isArray())
                        .andExpect(jsonPath("$.orderItems[0].productId").value(VALID_PRODUCT_ID.toString()))
                        .andExpect(jsonPath("$.orderItems[0].productTitle").value(VALID_PRODUCT_TITLE))
                        .andExpect(jsonPath("$.orderItems[0].quantity").value(VALID_QUANTITY))
                        .andExpect(jsonPath("$.orderItems[0].lockedPrice").value(VALID_LOCKED_PRICE));

                verify(orderService).placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class));
            }

            @Test
            @DisplayName("Should call orderService.placeOrder exactly once")
            @WithMockUser(roles = "USER")
            void shouldCallServiceExactlyOnce() throws Exception {
                // Arrange
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenReturn(validResponse());

                // Act
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated());

                // Assert
                verify(orderService).placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class));
                verifyNoMoreInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 201 when idempotency key is returned from an existing order (idempotent replay)")
            @WithMockUser(roles = {"USER", "ADMIN"})
            void shouldReturn201_whenIdempotentReplay() throws Exception {
                // Arrange — service returns an existing order on duplicate key
                OrderResponseDTO existingOrderResponse = new OrderResponseDTO(
                        VALID_ORDER_ID, Status.PENDING, VALID_TOTAL_PRICE,
                        VALID_SHIPPING_ADDRESS, Instant.now(),
                        Set.of(new OrderItemResponseDTO(VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY, VALID_LOCKED_PRICE))
                );
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenReturn(existingOrderResponse);

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").value(VALID_ORDER_ID.toString()));
            }
        }

        // ==================== AUTHENTICATION FAILURES (401) ====================

        @Nested
        @DisplayName("Authentication failures — 401 Unauthorized")
        class AuthenticationFailures {

            @Test
            @DisplayName("Should return 401 when no credentials are provided (anonymous)")
            void shouldReturn401_whenNoCredentials() throws Exception {
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isUnauthorized());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 401 when invalid credentials are provided")
            void shouldReturn401_whenInvalidCredentials() throws Exception {
                mockMvc.perform(post(ORDERS_URL)
                                .with(httpBasic("wrong@email.com", "WrongPassword1!"))
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isUnauthorized());

                verifyNoInteractions(orderService);
            }
        }

        // ==================== AUTHORIZATION FAILURES (403) ====================

        @Nested
        @DisplayName("Authorization failures — 403 Forbidden")
        class AuthorizationFailures {

            @Test
            @DisplayName("Should return 403 when authenticated user has ROLE_SYSTEM (not USER)")
            @WithMockUser(roles = "SYSTEM")
            void shouldReturn403_whenRoleIsSystem() throws Exception {
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 403 when user has no roles at all")
            @WithMockUser(roles = {})
            void shouldReturn403_whenUserHasNoRoles() throws Exception {
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
            }
        }

        // ==================== VALIDATION FAILURES — Idempotency-Key header (400) ====================

        @Nested
        @DisplayName("Validation failures — Idempotency-Key header — 400 Bad Request")
        class IdempotencyKeyValidationFailures {

            @Test
            @DisplayName("Should return 400 when Idempotency-Key header is missing entirely")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenIdempotencyKeyHeaderIsMissing() throws Exception {
                mockMvc.perform(post(ORDERS_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when Idempotency-Key header is blank (empty string)")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenIdempotencyKeyIsBlank() throws Exception {
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", "")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when Idempotency-Key header is whitespace-only")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenIdempotencyKeyIsWhitespaceOnly() throws Exception {
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", "   ")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(orderService);
            }
        }

        // ==================== VALIDATION FAILURES — Request body (400) ====================

        @Nested
        @DisplayName("Validation failures — Request body — 400 Bad Request")
        class RequestBodyValidationFailures {

            // ---------- shippingAddress ----------

            @Test
            @DisplayName("Should return 400 when shippingAddress is null")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenShippingAddressIsNull() throws Exception {
                String jsonWithNullAddress = """
                        {
                            "shippingAddress": null
                        }
                        """;

                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonWithNullAddress))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when shippingAddress is blank (empty string)")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenShippingAddressIsBlank() throws Exception {
                OrderRequestDTO request = new OrderRequestDTO("");

                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when shippingAddress is whitespace-only")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenShippingAddressIsWhitespaceOnly() throws Exception {
                OrderRequestDTO request = new OrderRequestDTO("   ");

                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when shippingAddress exceeds 512 characters")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenShippingAddressExceedsMaxLength() throws Exception {
                String longAddress = "A".repeat(513);
                OrderRequestDTO request = new OrderRequestDTO(longAddress);

                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 201 when shippingAddress is exactly 512 characters (boundary)")
            @WithMockUser(roles = "USER")
            void shouldReturn201_whenShippingAddressIsExactly512Characters() throws Exception {
                // Arrange
                String maxAddress = "A".repeat(512);
                OrderRequestDTO request = new OrderRequestDTO(maxAddress);

                OrderResponseDTO response = new OrderResponseDTO(
                        VALID_ORDER_ID, Status.PENDING, VALID_TOTAL_PRICE,
                        maxAddress, Instant.now(),
                        Set.of(new OrderItemResponseDTO(VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY, VALID_LOCKED_PRICE))
                );
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.shippingAddress").value(maxAddress));
            }
        }

        // ==================== NOT FOUND CASES (404) ====================

        @Nested
        @DisplayName("Not found cases — 404 Not Found")
        class NotFoundCases {

            @Test
            @DisplayName("Should return 404 when customer profile is not found in database")
            @WithMockUser(roles = "USER")
            void shouldReturn404_whenCustomerNotFound() throws Exception {
                // Arrange
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenThrow(new CustomerNotFoundException("Profile not found"));

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").value("Profile not found"));
            }
        }

        // ==================== BUSINESS VALIDATION FAILURES — InvalidDataException (400) ====================

        @Nested
        @DisplayName("Business validation failures — 400 Bad Request (InvalidDataException)")
        class BusinessValidationFailures {

            @Test
            @DisplayName("Should return 400 when service throws InvalidDataException for empty cart")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenCartIsEmpty() throws Exception {
                // Arrange
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenThrow(new InvalidDataException("Cannot place an order with an empty cart"));

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Cannot place an order with an empty cart"));
            }
        }

        // ==================== CONFLICT CASES (409) ====================

        @Nested
        @DisplayName("Conflict cases — 409 Conflict")
        class ConflictCases {

            @Test
            @DisplayName("Should return 409 when requested quantity exceeds available stock (InsufficientStockException)")
            @WithMockUser(roles = "USER")
            void shouldReturn409_whenInsufficientStock() throws Exception {
                // Arrange
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenThrow(new InsufficientStockException("Wireless Headphones has insufficient stock. Only 2 left"));

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.status").value(409))
                        .andExpect(jsonPath("$.message").value("Wireless Headphones has insufficient stock. Only 2 left"));
            }

            @Test
            @DisplayName("Should return 409 when a race condition causes a ResourceConflictException on save")
            @WithMockUser(roles = "USER")
            void shouldReturn409_whenRaceConditionOccurs() throws Exception {
                // Arrange
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenThrow(new ResourceConflictException("Order is currently processing. Please refresh"));

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.status").value(409))
                        .andExpect(jsonPath("$.message").value("Order is currently processing. Please refresh"));
            }

            @Test
            @DisplayName("Should return 409 when optimistic locking failure occurs (ResourceConflictException)")
            @WithMockUser(roles = "USER")
            void shouldReturn409_whenOptimisticLockingFailure() throws Exception {
                // Arrange
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenThrow(new ResourceConflictException("Inventory was updated by another user. Please try again"));

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.status").value(409))
                        .andExpect(jsonPath("$.message").value("Inventory was updated by another user. Please try again"));
            }
        }

        // ==================== SERVICE EXCEPTION HANDLING (500) ====================

        @Nested
        @DisplayName("Service exception handling — 500 Internal Server Error")
        class ServiceExceptionHandling {

            @Test
            @DisplayName("Should return 500 when service throws an unexpected RuntimeException")
            @WithMockUser(roles = "USER")
            void shouldReturn500_whenServiceThrowsRuntimeException() throws Exception {
                // Arrange
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
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
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenThrow(new IllegalStateException("Unexpected internal state"));

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.status").value(500))
                        .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
            }
        }

        // ==================== MALFORMED INPUT (400) ====================

        @Nested
        @DisplayName("Malformed input — 400 Bad Request")
        class MalformedInput {

            @Test
            @DisplayName("Should return 400 when request body is malformed JSON")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenJsonIsMalformed() throws Exception {
                String malformedJson = "{ \"shippingAddress\": ";

                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(malformedJson))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when request body is missing entirely")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenRequestBodyIsMissing() throws Exception {
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when request body is an empty JSON object")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenRequestBodyIsEmptyObject() throws Exception {
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when shippingAddress is the wrong type (e.g., an object)")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenShippingAddressIsWrongType() throws Exception {
                String badJson = """
                        {
                            "shippingAddress": {}
                        }
                        """;

                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badJson))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(orderService);
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
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenReturn(validResponse());

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").exists())
                        .andExpect(jsonPath("$.status").exists())
                        .andExpect(jsonPath("$.totalPrice").exists())
                        .andExpect(jsonPath("$.shippingAddress").exists())
                        .andExpect(jsonPath("$.createdAt").exists())
                        .andExpect(jsonPath("$.orderItems").exists())
                        .andExpect(jsonPath("$.orderItems").isArray());
            }

            @Test
            @DisplayName("Should return order item fields in the response")
            @WithMockUser(roles = "USER")
            void shouldReturnOrderItemFieldsInResponse() throws Exception {
                // Arrange
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenReturn(validResponse());

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.orderItems[0].productId").exists())
                        .andExpect(jsonPath("$.orderItems[0].productTitle").exists())
                        .andExpect(jsonPath("$.orderItems[0].quantity").exists())
                        .andExpect(jsonPath("$.orderItems[0].lockedPrice").exists());
            }

            @Test
            @DisplayName("Error response should contain status, message, and timeStamp fields")
            @WithMockUser(roles = "USER")
            void shouldReturnErrorResponseStructure() throws Exception {
                // Arrange — trigger a business validation error
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenThrow(new InvalidDataException("Cannot place an order with an empty cart"));

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
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
            @DisplayName("Should ignore unknown fields in the request body and still succeed")
            @WithMockUser(roles = "USER")
            void shouldIgnoreUnknownFieldsInRequest() throws Exception {
                // Arrange
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenReturn(validResponse());

                String jsonWithExtraFields = """
                        {
                            "shippingAddress": "123 Main St, New York, NY 10001",
                            "unknownField": "should be ignored",
                            "anotherExtra": 42
                        }
                        """;

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonWithExtraFields))
                        .andExpect(status().isCreated());
            }

            @Test
            @DisplayName("Should return 201 when shippingAddress contains special characters")
            @WithMockUser(roles = "USER")
            void shouldReturn201_whenShippingAddressContainsSpecialCharacters() throws Exception {
                // Arrange
                String specialAddress = "Apt #42, 123 O'Brien St, São Paulo — Brazil (南京)";
                OrderRequestDTO request = new OrderRequestDTO(specialAddress);

                OrderResponseDTO response = new OrderResponseDTO(
                        VALID_ORDER_ID, Status.PENDING, VALID_TOTAL_PRICE,
                        specialAddress, Instant.now(),
                        Set.of(new OrderItemResponseDTO(VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY, VALID_LOCKED_PRICE))
                );
                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.shippingAddress").value(specialAddress));
            }

            @Test
            @DisplayName("Should return 201 when idempotency key is a UUID-style string")
            @WithMockUser(roles = "USER")
            void shouldReturn201_whenIdempotencyKeyIsUUID() throws Exception {
                // Arrange
                String uuidKey = UUID.randomUUID().toString();
                when(orderService.placeOrder(eq(uuidKey), any(OrderRequestDTO.class)))
                        .thenReturn(validResponse());

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", uuidKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated());
            }

            @Test
            @DisplayName("Should return 201 when response contains multiple order items")
            @WithMockUser(roles = "USER")
            void shouldReturn201_whenResponseContainsMultipleOrderItems() throws Exception {
                // Arrange
                UUID productId2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

                OrderItemResponseDTO item1 = new OrderItemResponseDTO(
                        VALID_PRODUCT_ID, "Wireless Headphones", 2, 2500L
                );
                OrderItemResponseDTO item2 = new OrderItemResponseDTO(
                        productId2, "Mechanical Keyboard", 1, 8000L
                );
                OrderResponseDTO response = new OrderResponseDTO(
                        VALID_ORDER_ID, Status.PENDING, 13000L,
                        VALID_SHIPPING_ADDRESS, Instant.now(), Set.of(item1, item2)
                );

                when(orderService.placeOrder(eq(VALID_IDEMPOTENCY_KEY), any(OrderRequestDTO.class)))
                        .thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(ORDERS_URL)
                                .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.totalPrice").value(13000L))
                        .andExpect(jsonPath("$.orderItems.length()").value(2));
            }
        }
    }
}
