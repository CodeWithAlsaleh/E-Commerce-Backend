package com.sivan.ecommerce.controller.order;

import com.sivan.ecommerce.config.SecurityConfig;
import com.sivan.ecommerce.dto.order.OrderFilterDTO;
import com.sivan.ecommerce.dto.order.OrderItemResponseDTO;
import com.sivan.ecommerce.dto.order.OrderRequestDTO;
import com.sivan.ecommerce.dto.order.OrderResponseDTO;
import com.sivan.ecommerce.dto.order.OrderSummaryResponseDTO;
import com.sivan.ecommerce.entity.order.Status;
import com.sivan.ecommerce.exception.CustomerNotFoundException;
import com.sivan.ecommerce.exception.InsufficientStockException;
import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.exception.OrderNotFoundException;
import com.sivan.ecommerce.exception.OrderStateConflictException;
import com.sivan.ecommerce.exception.ResourceConflictException;
import com.sivan.ecommerce.service.order.OrderService;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                    VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now(), Set.of(orderItem)
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
                        VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now(),
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
                        maxAddress, Instant.now(), Instant.now(),
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
                        .andExpect(jsonPath("$.updatedAt").exists())
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
                        specialAddress, Instant.now(), Instant.now(),
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
                        VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now(), Set.of(item1, item2)
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

    // ==================== getOrders() ====================
    @Nested
    @DisplayName("getOrders()")
    class GetOrders {

        // ======================== Constants ========================

        private static final UUID ORDER_ID_1 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        private static final UUID ORDER_ID_2 = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        private static final UUID ORDER_ID_3 = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        // ======================== Helpers ========================

        private OrderSummaryResponseDTO buildSummary(UUID id, Status status, long totalPrice) {
            return new OrderSummaryResponseDTO(id, status, totalPrice, VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now());
        }

        private Page<OrderSummaryResponseDTO> singleOrderPage() {
            OrderSummaryResponseDTO summary = buildSummary(ORDER_ID_1, Status.PENDING, VALID_TOTAL_PRICE);
            return new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1);
        }

        private Page<OrderSummaryResponseDTO> emptyPage() {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        }

        // ==================== SUCCESS CASES (200) ====================

        @Nested
        @DisplayName("Success cases — 200 OK")
        class SuccessCases {

            @Test
            @DisplayName("Should return 200 and paginated orders when authenticated USER requests orders")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenValidRequest() throws Exception {
                // Arrange
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleOrderPage());

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray())
                        .andExpect(jsonPath("$.content", hasSize(1)))
                        .andExpect(jsonPath("$.content[0].id").value(ORDER_ID_1.toString()))
                        .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                        .andExpect(jsonPath("$.content[0].totalPrice").value(VALID_TOTAL_PRICE))
                        .andExpect(jsonPath("$.content[0].shippingAddress").value(VALID_SHIPPING_ADDRESS))
                        .andExpect(jsonPath("$.content[0].createdAt").exists())
                        .andExpect(jsonPath("$.content[0].updatedAt").exists());

                verify(orderService).getOrders(any(OrderFilterDTO.class), any(Pageable.class));
            }

            @Test
            @DisplayName("Should call orderService.getOrders exactly once")
            @WithMockUser(roles = "USER")
            void shouldCallServiceExactlyOnce() throws Exception {
                // Arrange
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleOrderPage());

                // Act
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isOk());

                // Assert
                verify(orderService).getOrders(any(OrderFilterDTO.class), any(Pageable.class));
                verifyNoMoreInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 200 with empty content when no orders exist")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenNoOrdersExist() throws Exception {
                // Arrange
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(emptyPage());

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray())
                        .andExpect(jsonPath("$.content", hasSize(0)))
                        .andExpect(jsonPath("$.totalElements").value(0));
            }

            @Test
            @DisplayName("Should return 200 and pass pagination parameters correctly")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenPaginationParametersProvided() throws Exception {
                // Arrange
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleOrderPage());

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL)
                                .param("page", "0")
                                .param("size", "10"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());

                verify(orderService).getOrders(any(OrderFilterDTO.class), any(Pageable.class));
            }

            @Test
            @DisplayName("Should return 200 when filtering by status PENDING")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenFilteringByStatusPending() throws Exception {
                // Arrange
                OrderSummaryResponseDTO pendingSummary = buildSummary(ORDER_ID_1, Status.PENDING, VALID_TOTAL_PRICE);
                Page<OrderSummaryResponseDTO> pendingPage = new PageImpl<>(List.of(pendingSummary), PageRequest.of(0, 20), 1);

                OrderFilterDTO expectedFilter = new OrderFilterDTO(Status.PENDING);

                when(orderService.getOrders(eq(expectedFilter), any(Pageable.class)))
                        .thenReturn(pendingPage);

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL)
                                .param("status", "PENDING"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(1)))
                        .andExpect(jsonPath("$.content[0].status").value("PENDING"));

                verify(orderService).getOrders(eq(expectedFilter), any(Pageable.class));
            }

            @Test
            @DisplayName("Should return 200 when filtering by status DELIVERED")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenFilteringByStatusDelivered() throws Exception {
                // Arrange
                OrderSummaryResponseDTO deliveredSummary = buildSummary(ORDER_ID_1, Status.DELIVERED, VALID_TOTAL_PRICE);
                Page<OrderSummaryResponseDTO> deliveredPage = new PageImpl<>(List.of(deliveredSummary), PageRequest.of(0, 20), 1);

                OrderFilterDTO expectedFilter = new OrderFilterDTO(Status.DELIVERED);

                when(orderService.getOrders(eq(expectedFilter), any(Pageable.class)))
                        .thenReturn(deliveredPage);

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL)
                                .param("status", "DELIVERED"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(1)))
                        .andExpect(jsonPath("$.content[0].status").value("DELIVERED"));

                verify(orderService).getOrders(eq(expectedFilter), any(Pageable.class));
            }

            @Test
            @DisplayName("Should return 200 when filtering by status CANCELED")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenFilteringByStatusCanceled() throws Exception {
                // Arrange
                OrderSummaryResponseDTO canceledSummary = buildSummary(ORDER_ID_1, Status.CANCELED, VALID_TOTAL_PRICE);
                Page<OrderSummaryResponseDTO> canceledPage = new PageImpl<>(List.of(canceledSummary), PageRequest.of(0, 20), 1);

                OrderFilterDTO expectedFilter = new OrderFilterDTO(Status.CANCELED);

                when(orderService.getOrders(eq(expectedFilter), any(Pageable.class)))
                        .thenReturn(canceledPage);

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL)
                                .param("status", "CANCELED"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(1)))
                        .andExpect(jsonPath("$.content[0].status").value("CANCELED"));

                verify(orderService).getOrders(eq(expectedFilter), any(Pageable.class));
            }

            @Test
            @DisplayName("Should return 200 when filtering by status SHIPPED")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenFilteringByStatusShipped() throws Exception {
                // Arrange
                OrderSummaryResponseDTO shippedSummary = buildSummary(ORDER_ID_1, Status.SHIPPED, VALID_TOTAL_PRICE);
                Page<OrderSummaryResponseDTO> shippedPage = new PageImpl<>(List.of(shippedSummary), PageRequest.of(0, 20), 1);

                OrderFilterDTO expectedFilter = new OrderFilterDTO(Status.SHIPPED);

                when(orderService.getOrders(eq(expectedFilter), any(Pageable.class)))
                        .thenReturn(shippedPage);

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL)
                                .param("status", "SHIPPED"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(1)))
                        .andExpect(jsonPath("$.content[0].status").value("SHIPPED"));

                verify(orderService).getOrders(eq(expectedFilter), any(Pageable.class));
            }

            @Test
            @DisplayName("Should return 200 with multiple orders in the page")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenMultipleOrdersExist() throws Exception {
                // Arrange
                OrderSummaryResponseDTO summary1 = buildSummary(ORDER_ID_1, Status.PENDING, 7500L);
                OrderSummaryResponseDTO summary2 = buildSummary(ORDER_ID_2, Status.SHIPPED, 12000L);
                OrderSummaryResponseDTO summary3 = buildSummary(ORDER_ID_3, Status.DELIVERED, 3500L);

                Page<OrderSummaryResponseDTO> multiPage =
                        new PageImpl<>(List.of(summary1, summary2, summary3), PageRequest.of(0, 20), 3);

                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(multiPage);

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(3)))
                        .andExpect(jsonPath("$.totalElements").value(3))
                        .andExpect(jsonPath("$.content[0].id").value(ORDER_ID_1.toString()))
                        .andExpect(jsonPath("$.content[1].id").value(ORDER_ID_2.toString()))
                        .andExpect(jsonPath("$.content[2].id").value(ORDER_ID_3.toString()));
            }

            @Test
            @DisplayName("Should return 200 when sorting by createdAt")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenSortingByCreatedAt() throws Exception {
                // Arrange
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleOrderPage());

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL)
                                .param("sort", "createdAt,desc"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());

                verify(orderService).getOrders(any(OrderFilterDTO.class), any(Pageable.class));
            }

            @Test
            @DisplayName("Should return 200 when sorting by totalPrice")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenSortingByTotalPrice() throws Exception {
                // Arrange
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleOrderPage());

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL)
                                .param("sort", "totalPrice,asc"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());

                verify(orderService).getOrders(any(OrderFilterDTO.class), any(Pageable.class));
            }

            @Test
            @DisplayName("Should return 200 when ADMIN role accesses orders (ADMIN has USER permissions implicitly)")
            @WithMockUser(roles = {"USER", "ADMIN"})
            void shouldReturn200_whenAdminWithUserRole() throws Exception {
                // Arrange
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleOrderPage());

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());
            }
        }

        // ==================== AUTHENTICATION FAILURES (401) ====================

        @Nested
        @DisplayName("Authentication failures — 401 Unauthorized")
        class AuthenticationFailures {

            @Test
            @DisplayName("Should return 401 when no credentials are provided (anonymous)")
            void shouldReturn401_whenNoCredentials() throws Exception {
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isUnauthorized());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 401 when invalid credentials are provided")
            void shouldReturn401_whenInvalidCredentials() throws Exception {
                mockMvc.perform(get(ORDERS_URL)
                                .with(httpBasic("wrong@email.com", "WrongPassword1!")))
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
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 403 when authenticated user has ROLE_ADMIN only (without USER)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn403_whenRoleIsAdminOnly() throws Exception {
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 403 when user has no roles at all")
            @WithMockUser(roles = {})
            void shouldReturn403_whenUserHasNoRoles() throws Exception {
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
            }
        }

        // ==================== VALIDATION FAILURES — Query parameters (400) ====================

        @Nested
        @DisplayName("Validation failures — Query parameters — 400 Bad Request")
        class QueryParameterValidationFailures {

            @Test
            @DisplayName("Should return 400 when status query parameter is an invalid enum value")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenStatusIsInvalidEnum() throws Exception {
                mockMvc.perform(get(ORDERS_URL)
                                .param("status", "INVALID_STATUS"))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when service rejects disallowed sort field")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenSortFieldIsNotAllowed() throws Exception {
                // Arrange — service throws InvalidDataException for disallowed sort
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenThrow(new InvalidDataException("Sorting by 'email' is not allowed"));

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL)
                                .param("sort", "email,asc"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").value("Sorting by 'email' is not allowed"));
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
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenThrow(new CustomerNotFoundException("Profile not found"));

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").value("Profile not found"));
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
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.status").value(500))
                        .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
            }

            @Test
            @DisplayName("Should return 500 when service throws an unexpected IllegalStateException")
            @WithMockUser(roles = "USER")
            void shouldReturn500_whenServiceThrowsIllegalStateException() throws Exception {
                // Arrange
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenThrow(new IllegalStateException("Unexpected internal state"));

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL))
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
            @DisplayName("Should return all expected fields in a paginated success response")
            @WithMockUser(roles = "USER")
            void shouldReturnAllPaginationFieldsInResponse() throws Exception {
                // Arrange
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleOrderPage());

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray())
                        .andExpect(jsonPath("$.totalElements").isNumber())
                        .andExpect(jsonPath("$.totalPages").isNumber())
                        .andExpect(jsonPath("$.size").isNumber())
                        .andExpect(jsonPath("$.number").isNumber());
            }

            @Test
            @DisplayName("Should return all expected order summary fields in the content array")
            @WithMockUser(roles = "USER")
            void shouldReturnAllOrderSummaryFields() throws Exception {
                // Arrange
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleOrderPage());

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content[0].id").exists())
                        .andExpect(jsonPath("$.content[0].status").exists())
                        .andExpect(jsonPath("$.content[0].totalPrice").exists())
                        .andExpect(jsonPath("$.content[0].shippingAddress").exists())
                        .andExpect(jsonPath("$.content[0].createdAt").exists())
                        .andExpect(jsonPath("$.content[0].updatedAt").exists());
            }

            @Test
            @DisplayName("Error response should contain status, message, and timeStamp fields")
            @WithMockUser(roles = "USER")
            void shouldReturnErrorResponseStructure() throws Exception {
                // Arrange — trigger a business validation error
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenThrow(new InvalidDataException("Sorting by 'badField' is not allowed"));

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL))
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
            @DisplayName("Should return 200 when no status filter is provided (null status)")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenNoStatusFilterProvided() throws Exception {
                // Arrange
                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(singleOrderPage());

                // Act & Assert — no ?status= parameter at all
                mockMvc.perform(get(ORDERS_URL))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content").isArray());

                verify(orderService).getOrders(any(OrderFilterDTO.class), any(Pageable.class));
            }

            @Test
            @DisplayName("Should return 200 with correct pagination metadata when on second page")
            @WithMockUser(roles = "USER")
            void shouldReturn200_withCorrectPaginationOnSecondPage() throws Exception {
                // Arrange — simulate second page with 5 total elements, page size 2
                OrderSummaryResponseDTO summary = buildSummary(ORDER_ID_2, Status.SHIPPED, 9000L);
                Page<OrderSummaryResponseDTO> secondPage =
                        new PageImpl<>(List.of(summary), PageRequest.of(1, 2), 5);

                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(secondPage);

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL)
                                .param("page", "1")
                                .param("size", "2"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.number").value(1))
                        .andExpect(jsonPath("$.size").value(2))
                        .andExpect(jsonPath("$.totalElements").value(5))
                        .andExpect(jsonPath("$.totalPages").value(3))
                        .andExpect(jsonPath("$.content", hasSize(1)));
            }

            @Test
            @DisplayName("Should return 200 when status filter and pagination are combined")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenFilterAndPaginationCombined() throws Exception {
                // Arrange
                OrderSummaryResponseDTO shippedSummary = buildSummary(ORDER_ID_1, Status.SHIPPED, 11000L);
                Page<OrderSummaryResponseDTO> filteredPage =
                        new PageImpl<>(List.of(shippedSummary), PageRequest.of(0, 5), 1);

                when(orderService.getOrders(any(OrderFilterDTO.class), any(Pageable.class)))
                        .thenReturn(filteredPage);

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL)
                                .param("status", "SHIPPED")
                                .param("page", "0")
                                .param("size", "5"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content", hasSize(1)))
                        .andExpect(jsonPath("$.content[0].status").value("SHIPPED"));
            }
        }
    }

    // ==================== getOrder() ====================
    @Nested
    @DisplayName("getOrder()")
    class GetOrder {

        // ======================== Helpers ========================

        private OrderResponseDTO validOrderResponse() {
            OrderItemResponseDTO orderItem = new OrderItemResponseDTO(
                    VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY, VALID_LOCKED_PRICE
            );
            return new OrderResponseDTO(
                    VALID_ORDER_ID, Status.PENDING, VALID_TOTAL_PRICE,
                    VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now(), Set.of(orderItem)
            );
        }

        // ==================== SUCCESS CASES (200) ====================

        @Nested
        @DisplayName("Success cases — 200 OK")
        class SuccessCases {

            @Test
            @DisplayName("Should return 200 and correct JSON when authenticated USER retrieves an existing order")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenOrderExists() throws Exception {
                // Arrange
                when(orderService.getOrder(VALID_ORDER_ID)).thenReturn(validOrderResponse());

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(VALID_ORDER_ID.toString()))
                        .andExpect(jsonPath("$.status").value("PENDING"))
                        .andExpect(jsonPath("$.totalPrice").value(VALID_TOTAL_PRICE))
                        .andExpect(jsonPath("$.shippingAddress").value(VALID_SHIPPING_ADDRESS))
                        .andExpect(jsonPath("$.createdAt").exists())
                        .andExpect(jsonPath("$.updatedAt").exists())
                        .andExpect(jsonPath("$.orderItems").isArray())
                        .andExpect(jsonPath("$.orderItems[0].productId").value(VALID_PRODUCT_ID.toString()))
                        .andExpect(jsonPath("$.orderItems[0].productTitle").value(VALID_PRODUCT_TITLE))
                        .andExpect(jsonPath("$.orderItems[0].quantity").value(VALID_QUANTITY))
                        .andExpect(jsonPath("$.orderItems[0].lockedPrice").value(VALID_LOCKED_PRICE));

                verify(orderService).getOrder(VALID_ORDER_ID);
            }

            @Test
            @DisplayName("Should call orderService.getOrder exactly once")
            @WithMockUser(roles = "USER")
            void shouldCallServiceExactlyOnce() throws Exception {
                // Arrange
                when(orderService.getOrder(VALID_ORDER_ID)).thenReturn(validOrderResponse());

                // Act
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isOk());

                // Assert
                verify(orderService).getOrder(VALID_ORDER_ID);
                verifyNoMoreInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 200 when ADMIN with USER role accesses order")
            @WithMockUser(roles = {"USER", "ADMIN"})
            void shouldReturn200_whenAdminWithUserRole() throws Exception {
                // Arrange
                when(orderService.getOrder(VALID_ORDER_ID)).thenReturn(validOrderResponse());

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isOk())
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
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isUnauthorized());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 401 when invalid credentials are provided")
            void shouldReturn401_whenInvalidCredentials() throws Exception {
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID)
                                .with(httpBasic("wrong@email.com", "WrongPassword1!")))
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
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 403 when authenticated user has ROLE_ADMIN only (without USER)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn403_whenRoleIsAdminOnly() throws Exception {
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 403 when user has no roles at all")
            @WithMockUser(roles = {})
            void shouldReturn403_whenUserHasNoRoles() throws Exception {
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
            }
        }

        // ==================== NOT FOUND CASES — Customer profile (404) ====================

        @Nested
        @DisplayName("Not found cases — 404 Not Found")
        class NotFoundCases {

            @Test
            @DisplayName("Should return 404 when customer profile is not found in database")
            @WithMockUser(roles = "USER")
            void shouldReturn404_whenCustomerNotFound() throws Exception {
                // Arrange
                when(orderService.getOrder(VALID_ORDER_ID))
                        .thenThrow(new CustomerNotFoundException("Profile not found"));

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").value("Profile not found"));
            }

            @Test
            @DisplayName("Should return 404 when order does not exist for the authenticated customer")
            @WithMockUser(roles = "USER")
            void shouldReturn404_whenOrderNotFound() throws Exception {
                // Arrange
                when(orderService.getOrder(VALID_ORDER_ID))
                        .thenThrow(new OrderNotFoundException("Order not found"));

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").value("Order not found"));
            }
        }

        // ==================== VALIDATION FAILURES — Path variable (400) ====================

        @Nested
        @DisplayName("Invalid path variable — 400 Bad Request")
        class InvalidPathVariable {

            @Test
            @DisplayName("Should return 400 when orderId is not a valid UUID format")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenOrderIdIsInvalidUUID() throws Exception {
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", "not-a-valid-uuid"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when orderId is a plain number instead of UUID")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenOrderIdIsPlainNumber() throws Exception {
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", "12345"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when orderId is an empty string")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenOrderIdIsEmpty() throws Exception {
                // GET /orders/ with trailing slash — Spring resolves this to the list endpoint,
                // but GET /orders/%20 (whitespace-encoded) hits the path variable as a blank string
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", " "))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(orderService);
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
                when(orderService.getOrder(VALID_ORDER_ID))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.status").value(500))
                        .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
            }

            @Test
            @DisplayName("Should return 500 when service throws an unexpected IllegalStateException")
            @WithMockUser(roles = "USER")
            void shouldReturn500_whenServiceThrowsIllegalStateException() throws Exception {
                // Arrange
                when(orderService.getOrder(VALID_ORDER_ID))
                        .thenThrow(new IllegalStateException("Unexpected internal state"));

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
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
            @DisplayName("Should return all expected fields in the success response")
            @WithMockUser(roles = "USER")
            void shouldReturnAllFieldsInResponse() throws Exception {
                // Arrange
                when(orderService.getOrder(VALID_ORDER_ID)).thenReturn(validOrderResponse());

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").exists())
                        .andExpect(jsonPath("$.status").exists())
                        .andExpect(jsonPath("$.totalPrice").exists())
                        .andExpect(jsonPath("$.shippingAddress").exists())
                        .andExpect(jsonPath("$.createdAt").exists())
                        .andExpect(jsonPath("$.updatedAt").exists())
                        .andExpect(jsonPath("$.orderItems").exists())
                        .andExpect(jsonPath("$.orderItems").isArray());
            }

            @Test
            @DisplayName("Should return order item fields in the response")
            @WithMockUser(roles = "USER")
            void shouldReturnOrderItemFieldsInResponse() throws Exception {
                // Arrange
                when(orderService.getOrder(VALID_ORDER_ID)).thenReturn(validOrderResponse());

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.orderItems[0].productId").exists())
                        .andExpect(jsonPath("$.orderItems[0].productTitle").exists())
                        .andExpect(jsonPath("$.orderItems[0].quantity").exists())
                        .andExpect(jsonPath("$.orderItems[0].lockedPrice").exists());
            }

            @Test
            @DisplayName("Error response should contain status, message, and timeStamp fields")
            @WithMockUser(roles = "USER")
            void shouldReturnErrorResponseStructure() throws Exception {
                // Arrange
                when(orderService.getOrder(VALID_ORDER_ID))
                        .thenThrow(new OrderNotFoundException("Order not found"));

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").isNotEmpty())
                        .andExpect(jsonPath("$.timeStamp").isNumber());
            }
        }

        // ==================== EDGE CASES ====================

        @Nested
        @DisplayName("Edge cases")
        class EdgeCases {

            @Test
            @DisplayName("Should return 200 when order contains multiple order items")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenOrderContainsMultipleItems() throws Exception {
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
                        VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now(), Set.of(item1, item2)
                );

                when(orderService.getOrder(VALID_ORDER_ID)).thenReturn(response);

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalPrice").value(13000L))
                        .andExpect(jsonPath("$.orderItems.length()").value(2));
            }

            @Test
            @DisplayName("Should return 200 when order has a shippingAddress with special characters")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenShippingAddressHasSpecialCharacters() throws Exception {
                // Arrange
                String specialAddress = "Apt #42, 123 O'Brien St, São Paulo — Brazil (南京)";

                OrderItemResponseDTO orderItem = new OrderItemResponseDTO(
                        VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY, VALID_LOCKED_PRICE
                );
                OrderResponseDTO response = new OrderResponseDTO(
                        VALID_ORDER_ID, Status.DELIVERED, VALID_TOTAL_PRICE,
                        specialAddress, Instant.now(), Instant.now(), Set.of(orderItem)
                );

                when(orderService.getOrder(VALID_ORDER_ID)).thenReturn(response);

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.shippingAddress").value(specialAddress));
            }

            @Test
            @DisplayName("Should return 200 when order status is SHIPPED")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenOrderStatusIsShipped() throws Exception {
                // Arrange
                OrderItemResponseDTO orderItem = new OrderItemResponseDTO(
                        VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY, VALID_LOCKED_PRICE
                );
                OrderResponseDTO response = new OrderResponseDTO(
                        VALID_ORDER_ID, Status.SHIPPED, VALID_TOTAL_PRICE,
                        VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now(), Set.of(orderItem)
                );

                when(orderService.getOrder(VALID_ORDER_ID)).thenReturn(response);

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("SHIPPED"));
            }

            @Test
            @DisplayName("Should return 200 when order status is CANCELED")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenOrderStatusIsCanceled() throws Exception {
                // Arrange
                OrderItemResponseDTO orderItem = new OrderItemResponseDTO(
                        VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY, VALID_LOCKED_PRICE
                );
                OrderResponseDTO response = new OrderResponseDTO(
                        VALID_ORDER_ID, Status.CANCELED, VALID_TOTAL_PRICE,
                        VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now(), Set.of(orderItem)
                );

                when(orderService.getOrder(VALID_ORDER_ID)).thenReturn(response);

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("CANCELED"));
            }

            @Test
            @DisplayName("Should return 200 when a different valid UUID is used as orderId")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenDifferentUUIDIsUsed() throws Exception {
                // Arrange
                UUID differentOrderId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

                OrderItemResponseDTO orderItem = new OrderItemResponseDTO(
                        VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY, VALID_LOCKED_PRICE
                );
                OrderResponseDTO response = new OrderResponseDTO(
                        differentOrderId, Status.PENDING, VALID_TOTAL_PRICE,
                        VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now(), Set.of(orderItem)
                );

                when(orderService.getOrder(differentOrderId)).thenReturn(response);

                // Act & Assert
                mockMvc.perform(get(ORDERS_URL + "/{orderId}", differentOrderId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(differentOrderId.toString()));
            }
        }
    }

    // ==================== cancelOrder() ====================
    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrder {

        // ======================== Constants ========================

        private static final String CANCEL_URL = ORDERS_URL + "/{orderId}/cancel";

        // ======================== Helpers ========================

        private OrderResponseDTO canceledOrderResponse() {
            OrderItemResponseDTO orderItem = new OrderItemResponseDTO(
                    VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY, VALID_LOCKED_PRICE
            );
            return new OrderResponseDTO(
                    VALID_ORDER_ID, Status.CANCELED, VALID_TOTAL_PRICE,
                    VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now(), Set.of(orderItem)
            );
        }

        // ==================== SUCCESS CASES (200) ====================

        @Nested
        @DisplayName("Success cases — 200 OK")
        class SuccessCases {

            @Test
            @DisplayName("Should return 200 and correct JSON when authenticated USER cancels a PENDING order")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenValidCancellation() throws Exception {
                // Arrange
                when(orderService.cancelOrder(VALID_ORDER_ID)).thenReturn(canceledOrderResponse());

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(VALID_ORDER_ID.toString()))
                        .andExpect(jsonPath("$.status").value("CANCELED"))
                        .andExpect(jsonPath("$.totalPrice").value(VALID_TOTAL_PRICE))
                        .andExpect(jsonPath("$.shippingAddress").value(VALID_SHIPPING_ADDRESS))
                        .andExpect(jsonPath("$.createdAt").exists())
                        .andExpect(jsonPath("$.updatedAt").exists())
                        .andExpect(jsonPath("$.orderItems").isArray())
                        .andExpect(jsonPath("$.orderItems[0].productId").value(VALID_PRODUCT_ID.toString()))
                        .andExpect(jsonPath("$.orderItems[0].productTitle").value(VALID_PRODUCT_TITLE))
                        .andExpect(jsonPath("$.orderItems[0].quantity").value(VALID_QUANTITY))
                        .andExpect(jsonPath("$.orderItems[0].lockedPrice").value(VALID_LOCKED_PRICE));

                verify(orderService).cancelOrder(VALID_ORDER_ID);
            }

            @Test
            @DisplayName("Should call orderService.cancelOrder exactly once")
            @WithMockUser(roles = "USER")
            void shouldCallServiceExactlyOnce() throws Exception {
                // Arrange
                when(orderService.cancelOrder(VALID_ORDER_ID)).thenReturn(canceledOrderResponse());

                // Act
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isOk());

                // Assert
                verify(orderService).cancelOrder(VALID_ORDER_ID);
                verifyNoMoreInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 200 when ADMIN with USER role cancels an order")
            @WithMockUser(roles = {"USER", "ADMIN"})
            void shouldReturn200_whenAdminWithUserRole() throws Exception {
                // Arrange
                when(orderService.cancelOrder(VALID_ORDER_ID)).thenReturn(canceledOrderResponse());

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(VALID_ORDER_ID.toString()))
                        .andExpect(jsonPath("$.status").value("CANCELED"));
            }
        }

        // ==================== AUTHENTICATION FAILURES (401) ====================

        @Nested
        @DisplayName("Authentication failures — 401 Unauthorized")
        class AuthenticationFailures {

            @Test
            @DisplayName("Should return 401 when no credentials are provided (anonymous)")
            void shouldReturn401_whenNoCredentials() throws Exception {
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isUnauthorized());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 401 when invalid credentials are provided")
            void shouldReturn401_whenInvalidCredentials() throws Exception {
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID)
                                .with(httpBasic("wrong@email.com", "WrongPassword1!")))
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
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 403 when authenticated user has ROLE_ADMIN only (without USER)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn403_whenRoleIsAdminOnly() throws Exception {
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 403 when user has no roles at all")
            @WithMockUser(roles = {})
            void shouldReturn403_whenUserHasNoRoles() throws Exception {
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(orderService);
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
                when(orderService.cancelOrder(VALID_ORDER_ID))
                        .thenThrow(new CustomerNotFoundException("Profile not found"));

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").value("Profile not found"));
            }

            @Test
            @DisplayName("Should return 404 when order does not exist for the authenticated customer")
            @WithMockUser(roles = "USER")
            void shouldReturn404_whenOrderNotFound() throws Exception {
                // Arrange
                when(orderService.cancelOrder(VALID_ORDER_ID))
                        .thenThrow(new OrderNotFoundException("Order not found"));

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").value("Order not found"));
            }
        }

        // ==================== CONFLICT CASES (409) ====================

        @Nested
        @DisplayName("Conflict cases — 409 Conflict")
        class ConflictCases {

            @Test
            @DisplayName("Should return 409 when order has already been shipped")
            @WithMockUser(roles = "USER")
            void shouldReturn409_whenOrderAlreadyShipped() throws Exception {
                // Arrange
                when(orderService.cancelOrder(VALID_ORDER_ID))
                        .thenThrow(new OrderStateConflictException("Order cannot be canceled because it has already been shipped"));

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.status").value(409))
                        .andExpect(jsonPath("$.message").value("Order cannot be canceled because it has already been shipped"));
            }

            @Test
            @DisplayName("Should return 409 when order has already been delivered")
            @WithMockUser(roles = "USER")
            void shouldReturn409_whenOrderAlreadyDelivered() throws Exception {
                // Arrange
                when(orderService.cancelOrder(VALID_ORDER_ID))
                        .thenThrow(new OrderStateConflictException("Order cannot be canceled because it has already been delivered"));

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.status").value(409))
                        .andExpect(jsonPath("$.message").value("Order cannot be canceled because it has already been delivered"));
            }

            @Test
            @DisplayName("Should return 409 when order has already been canceled")
            @WithMockUser(roles = "USER")
            void shouldReturn409_whenOrderAlreadyCanceled() throws Exception {
                // Arrange
                when(orderService.cancelOrder(VALID_ORDER_ID))
                        .thenThrow(new OrderStateConflictException("Order cannot be canceled because it has already been canceled"));

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.status").value(409))
                        .andExpect(jsonPath("$.message").value("Order cannot be canceled because it has already been canceled"));
            }

            @Test
            @DisplayName("Should return 409 when a race condition causes a DataIntegrityViolation on cancel")
            @WithMockUser(roles = "USER")
            void shouldReturn409_whenRaceConditionOnCancel() throws Exception {
                // Arrange
                when(orderService.cancelOrder(VALID_ORDER_ID))
                        .thenThrow(new OrderStateConflictException("Order already has been canceled. Please refresh"));

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.status").value(409))
                        .andExpect(jsonPath("$.message").value("Order already has been canceled. Please refresh"));
            }
        }

        // ==================== VALIDATION FAILURES — Path variable (400) ====================

        @Nested
        @DisplayName("Invalid path variable — 400 Bad Request")
        class InvalidPathVariable {

            @Test
            @DisplayName("Should return 400 when orderId is not a valid UUID format")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenOrderIdIsInvalidUUID() throws Exception {
                mockMvc.perform(post(CANCEL_URL, "not-a-valid-uuid"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when orderId is a plain number instead of UUID")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenOrderIdIsPlainNumber() throws Exception {
                mockMvc.perform(post(CANCEL_URL, "12345"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(orderService);
            }

            @Test
            @DisplayName("Should return 400 when orderId is an empty string")
            @WithMockUser(roles = "USER")
            void shouldReturn400_whenOrderIdIsEmpty() throws Exception {
                mockMvc.perform(post(CANCEL_URL, " "))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(orderService);
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
                when(orderService.cancelOrder(VALID_ORDER_ID))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.status").value(500))
                        .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
            }

            @Test
            @DisplayName("Should return 500 when service throws an unexpected IllegalStateException")
            @WithMockUser(roles = "USER")
            void shouldReturn500_whenServiceThrowsIllegalStateException() throws Exception {
                // Arrange
                when(orderService.cancelOrder(VALID_ORDER_ID))
                        .thenThrow(new IllegalStateException("Unexpected internal state"));

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
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
            @DisplayName("Should return all expected fields in the success response")
            @WithMockUser(roles = "USER")
            void shouldReturnAllFieldsInResponse() throws Exception {
                // Arrange
                when(orderService.cancelOrder(VALID_ORDER_ID)).thenReturn(canceledOrderResponse());

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").exists())
                        .andExpect(jsonPath("$.status").exists())
                        .andExpect(jsonPath("$.totalPrice").exists())
                        .andExpect(jsonPath("$.shippingAddress").exists())
                        .andExpect(jsonPath("$.createdAt").exists())
                        .andExpect(jsonPath("$.updatedAt").exists())
                        .andExpect(jsonPath("$.orderItems").exists())
                        .andExpect(jsonPath("$.orderItems").isArray());
            }

            @Test
            @DisplayName("Should return order item fields in the response")
            @WithMockUser(roles = "USER")
            void shouldReturnOrderItemFieldsInResponse() throws Exception {
                // Arrange
                when(orderService.cancelOrder(VALID_ORDER_ID)).thenReturn(canceledOrderResponse());

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.orderItems").isArray())
                        .andExpect(jsonPath("$.orderItems[0].productId").exists())
                        .andExpect(jsonPath("$.orderItems[0].productTitle").exists())
                        .andExpect(jsonPath("$.orderItems[0].quantity").exists())
                        .andExpect(jsonPath("$.orderItems[0].lockedPrice").exists());
            }

            @Test
            @DisplayName("Error response should contain status, message, and timeStamp fields")
            @WithMockUser(roles = "USER")
            void shouldReturnErrorResponseStructure() throws Exception {
                // Arrange
                when(orderService.cancelOrder(VALID_ORDER_ID))
                        .thenThrow(new OrderNotFoundException("Order not found"));

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.status").value(404))
                        .andExpect(jsonPath("$.message").isNotEmpty())
                        .andExpect(jsonPath("$.timeStamp").isNumber());
            }
        }

        // ==================== EDGE CASES ====================

        @Nested
        @DisplayName("Edge cases")
        class EdgeCases {

            @Test
            @DisplayName("Should return 200 when canceled order contains multiple order items")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenCanceledOrderContainsMultipleItems() throws Exception {
                // Arrange
                UUID productId2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

                OrderItemResponseDTO item1 = new OrderItemResponseDTO(
                        VALID_PRODUCT_ID, "Wireless Headphones", 2, 2500L
                );
                OrderItemResponseDTO item2 = new OrderItemResponseDTO(
                        productId2, "Mechanical Keyboard", 1, 8000L
                );
                OrderResponseDTO response = new OrderResponseDTO(
                        VALID_ORDER_ID, Status.CANCELED, 13000L,
                        VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now(), Set.of(item1, item2)
                );

                when(orderService.cancelOrder(VALID_ORDER_ID)).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("CANCELED"))
                        .andExpect(jsonPath("$.totalPrice").value(13000L))
                        .andExpect(jsonPath("$.orderItems.length()").value(2));
            }

            @Test
            @DisplayName("Should return 200 when canceled order has a shippingAddress with special characters")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenShippingAddressHasSpecialCharacters() throws Exception {
                // Arrange
                String specialAddress = "Apt #42, 123 O'Brien St, São Paulo — Brazil (南京)";

                OrderItemResponseDTO orderItem = new OrderItemResponseDTO(
                        VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY, VALID_LOCKED_PRICE
                );
                OrderResponseDTO response = new OrderResponseDTO(
                        VALID_ORDER_ID, Status.CANCELED, VALID_TOTAL_PRICE,
                        specialAddress, Instant.now(), Instant.now(), Set.of(orderItem)
                );

                when(orderService.cancelOrder(VALID_ORDER_ID)).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.shippingAddress").value(specialAddress));
            }

            @Test
            @DisplayName("Should return 200 when a different valid UUID is used as orderId")
            @WithMockUser(roles = "USER")
            void shouldReturn200_whenDifferentUUIDIsUsed() throws Exception {
                // Arrange
                UUID differentOrderId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

                OrderItemResponseDTO orderItem = new OrderItemResponseDTO(
                        VALID_PRODUCT_ID, VALID_PRODUCT_TITLE, VALID_QUANTITY, VALID_LOCKED_PRICE
                );
                OrderResponseDTO response = new OrderResponseDTO(
                        differentOrderId, Status.CANCELED, VALID_TOTAL_PRICE,
                        VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now(), Set.of(orderItem)
                );

                when(orderService.cancelOrder(differentOrderId)).thenReturn(response);

                // Act & Assert
                mockMvc.perform(post(CANCEL_URL, differentOrderId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(differentOrderId.toString()))
                        .andExpect(jsonPath("$.status").value("CANCELED"));
            }
        }

        // ==================== HTTP METHOD FAILURES (405) ====================

        @Nested
        @DisplayName("Invalid HTTP Method — 405 Method Not Allowed")
        class InvalidHttpMethod {

            @Test
            @DisplayName("Should return 405 when sending a GET request to the cancel endpoint")
            @WithMockUser(roles = "USER")
            void shouldReturn405_whenUsingGetMethod() throws Exception {
                mockMvc.perform(get(CANCEL_URL, VALID_ORDER_ID))
                        .andExpect(status().isMethodNotAllowed());

                verifyNoInteractions(orderService);
            }
        }
    }
}
