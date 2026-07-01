package com.sivan.ecommerce.service.order;

import com.sivan.ecommerce.dto.order.OrderFilterDTO;
import com.sivan.ecommerce.dto.order.OrderItemResponseDTO;
import com.sivan.ecommerce.dto.order.OrderRequestDTO;
import com.sivan.ecommerce.dto.order.OrderResponseDTO;
import com.sivan.ecommerce.dto.order.OrderSummaryResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.cart.Cart;
import com.sivan.ecommerce.entity.cart.CartItem;
import com.sivan.ecommerce.entity.customer.Customer;
import com.sivan.ecommerce.entity.order.Order;
import com.sivan.ecommerce.entity.order.OrderItem;
import com.sivan.ecommerce.entity.order.Status;
import com.sivan.ecommerce.entity.outbox.InventoryOutbox;
import com.sivan.ecommerce.entity.product.Product;
import com.sivan.ecommerce.exception.CustomerNotFoundException;
import com.sivan.ecommerce.exception.InsufficientStockException;
import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.exception.OrderNotFoundException;
import com.sivan.ecommerce.exception.OrderStateConflictException;
import com.sivan.ecommerce.exception.ResourceConflictException;
import com.sivan.ecommerce.repository.cart.CartItemRepository;
import com.sivan.ecommerce.repository.customer.CustomerRepository;
import com.sivan.ecommerce.repository.order.OrderRepository;
import com.sivan.ecommerce.repository.outbox.InventoryOutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderServiceImpl}.
 * Only the service layer is tested — repositories are mocked.
 * No Spring context is loaded, keeping tests fast and isolated.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl")
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private InventoryOutboxRepository inventoryOutboxRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    // ======================== Helper Constants ========================

    private static final String VALID_EMAIL = "john.doe@example.com";
    private static final String VALID_SHIPPING_ADDRESS = "123 Main St, New York, NY 10001";
    private static final String VALID_IDEMPOTENCY_KEY = "idem-key-12345";
    private static final long PRODUCT_PRICE = 2500L;
    private static final int PRODUCT_STOCK = 50;
    private static final int CART_ITEM_QUANTITY = 3;

    // ======================== Helper Methods ========================

    /**
     * Sets up a mocked {@link SecurityContext} so that
     * {@code SecurityContextHolder.getContext().getAuthentication().getName()}
     * returns the given email.
     *
     * <p><b>IMPORTANT:</b> Every test that calls this helper MUST clear
     * the context afterward via {@link #clearSecurityContext()} to prevent
     * test pollution across the suite.</p>
     */
    private void stubAuthenticatedUser(String email) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(email);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);
    }

    /**
     * Clears the {@link SecurityContextHolder} to avoid leaking state
     * between tests (static thread-local pollution).
     */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Creates an active {@link Product} with an ID assigned via reflection,
     * mimicking Hibernate's @UuidGenerator behavior on persist.
     */
    private Product buildActiveProduct(UUID id, String title, int stock, long price) {
        Product product = new Product(
                title, "A great product",
                stock, price, "USD",
                "https://example.com/images/product.png"
        );
        product.setActive(true);
        EntityTestUtil.setId(product, id);

        return product;
    }

    /**
     * Creates a {@link Customer} with a {@link Cart} already assigned,
     * and both have IDs set via reflection.
     */
    private Customer buildCustomerWithCart(UUID customerId, UUID cartId) {
        Customer customer = new Customer(
                "John", "Doe", VALID_EMAIL,
                "New York, USA", "$2a$10$encodedPasswordHash"
        );
        EntityTestUtil.setId(customer, customerId);

        Cart cart = new Cart();
        EntityTestUtil.setId(cart, cartId);
        customer.setCart(cart);

        return customer;
    }

    /**
     * Creates a {@link CartItem} with product and cart relationships
     * and an ID assigned via reflection.
     */
    private CartItem buildCartItem(UUID cartItemId, Product product, Cart cart, int quantity) {
        CartItem cartItem = new CartItem(quantity);
        cartItem.setProduct(product);
        cartItem.setCart(cart);
        EntityTestUtil.setId(cartItem, cartItemId);

        return cartItem;
    }

    /**
     * Builds a valid {@link OrderRequestDTO} with a populated shipping address.
     */
    private OrderRequestDTO validRequest() {
        return new OrderRequestDTO(VALID_SHIPPING_ADDRESS);
    }

    /**
     * Sets up the common mocking for a placeOrder() flow with a single cart item,
     * WITHOUT stubbing {@code orderRepository.save()}.
     *
     * <p>Use this when the test needs to provide its own {@code save()} behavior
     * (e.g., a custom {@code thenAnswer}) to avoid Mockito's
     * {@code UnnecessaryStubbingException}.</p>
     */
    private StubResult stubPlaceOrderBase() {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID cartItemId = UUID.randomUUID();

        Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);
        Customer customer = buildCustomerWithCart(customerId, cartId);
        CartItem cartItem = buildCartItem(cartItemId, product, customer.getCart(), CART_ITEM_QUANTITY);

        List<CartItem> cartItems = List.of(cartItem);

        stubAuthenticatedUser(VALID_EMAIL);

        when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
        when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(cartItems);

        return new StubResult(productId, customerId, cartId, cartItemId, product, customer, cartItems);
    }

    /**
     * Sets up the common mocking for a successful placeOrder() flow with a single cart item:
     * - idempotency key does not exist
     * - authenticated user email is stubbed
     * - customer with cart is found
     * - cart has one active product with sufficient stock
     * - repository.save() returns the order as-is
     */
    private StubResult stubPlaceOrderSuccess() {
        StubResult result = stubPlaceOrderBase();

        // save() returns the order as-is (the service builds and passes it)
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        return result;
    }

    /**
     * Holds references to entities created during test setup,
     * so tests can assert against them without re-querying.
     */
    private record StubResult(UUID productId, UUID customerId, UUID cartId,
                              UUID cartItemId, Product product, Customer customer,
                              List<CartItem> cartItems) {
    }

    // ==================== placeOrder() ====================
    @Nested
    @DisplayName("placeOrder()")
    class PlaceOrder {

        // ==================== placeOrder() — IDEMPOTENCY (duplicate key) ====================

        @Nested
        @DisplayName("placeOrder() — Idempotency (duplicate key)")
        class PlaceOrderIdempotency {

            @Test
            @DisplayName("Should return the existing order when idempotency key already exists")
            void shouldReturnExistingOrder_whenIdempotencyKeyExists() {
                // Arrange
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);
                Customer customer = buildCustomerWithCart(UUID.randomUUID(), UUID.randomUUID());

                Order existingOrder = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(existingOrder, orderId);
                existingOrder.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);

                // Add an order item so the mapper has something to map
                OrderItem orderItem = new OrderItem(existingOrder, product, CART_ITEM_QUANTITY, PRODUCT_PRICE);
                existingOrder.addOrderItem(orderItem);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY))
                        .thenReturn(Optional.of(existingOrder));

                // Act
                OrderResponseDTO response = orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertNotNull(response);
                assertEquals(orderId, response.id());
                assertEquals(Status.PENDING, response.status());
                assertEquals(7500L, response.totalPrice());
                assertEquals(VALID_SHIPPING_ADDRESS, response.shippingAddress());
            }

            @Test
            @DisplayName("Should not interact with customer, cart, or save when idempotency key exists")
            void shouldSkipAllBusinessLogic_whenIdempotencyKeyExists() {
                // Arrange
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);
                Customer customer = buildCustomerWithCart(UUID.randomUUID(), UUID.randomUUID());

                Order existingOrder = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(existingOrder, orderId);
                existingOrder.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                existingOrder.addOrderItem(new OrderItem(existingOrder, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY))
                        .thenReturn(Optional.of(existingOrder));

                // Act
                orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert — only findByIdempotencyKey should have been called
                verify(orderRepository).findByIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                verify(orderRepository, never()).saveAndFlush(any(Order.class));
                verifyNoInteractions(customerRepository);
                verifyNoInteractions(cartItemRepository);
            }
        }

        // ==================== placeOrder() — SUCCESS CASES ====================

        @Nested
        @DisplayName("placeOrder() — Success cases")
        class PlaceOrderSuccess {

            @Test
            @DisplayName("Should return a valid OrderResponseDTO when the order is placed successfully")
            void shouldReturnOrderResponseDTO_whenOrderIsPlacedSuccessfully() {
                // Arrange
                stubPlaceOrderSuccess();

                // Act
                OrderResponseDTO response = orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertNotNull(response);
                assertEquals(Status.PENDING, response.status());
                assertEquals(VALID_SHIPPING_ADDRESS, response.shippingAddress());
            }

            @Test
            @DisplayName("Should calculate the total price correctly for a single cart item")
            void shouldCalculateTotalPriceCorrectly_forSingleCartItem() {
                // Arrange
                stubPlaceOrderSuccess();

                // Expected: CART_ITEM_QUANTITY (3) × PRODUCT_PRICE (2500) = 7500
                long expectedTotal = CART_ITEM_QUANTITY * PRODUCT_PRICE;

                // Act
                OrderResponseDTO response = orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertEquals(expectedTotal, response.totalPrice());
            }

            @Test
            @DisplayName("Should calculate the total price correctly for multiple cart items")
            void shouldCalculateTotalPriceCorrectly_forMultipleCartItems() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product headphones = buildActiveProduct(UUID.randomUUID(), "Wireless Headphones", 50, 2500L);
                Product keyboard = buildActiveProduct(UUID.randomUUID(), "Mechanical Keyboard", 30, 8000L);

                Customer customer = buildCustomerWithCart(customerId, cartId);

                CartItem cartItem1 = buildCartItem(UUID.randomUUID(), headphones, customer.getCart(), 2);
                CartItem cartItem2 = buildCartItem(UUID.randomUUID(), keyboard, customer.getCart(), 1);

                List<CartItem> cartItems = List.of(cartItem1, cartItem2);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(cartItems);
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Expected: (2 × 2500) + (1 × 8000) = 5000 + 8000 = 13000
                long expectedTotal = (2 * 2500L) + (1 * 8000L);

                // Act
                OrderResponseDTO response = orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertEquals(expectedTotal, response.totalPrice());
            }

            @Test
            @DisplayName("Should decrement product stock by the ordered quantity")
            void shouldDecrementProductStock_byOrderedQuantity() {
                // Arrange
                StubResult stub = stubPlaceOrderSuccess();
                int originalStock = stub.product().getQuantity();

                // Act
                orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertEquals(originalStock - CART_ITEM_QUANTITY, stub.product().getQuantity());
            }

            @Test
            @DisplayName("Should decrement stock for all products in a multi-item cart")
            void shouldDecrementStockForAllProducts_inMultiItemCart() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product headphones = buildActiveProduct(UUID.randomUUID(), "Wireless Headphones", 50, 2500L);
                Product keyboard = buildActiveProduct(UUID.randomUUID(), "Mechanical Keyboard", 30, 8000L);

                Customer customer = buildCustomerWithCart(customerId, cartId);

                CartItem cartItem1 = buildCartItem(UUID.randomUUID(), headphones, customer.getCart(), 2);
                CartItem cartItem2 = buildCartItem(UUID.randomUUID(), keyboard, customer.getCart(), 5);

                List<CartItem> cartItems = List.of(cartItem1, cartItem2);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(cartItems);
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertEquals(48, headphones.getQuantity());
                assertEquals(25, keyboard.getQuantity());
            }

            @Test
            @DisplayName("Should delete all cart items after placing the order")
            void shouldDeleteAllCartItems_afterPlacingOrder() {
                // Arrange
                StubResult stub = stubPlaceOrderSuccess();

                // Act
                orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                verify(cartItemRepository).deleteAll(stub.cartItems());
            }

            @Test
            @DisplayName("Should save the order exactly once via the repository")
            void shouldCallRepositorySaveExactlyOnce() {
                // Arrange
                stubPlaceOrderSuccess();

                // Act
                orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                verify(orderRepository).saveAndFlush(any(Order.class));
            }

            @Test
            @DisplayName("Should set the idempotency key on the order before saving")
            void shouldSetIdempotencyKeyOnOrder_beforeSaving() {
                // Arrange
                stubPlaceOrderBase();

                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
                    Order capturedOrder = inv.getArgument(0);

                    // Verify the idempotency key was set before save
                    assertEquals(VALID_IDEMPOTENCY_KEY, capturedOrder.getIdempotencyKey());

                    return capturedOrder;
                });

                // Act
                orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                verify(orderRepository).saveAndFlush(any(Order.class));
            }

            @Test
            @DisplayName("Should set the order status to PENDING")
            void shouldSetOrderStatusToPending() {
                // Arrange
                stubPlaceOrderSuccess();

                // Act
                OrderResponseDTO response = orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertEquals(Status.PENDING, response.status());
            }

            @Test
            @DisplayName("Should set the shipping address from the request DTO")
            void shouldSetShippingAddressFromRequestDTO() {
                // Arrange
                stubPlaceOrderSuccess();

                // Act
                OrderResponseDTO response = orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertEquals(VALID_SHIPPING_ADDRESS, response.shippingAddress());
            }

            @Test
            @DisplayName("Should include the correct number of order items in the response")
            void shouldIncludeCorrectNumberOfOrderItems_inResponse() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product headphones = buildActiveProduct(UUID.randomUUID(), "Wireless Headphones", 50, 2500L);
                Product keyboard = buildActiveProduct(UUID.randomUUID(), "Mechanical Keyboard", 30, 8000L);

                Customer customer = buildCustomerWithCart(customerId, cartId);

                CartItem cartItem1 = buildCartItem(UUID.randomUUID(), headphones, customer.getCart(), 2);
                CartItem cartItem2 = buildCartItem(UUID.randomUUID(), keyboard, customer.getCart(), 1);

                List<CartItem> cartItems = List.of(cartItem1, cartItem2);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(cartItems);
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                OrderResponseDTO response = orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertNotNull(response.orderItems());
                assertEquals(2, response.orderItems().size());
            }

            @Test
            @DisplayName("Should map order items with the correct locked price and quantity")
            void shouldMapOrderItemsWithCorrectLockedPriceAndQuantity() {
                // Arrange
                StubResult stub = stubPlaceOrderSuccess();

                // Act
                OrderResponseDTO response = orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertEquals(1, response.orderItems().size());

                OrderItemResponseDTO item = response.orderItems().iterator().next();
                assertEquals(stub.productId(), item.productId());
                assertEquals("Wireless Headphones", item.productTitle());
                assertEquals(CART_ITEM_QUANTITY, item.quantity());
                assertEquals(PRODUCT_PRICE, item.lockedPrice());
            }

            @Test
            @DisplayName("Should check idempotency key before starting business logic")
            void shouldCheckIdempotencyKeyFirst() {
                // Arrange
                stubPlaceOrderSuccess();

                // Act
                orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert — verify the order: findByIdempotencyKey is called before save
                var inOrder = inOrder(orderRepository);
                inOrder.verify(orderRepository).findByIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                inOrder.verify(orderRepository).saveAndFlush(any(Order.class));
            }

            @Test
            @DisplayName("Should check queries order")
            void shouldQueriesOrder() {
                // Arrange
                stubPlaceOrderSuccess();

                // Act
                orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert — verify queries order
                var inOrder = inOrder(orderRepository, customerRepository, cartItemRepository);

                inOrder.verify(orderRepository).findByIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                inOrder.verify(customerRepository).findByEmailWithCart(VALID_EMAIL.toLowerCase());
                inOrder.verify(cartItemRepository).findAllByCartIdWithProduct(any(UUID.class));
                inOrder.verify(orderRepository).saveAndFlush(any(Order.class));
                inOrder.verify(cartItemRepository).deleteAll(anyList());
            }
        }

        // ==================== placeOrder() — EMAIL LOWERCASING ====================

        @Nested
        @DisplayName("placeOrder() — Email lowercasing")
        class PlaceOrderEmailLowercasing {

            @Test
            @DisplayName("Should lowercase the email before querying the customer repository")
            void shouldLowercaseEmail_beforeCallingRepository() {
                // Arrange — SecurityContext returns uppercase email
                String upperCaseEmail = "JOHN.DOE@EXAMPLE.COM";

                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product product = buildActiveProduct(UUID.randomUUID(), "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);
                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem cartItem = buildCartItem(UUID.randomUUID(), product, customer.getCart(), CART_ITEM_QUANTITY);

                stubAuthenticatedUser(upperCaseEmail);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart("john.doe@example.com")).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                OrderResponseDTO response = orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertNotNull(response);
                verify(customerRepository).findByEmailWithCart("john.doe@example.com");
            }
        }

        // ==================== placeOrder() — NOT FOUND CASES (CustomerNotFoundException) ====================

        @Nested
        @DisplayName("placeOrder() — Customer not found")
        class PlaceOrderCustomerNotFound {

            @Test
            @DisplayName("Should throw CustomerNotFoundException when authenticated user profile is not found")
            void shouldThrowCustomerNotFoundException_whenProfileNotFound() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                // Act & Assert
                CustomerNotFoundException exception = assertThrows(
                        CustomerNotFoundException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertEquals("Profile not found", exception.getMessage());
            }

            @Test
            @DisplayName("Should not interact with cart or order save when customer is not found")
            void shouldNotCallCartOrSave_whenCustomerNotFound() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                // Act
                assertThrows(CustomerNotFoundException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest()));

                // Assert
                verify(orderRepository, never()).saveAndFlush(any(Order.class));
                verifyNoInteractions(cartItemRepository);
            }
        }

        // ==================== placeOrder() — EMPTY CART (InvalidDataException) ====================

        @Nested
        @DisplayName("placeOrder() — Empty cart validation")
        class PlaceOrderEmptyCart {

            @Test
            @DisplayName("Should throw InvalidDataException when cart is empty")
            void shouldThrowInvalidDataException_whenCartIsEmpty() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(new ArrayList<>());

                // Act & Assert
                InvalidDataException exception = assertThrows(
                        InvalidDataException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertEquals("Cannot place an order with an empty cart", exception.getMessage());
            }

            @Test
            @DisplayName("Should not save order or delete cart items when cart is empty")
            void shouldNotSaveOrDeleteCartItems_whenCartIsEmpty() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(new ArrayList<>());

                // Act
                assertThrows(InvalidDataException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest()));

                // Assert
                verify(orderRepository, never()).saveAndFlush(any(Order.class));
                verify(cartItemRepository, never()).deleteAll(anyList());
            }
        }

        // ==================== placeOrder() — INSUFFICIENT STOCK (InsufficientStockException) ====================

        @Nested
        @DisplayName("placeOrder() — Insufficient stock validation")
        class PlaceOrderInsufficientStock {

            @Test
            @DisplayName("Should throw InsufficientStockException when product is inactive")
            void shouldThrowInsufficientStockException_whenProductIsInactive() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product inactiveProduct = buildActiveProduct(UUID.randomUUID(), "Deleted Product", 10, 1000L);
                inactiveProduct.setActive(false);

                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem cartItem = buildCartItem(UUID.randomUUID(), inactiveProduct, customer.getCart(), 1);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                // Act & Assert
                InsufficientStockException exception = assertThrows(
                        InsufficientStockException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertTrue(exception.getMessage().contains("Deleted Product"));
                assertTrue(exception.getMessage().contains("is no longer available"));
            }

            @Test
            @DisplayName("Should throw InsufficientStockException when requested quantity exceeds stock")
            void shouldThrowInsufficientStockException_whenQuantityExceedsStock() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                // Product only has 2 in stock, but cart requests 10
                Product lowStockProduct = buildActiveProduct(UUID.randomUUID(), "Limited Widget", 2, 500L);

                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem cartItem = buildCartItem(UUID.randomUUID(), lowStockProduct, customer.getCart(), 10);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                // Act & Assert
                InsufficientStockException exception = assertThrows(
                        InsufficientStockException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertTrue(exception.getMessage().contains("Limited Widget"));
                assertTrue(exception.getMessage().contains("insufficient stock"));
                assertTrue(exception.getMessage().contains("2"));
            }

            @Test
            @DisplayName("Should aggregate multiple stock errors into a single exception message")
            void shouldAggregateMultipleStockErrors_intoSingleException() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product inactiveProduct = buildActiveProduct(UUID.randomUUID(), "Removed Laptop", 10, 50000L);
                inactiveProduct.setActive(false);

                Product lowStockProduct = buildActiveProduct(UUID.randomUUID(), "Rare Keyboard", 1, 12000L);

                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem cartItem1 = buildCartItem(UUID.randomUUID(), inactiveProduct, customer.getCart(), 1);
                CartItem cartItem2 = buildCartItem(UUID.randomUUID(), lowStockProduct, customer.getCart(), 5);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem1, cartItem2));

                // Act & Assert
                InsufficientStockException exception = assertThrows(
                        InsufficientStockException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );

                // Both error messages should be present in the combined string
                String message = exception.getMessage();
                assertTrue(message.contains("Removed Laptop"), "Should contain inactive product error");
                assertTrue(message.contains("is no longer available"), "Should contain unavailability message");
                assertTrue(message.contains("Rare Keyboard"), "Should contain low-stock product error");
                assertTrue(message.contains("insufficient stock"), "Should contain insufficient stock message");
            }

            @Test
            @DisplayName("Should not save order or delete cart items when stock validation fails")
            void shouldNotSaveOrDeleteCartItems_whenStockValidationFails() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product inactiveProduct = buildActiveProduct(UUID.randomUUID(), "Deleted Product", 10, 1000L);
                inactiveProduct.setActive(false);

                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem cartItem = buildCartItem(UUID.randomUUID(), inactiveProduct, customer.getCart(), 1);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                // Act
                assertThrows(InsufficientStockException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest()));

                // Assert
                verify(orderRepository, never()).saveAndFlush(any(Order.class));
                verify(cartItemRepository, never()).deleteAll(anyList());
            }
        }

        // ==================== placeOrder() — CONFLICT CASES (Race Conditions) ====================

        @Nested
        @DisplayName("placeOrder() — Conflict / race condition handling")
        class PlaceOrderConflict {

            @Test
            @DisplayName("Should throw ResourceConflictException when DataIntegrityViolationException occurs on save")
            void shouldThrowResourceConflictException_whenDataIntegrityViolationOnSave() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product product = buildActiveProduct(UUID.randomUUID(), "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);
                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem cartItem = buildCartItem(UUID.randomUUID(), product, customer.getCart(), CART_ITEM_QUANTITY);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                when(orderRepository.saveAndFlush(any(Order.class)))
                        .thenThrow(new DataIntegrityViolationException("Duplicate idempotency key"));

                // Act & Assert
                ResourceConflictException exception = assertThrows(
                        ResourceConflictException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertEquals("Order is currently processing. Please refresh", exception.getMessage());
            }

            @Test
            @DisplayName("Should throw ResourceConflictException when ObjectOptimisticLockingFailureException occurs on save")
            void shouldThrowResourceConflictException_whenOptimisticLockingFailureOnSave() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product product = buildActiveProduct(UUID.randomUUID(), "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);
                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem cartItem = buildCartItem(UUID.randomUUID(), product, customer.getCart(), CART_ITEM_QUANTITY);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                when(orderRepository.saveAndFlush(any(Order.class)))
                        .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, "version conflict"));

                // Act & Assert
                ResourceConflictException exception = assertThrows(
                        ResourceConflictException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertEquals("Inventory was updated by another user. Please try again", exception.getMessage());
            }

            @Test
            @DisplayName("Should not delete cart items when DataIntegrityViolationException occurs")
            void shouldNotDeleteCartItems_whenDataIntegrityViolationOccurs() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product product = buildActiveProduct(UUID.randomUUID(), "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);
                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem cartItem = buildCartItem(UUID.randomUUID(), product, customer.getCart(), CART_ITEM_QUANTITY);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                when(orderRepository.saveAndFlush(any(Order.class)))
                        .thenThrow(new DataIntegrityViolationException("Duplicate"));

                // Act
                assertThrows(ResourceConflictException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest()));

                // Assert — cart items should NOT have been deleted since save failed
                verify(cartItemRepository, never()).deleteAll(anyList());
            }

            @Test
            @DisplayName("Should not delete cart items when ObjectOptimisticLockingFailureException occurs")
            void shouldNotDeleteCartItems_whenOptimisticLockingFailureOccurs() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product product = buildActiveProduct(UUID.randomUUID(), "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);
                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem cartItem = buildCartItem(UUID.randomUUID(), product, customer.getCart(), CART_ITEM_QUANTITY);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                when(orderRepository.saveAndFlush(any(Order.class)))
                        .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, "version conflict"));

                // Act
                assertThrows(ResourceConflictException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest()));

                // Assert — cart items should NOT have been deleted since save failed
                verify(cartItemRepository, never()).deleteAll(anyList());
            }
        }

        // ==================== placeOrder() — SECURITY CONTEXT FAILURES ====================

        @Nested
        @DisplayName("placeOrder() — Security context failures")
        class PlaceOrderSecurityContextFailures {

            @Test
            @DisplayName("Should throw NullPointerException when SecurityContext has no Authentication")
            void shouldThrowNPE_whenAuthenticationIsNull() {
                // Arrange — SecurityContext exists but getAuthentication() returns null
                SecurityContext securityContext = mock(SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(null);
                SecurityContextHolder.setContext(securityContext);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

                // Act & Assert — calling getName() on null Authentication throws NPE
                assertThrows(NullPointerException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest()));

                // Verify no business operations occurred
                verifyNoInteractions(customerRepository);
                verifyNoInteractions(cartItemRepository);
                verify(orderRepository, never()).saveAndFlush(any(Order.class));
            }
        }

        // ==================== placeOrder() — SERVER FAILURE CASES ====================

        @Nested
        @DisplayName("placeOrder() — Server failure simulation")
        class PlaceOrderServerFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when findByIdempotencyKey throws")
            void shouldPropagateException_whenFindByIdempotencyKeyThrowsRuntimeException() {
                // Arrange
                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertEquals("Database connection lost", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate RuntimeException when findByEmailWithCart throws")
            void shouldPropagateException_whenFindByEmailWithCartThrowsRuntimeException() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL))
                        .thenThrow(new RuntimeException("Database unavailable"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertEquals("Database unavailable", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate RuntimeException when findAllByCartIdWithProduct throws")
            void shouldPropagateException_whenFindAllByCartIdWithProductThrowsRuntimeException() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId))
                        .thenThrow(new RuntimeException("Database timeout"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertEquals("Database timeout", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when findByIdempotencyKey throws")
            void shouldPropagateException_whenFindByIdempotencyKeyThrowsIllegalStateException() {
                // Arrange
                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY))
                        .thenThrow(new IllegalStateException("Database connection lost"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertEquals("Database connection lost", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when findByEmailWithCart throws")
            void shouldPropagateException_whenFindByEmailWithCartThrowsIllegalStateException() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL))
                        .thenThrow(new IllegalStateException("Database unavailable"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertEquals("Database unavailable", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when findAllByCartIdWithProduct throws")
            void shouldPropagateException_whenFindAllByCartIdWithProductThrowsIllegalStateException() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId))
                        .thenThrow(new IllegalStateException("Database timeout"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertEquals("Database timeout", exception.getMessage());
            }
        }

        // ==================== placeOrder() — MAPPER INTERACTION ====================

        @Nested
        @DisplayName("placeOrder() — Mapper interaction verification")
        class PlaceOrderMapperVerification {

            @Test
            @DisplayName("Should build the order with the correct customer, status, and shipping address before saving")
            void shouldBuildOrderCorrectly_beforeSaving() {
                // Arrange
                StubResult stub = stubPlaceOrderBase();

                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
                    Order capturedOrder = inv.getArgument(0);

                    // Verify the order was built correctly before save
                    assertEquals(stub.customer(), capturedOrder.getCustomer());
                    assertEquals(Status.PENDING, capturedOrder.getStatus());
                    assertEquals(VALID_SHIPPING_ADDRESS, capturedOrder.getShippingAddress());
                    assertEquals(VALID_IDEMPOTENCY_KEY, capturedOrder.getIdempotencyKey());
                    assertFalse(capturedOrder.getOrderItems().isEmpty(),
                            "Order should have at least one order item");

                    return capturedOrder;
                });

                // Act
                orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                verify(orderRepository).saveAndFlush(any(Order.class));
            }

            @Test
            @DisplayName("Should lock the product price at order time (snapshot pricing)")
            void shouldLockProductPriceAtOrderTime() {
                // Arrange
                stubPlaceOrderBase();

                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
                    Order capturedOrder = inv.getArgument(0);

                    // Verify the order item's locked price matches the product price at order time
                    OrderItem capturedItem = capturedOrder.getOrderItems().iterator().next();
                    assertEquals(PRODUCT_PRICE, capturedItem.getLockedPrice(),
                            "Locked price should match product price at order time");
                    assertEquals(CART_ITEM_QUANTITY, capturedItem.getQuantity(),
                            "Order item quantity should match cart item quantity");

                    return capturedOrder;
                });

                // Act
                orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                verify(orderRepository).saveAndFlush(any(Order.class));
            }
        }

        // ==================== placeOrder() — EDGE CASES ====================

        @Nested
        @DisplayName("placeOrder() — Edge cases")
        class PlaceOrderEdgeCases {

            @Test
            @DisplayName("Should handle ordering exactly the remaining stock (boundary)")
            void shouldHandleOrderingExactlyRemainingStock() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                // Product has exactly 5 in stock, and cart requests exactly 5
                Product product = buildActiveProduct(UUID.randomUUID(), "Last Units Widget", 5, 1000L);
                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem cartItem = buildCartItem(UUID.randomUUID(), product, customer.getCart(), 5);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act — should NOT throw because quantity == stock
                OrderResponseDTO response = orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertNotNull(response);
                assertEquals(5000L, response.totalPrice());
                assertEquals(0, product.getQuantity(), "Product stock should be depleted to zero");
            }

            @Test
            @DisplayName("Should throw InsufficientStockException when quantity exceeds stock by exactly one")
            void shouldThrowException_whenQuantityExceedsStockByOne() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                // Product has 5 in stock, but cart requests 6
                Product product = buildActiveProduct(UUID.randomUUID(), "Almost Sold Out Widget", 5, 1000L);
                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem cartItem = buildCartItem(UUID.randomUUID(), product, customer.getCart(), 6);

                stubAuthenticatedUser(VALID_EMAIL);

                when(orderRepository.findByIdempotencyKey(VALID_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                // Act & Assert
                InsufficientStockException exception = assertThrows(
                        InsufficientStockException.class,
                        () -> orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest())
                );
                assertTrue(exception.getMessage().contains("Almost Sold Out Widget"));
                assertTrue(exception.getMessage().contains("5"));
            }

            @Test
            @DisplayName("Should handle a single-item cart correctly")
            void shouldHandleSingleItemCart() {
                // Arrange
                stubPlaceOrderSuccess();

                // Act
                OrderResponseDTO response = orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                assertNotNull(response);
                assertEquals(1, response.orderItems().size());
                assertEquals((long) CART_ITEM_QUANTITY * PRODUCT_PRICE, response.totalPrice());
            }
        }
    }

    // ==================== getOrders() ====================
    @Nested
    @DisplayName("getOrders()")
    class GetOrders {

        // ==================== getOrders() — SUCCESS CASES ====================

        @Nested
        @DisplayName("getOrders() — Success cases")
        class GetOrdersSuccess {

            @Test
            @DisplayName("Should return a page of OrderSummaryResponseDTO when valid request is made")
            void shouldReturnPageOfOrderSummaries_whenValidRequestIsMade() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderSummaryResponseDTO summary = new OrderSummaryResponseDTO(
                        UUID.randomUUID(), Status.PENDING, 7500L,
                        VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now()
                );
                Page<OrderSummaryResponseDTO> expectedPage = new PageImpl<>(
                        List.of(summary), PageRequest.of(0, 10, Sort.by("createdAt").descending()), 1
                );

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 10);

                when(orderRepository.findByFilters(customerId, null, PageRequest.of(0, 10, Sort.by("createdAt").descending())))
                        .thenReturn(expectedPage);

                // Act
                Page<OrderSummaryResponseDTO> result = orderService.getOrders(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
                assertEquals(1, result.getContent().size());

                OrderSummaryResponseDTO returned = result.getContent().get(0);
                assertEquals(summary.id(), returned.id());
                assertEquals(Status.PENDING, returned.status());
                assertEquals(7500L, returned.totalPrice());
                assertEquals(VALID_SHIPPING_ADDRESS, returned.shippingAddress());
            }

            @Test
            @DisplayName("Should filter orders by status when status is provided")
            void shouldFilterOrdersByStatus_whenStatusIsProvided() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderSummaryResponseDTO shippedOrder = new OrderSummaryResponseDTO(
                        UUID.randomUUID(), Status.SHIPPED, 15000L,
                        VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now()
                );
                Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
                Page<OrderSummaryResponseDTO> expectedPage = new PageImpl<>(
                        List.of(shippedOrder), pageable, 1
                );

                OrderFilterDTO filter = new OrderFilterDTO(Status.SHIPPED);

                when(orderRepository.findByFilters(customerId, Status.SHIPPED, pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<OrderSummaryResponseDTO> result = orderService.getOrders(filter, pageable);

                // Assert
                assertEquals(1, result.getTotalElements());
                assertEquals(Status.SHIPPED, result.getContent().get(0).status());

                verify(orderRepository).findByFilters(customerId, Status.SHIPPED, pageable);
            }

            @Test
            @DisplayName("Should pass null status to repository when no status filter is provided")
            void shouldPassNullStatus_whenNoFilterProvided() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable defaultSorted = PageRequest.of(0, 10, Sort.by("createdAt").descending());

                when(orderRepository.findByFilters(customerId, null, defaultSorted))
                        .thenReturn(Page.empty(defaultSorted));

                // Act
                orderService.getOrders(filter, PageRequest.of(0, 10));

                // Assert
                verify(orderRepository).findByFilters(customerId, null, defaultSorted);
            }
        }

        // ==================== getOrders() — DEFAULT SORTING ====================

        @Nested
        @DisplayName("getOrders() — Default sorting")
        class GetOrdersDefaultSorting {

            @Test
            @DisplayName("Should apply default sorting by createdAt descending when pageable is unsorted")
            void shouldApplyDefaultSorting_whenPageableIsUnsorted() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable unsortedPageable = PageRequest.of(0, 10);

                Pageable expectedPageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
                when(orderRepository.findByFilters(customerId, null, expectedPageable))
                        .thenReturn(Page.empty(expectedPageable));

                // Act
                orderService.getOrders(filter, unsortedPageable);

                // Assert — verify the pageable passed to repository has default sort
                verify(orderRepository).findByFilters(customerId, null, expectedPageable);
            }

            @Test
            @DisplayName("Should preserve explicit sorting when pageable is already sorted")
            void shouldPreserveExplicitSorting_whenPageableIsSorted() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable sortedPageable = PageRequest.of(0, 10, Sort.by("totalPrice").ascending());

                when(orderRepository.findByFilters(customerId, null, sortedPageable))
                        .thenReturn(Page.empty(sortedPageable));

                // Act
                orderService.getOrders(filter, sortedPageable);

                // Assert — the original sort should be preserved, NOT overridden
                verify(orderRepository).findByFilters(customerId, null, sortedPageable);
            }

            @Test
            @DisplayName("Should preserve page number and page size when applying default sort")
            void shouldPreservePageNumberAndSize_whenApplyingDefaultSort() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                // Request page 3 with size 5, unsorted
                Pageable unsortedPageable = PageRequest.of(3, 5);

                Pageable expectedPageable = PageRequest.of(3, 5, Sort.by("createdAt").descending());
                when(orderRepository.findByFilters(customerId, null, expectedPageable))
                        .thenReturn(Page.empty(expectedPageable));

                // Act
                orderService.getOrders(filter, unsortedPageable);

                // Assert — page number and size must survive the default sort application
                verify(orderRepository).findByFilters(customerId, null, expectedPageable);
            }
        }

        // ==================== getOrders() — SORT VALIDATION (InvalidDataException) ====================

        @Nested
        @DisplayName("getOrders() — Sort validation")
        class GetOrdersSortValidation {

            @Test
            @DisplayName("Should throw InvalidDataException when sorting by a disallowed property")
            void shouldThrowInvalidDataException_whenSortPropertyIsNotAllowed() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable invalidSort = PageRequest.of(0, 10, Sort.by("email"));

                // Act & Assert
                InvalidDataException exception = assertThrows(
                        InvalidDataException.class,
                        () -> orderService.getOrders(filter, invalidSort)
                );
                assertTrue(exception.getMessage().contains("email"));
                assertTrue(exception.getMessage().contains("is not allowed"));
            }

            @Test
            @DisplayName("Should throw InvalidDataException with the invalid property name in the message")
            void shouldIncludePropertyNameInExceptionMessage_whenSortIsInvalid() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable invalidSort = PageRequest.of(0, 10, Sort.by("password"));

                // Act & Assert
                InvalidDataException exception = assertThrows(
                        InvalidDataException.class,
                        () -> orderService.getOrders(filter, invalidSort)
                );
                assertEquals("Sorting by 'password' is not allowed", exception.getMessage());
            }

            @Test
            @DisplayName("Should not call repository when sort validation fails")
            void shouldNotCallRepository_whenSortValidationFails() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable invalidSort = PageRequest.of(0, 10, Sort.by("status"));

                // Act
                assertThrows(InvalidDataException.class,
                        () -> orderService.getOrders(filter, invalidSort));

                // Assert
                verify(orderRepository, never()).findByFilters(any(), any(), any());
            }

            @Test
            @DisplayName("Should accept sorting by totalPrice")
            void shouldAcceptSortingByTotalPrice() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable sortByPrice = PageRequest.of(0, 10, Sort.by("totalPrice").ascending());

                when(orderRepository.findByFilters(customerId, null, sortByPrice))
                        .thenReturn(Page.empty(sortByPrice));

                // Act & Assert — should NOT throw
                assertDoesNotThrow(() -> orderService.getOrders(filter, sortByPrice));
                verify(orderRepository).findByFilters(customerId, null, sortByPrice);
            }

            @Test
            @DisplayName("Should accept sorting by createdAt")
            void shouldAcceptSortingByCreatedAt() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable sortByDate = PageRequest.of(0, 10, Sort.by("createdAt").ascending());

                when(orderRepository.findByFilters(customerId, null, sortByDate))
                        .thenReturn(Page.empty(sortByDate));

                // Act & Assert — should NOT throw
                assertDoesNotThrow(() -> orderService.getOrders(filter, sortByDate));
                verify(orderRepository).findByFilters(customerId, null, sortByDate);
            }

            @Test
            @DisplayName("Should reject the first invalid property even when combined with valid ones")
            void shouldRejectFirstInvalidProperty_whenMixedWithValidOnes() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                // "createdAt" is valid, but "id" is not
                Pageable mixedSort = PageRequest.of(0, 10,
                        Sort.by("createdAt").ascending().and(Sort.by("id").ascending()));

                // Act & Assert — should throw on "id"
                InvalidDataException exception = assertThrows(
                        InvalidDataException.class,
                        () -> orderService.getOrders(filter, mixedSort)
                );
                assertTrue(exception.getMessage().contains("id"));
                assertTrue(exception.getMessage().contains("is not allowed"));
            }
        }

        // ==================== getOrders() — CUSTOMER NOT FOUND ====================

        @Nested
        @DisplayName("getOrders() — Customer not found")
        class GetOrdersCustomerNotFound {

            @Test
            @DisplayName("Should throw CustomerNotFoundException when authenticated user profile is not found")
            void shouldThrowCustomerNotFoundException_whenProfileNotFound() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 10);

                // Act & Assert
                CustomerNotFoundException exception = assertThrows(
                        CustomerNotFoundException.class,
                        () -> orderService.getOrders(filter, pageable)
                );
                assertEquals("Profile not found", exception.getMessage());
            }

            @Test
            @DisplayName("Should not interact with order repository when customer is not found")
            void shouldNotCallOrderRepository_whenCustomerNotFound() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 10);

                // Act
                assertThrows(CustomerNotFoundException.class,
                        () -> orderService.getOrders(filter, pageable));

                // Assert
                verify(orderRepository, never()).findByFilters(any(), any(), any());
            }
        }

        // ==================== getOrders() — EMAIL LOWERCASING ====================

        @Nested
        @DisplayName("getOrders() — Email lowercasing")
        class GetOrdersEmailLowercasing {

            @Test
            @DisplayName("Should lowercase the email before querying the customer repository")
            void shouldLowercaseEmail_beforeCallingRepository() {
                // Arrange
                String upperCaseEmail = "JOHN.DOE@EXAMPLE.COM";
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(upperCaseEmail);
                when(customerRepository.findByEmailWithCart("john.doe@example.com")).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

                when(orderRepository.findByFilters(customerId, null, pageable))
                        .thenReturn(Page.empty(pageable));

                // Act
                orderService.getOrders(filter, PageRequest.of(0, 10));

                // Assert
                verify(customerRepository).findByEmailWithCart("john.doe@example.com");
            }
        }

        // ==================== getOrders() — SECURITY CONTEXT FAILURES ====================

        @Nested
        @DisplayName("getOrders() — Security context failures")
        class GetOrdersSecurityContextFailures {

            @Test
            @DisplayName("Should throw NullPointerException when SecurityContext has no Authentication")
            void shouldThrowNPE_whenAuthenticationIsNull() {
                // Arrange — SecurityContext exists but getAuthentication() returns null
                SecurityContext securityContext = mock(SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(null);
                SecurityContextHolder.setContext(securityContext);

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 10);

                // Act & Assert — calling getName() on null Authentication throws NPE
                assertThrows(NullPointerException.class,
                        () -> orderService.getOrders(filter, pageable));

                // Verify no business operations occurred
                verifyNoInteractions(customerRepository);
                verify(orderRepository, never()).findByFilters(any(), any(), any());
            }
        }

        // ==================== getOrders() — SERVER FAILURE CASES ====================

        @Nested
        @DisplayName("getOrders() — Server failure simulation")
        class GetOrdersServerFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when findByEmailWithCart throws")
            void shouldPropagateException_whenFindByEmailWithCartThrowsRuntimeException() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL))
                        .thenThrow(new RuntimeException("Database unavailable"));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 10);

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> orderService.getOrders(filter, pageable)
                );
                assertEquals("Database unavailable", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate RuntimeException when findByFilters throws")
            void shouldPropagateException_whenFindByFiltersThrowsRuntimeException() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

                when(orderRepository.findByFilters(customerId, null, pageable))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> orderService.getOrders(filter, PageRequest.of(0, 10))
                );
                assertEquals("Database connection lost", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when findByEmailWithCart throws")
            void shouldPropagateException_whenFindByEmailWithCartThrowsIllegalStateException() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL))
                        .thenThrow(new IllegalStateException("Database unavailable"));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 10);

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> orderService.getOrders(filter, pageable)
                );
                assertEquals("Database unavailable", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when findByFilters throws")
            void shouldPropagateException_whenFindByFiltersThrowsIllegalStateException() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

                when(orderRepository.findByFilters(customerId, null, pageable))
                        .thenThrow(new IllegalStateException("Database connection lost"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> orderService.getOrders(filter, PageRequest.of(0, 10))
                );
                assertEquals("Database connection lost", exception.getMessage());
            }
        }

        // ==================== getOrders() — EDGE CASES ====================

        @Nested
        @DisplayName("getOrders() — Edge cases")
        class GetOrdersEdgeCases {

            @Test
            @DisplayName("Should return an empty page when customer has no orders")
            void shouldReturnEmptyPage_whenCustomerHasNoOrders() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

                when(orderRepository.findByFilters(customerId, null, pageable))
                        .thenReturn(Page.empty(pageable));

                // Act
                Page<OrderSummaryResponseDTO> result = orderService.getOrders(filter, PageRequest.of(0, 10));

                // Assert
                assertNotNull(result);
                assertEquals(0, result.getTotalElements());
                assertTrue(result.getContent().isEmpty());
            }

            @Test
            @DisplayName("Should return an empty page when filtering by status with no matching orders")
            void shouldReturnEmptyPage_whenNoOrdersMatchStatusFilter() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(Status.CANCELED);
                Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

                when(orderRepository.findByFilters(customerId, Status.CANCELED, pageable))
                        .thenReturn(Page.empty(pageable));

                // Act
                Page<OrderSummaryResponseDTO> result = orderService.getOrders(filter, pageable);

                // Assert
                assertNotNull(result);
                assertTrue(result.getContent().isEmpty());
            }

            @Test
            @DisplayName("Should return multiple pages worth of results correctly")
            void shouldHandlePagination_whenMultipleOrdersExist() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderSummaryResponseDTO order1 = new OrderSummaryResponseDTO(
                        UUID.randomUUID(), Status.PENDING, 5000L,
                        VALID_SHIPPING_ADDRESS, Instant.now(), Instant.now()
                );
                OrderSummaryResponseDTO order2 = new OrderSummaryResponseDTO(
                        UUID.randomUUID(), Status.SHIPPED, 12000L,
                        "456 Oak Ave, LA, CA 90001", Instant.now(), Instant.now()
                );

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 2, Sort.by("createdAt").descending());

                // totalElements = 5 but this page only has 2
                Page<OrderSummaryResponseDTO> expectedPage = new PageImpl<>(
                        List.of(order1, order2), pageable, 5
                );

                when(orderRepository.findByFilters(customerId, null, pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<OrderSummaryResponseDTO> result = orderService.getOrders(filter, pageable);

                // Assert
                assertEquals(5, result.getTotalElements());
                assertEquals(3, result.getTotalPages());
                assertEquals(2, result.getContent().size());
                assertFalse(result.isLast());
            }
        }

        // ==================== getOrders() — INTERACTION VERIFICATION ====================

        @Nested
        @DisplayName("getOrders() — Interaction verification")
        class GetOrdersInteractionVerification {

            @Test
            @DisplayName("Should call customerRepository and orderRepository in correct order")
            void shouldCallRepositoriesInCorrectOrder() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

                when(orderRepository.findByFilters(customerId, null, pageable))
                        .thenReturn(Page.empty(pageable));

                // Act
                orderService.getOrders(filter, PageRequest.of(0, 10));

                // Assert — verify the order: customer lookup before order query
                var inOrder = inOrder(customerRepository, orderRepository);
                inOrder.verify(customerRepository).findByEmailWithCart(VALID_EMAIL);
                inOrder.verify(orderRepository).findByFilters(eq(customerId), any(), any());
            }

            @Test
            @DisplayName("Should not interact with cartItemRepository")
            void shouldNotInteractWithCartItemRepository() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(null);
                Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

                when(orderRepository.findByFilters(customerId, null, pageable))
                        .thenReturn(Page.empty(pageable));

                // Act
                orderService.getOrders(filter, PageRequest.of(0, 10));

                // Assert — getOrders should never touch the cart
                verifyNoInteractions(cartItemRepository);
            }

            @Test
            @DisplayName("Should call findByFilters exactly once")
            void shouldCallFindByFiltersExactlyOnce() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));

                OrderFilterDTO filter = new OrderFilterDTO(Status.DELIVERED);
                Pageable pageable = PageRequest.of(0, 10, Sort.by("totalPrice").descending());

                when(orderRepository.findByFilters(customerId, Status.DELIVERED, pageable))
                        .thenReturn(Page.empty(pageable));

                // Act
                orderService.getOrders(filter, pageable);

                // Assert
                verify(orderRepository).findByFilters(customerId, Status.DELIVERED, pageable);
            }
        }
    }

    // ==================== getOrder() ====================
    @Nested
    @DisplayName("getOrder()")
    class GetOrder {

        // ==================== getOrder() — SUCCESS CASES ====================

        @Nested
        @DisplayName("getOrder() — Success cases")
        class GetOrderSuccess {

            @Test
            @DisplayName("Should return OrderResponseDTO when order exists for the authenticated customer")
            void shouldReturnOrderResponseDTO_whenOrderExistsForAuthenticatedCustomer() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act
                OrderResponseDTO response = orderService.getOrder(orderId);

                // Assert
                assertNotNull(response);
                assertEquals(orderId, response.id());
                assertEquals(Status.PENDING, response.status());
                assertEquals(7500L, response.totalPrice());
                assertEquals(VALID_SHIPPING_ADDRESS, response.shippingAddress());
            }

            @Test
            @DisplayName("Should return order items in the response DTO")
            void shouldReturnOrderItems_inResponseDTO() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Mechanical Keyboard", 30, 8000L);

                Order order = new Order(customer, Status.SHIPPED, 8000L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey("idem-key-order-items");
                order.addOrderItem(new OrderItem(order, product, 1, 8000L));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act
                OrderResponseDTO response = orderService.getOrder(orderId);

                // Assert
                assertNotNull(response.orderItems());
                assertEquals(1, response.orderItems().size());

                OrderItemResponseDTO item = response.orderItems().iterator().next();
                assertEquals(productId, item.productId());
                assertEquals("Mechanical Keyboard", item.productTitle());
                assertEquals(1, item.quantity());
                assertEquals(8000L, item.lockedPrice());
            }

            @Test
            @DisplayName("Should return order with multiple order items correctly")
            void shouldReturnOrder_withMultipleOrderItems() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId1 = UUID.randomUUID();
                UUID productId2 = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product headphones = buildActiveProduct(productId1, "Wireless Headphones", 50, 2500L);
                Product keyboard = buildActiveProduct(productId2, "Mechanical Keyboard", 30, 8000L);

                // total = (2 × 2500) + (1 × 8000) = 13000
                Order order = new Order(customer, Status.PENDING, 13000L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey("idem-key-multi");
                order.addOrderItem(new OrderItem(order, headphones, 2, 2500L));
                order.addOrderItem(new OrderItem(order, keyboard, 1, 8000L));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act
                OrderResponseDTO response = orderService.getOrder(orderId);

                // Assert
                assertEquals(13000L, response.totalPrice());
                assertEquals(2, response.orderItems().size());
            }

            @Test
            @DisplayName("Should return order for any valid status (DELIVERED)")
            void shouldReturnOrder_forDeliveredStatus() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "USB Cable", 100, 500L);

                Order order = new Order(customer, Status.DELIVERED, 1500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey("idem-key-delivered");
                order.addOrderItem(new OrderItem(order, product, 3, 500L));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act
                OrderResponseDTO response = orderService.getOrder(orderId);

                // Assert
                assertEquals(Status.DELIVERED, response.status());
                assertEquals(1500L, response.totalPrice());
            }

            @Test
            @DisplayName("Should check queries order")
            void shouldQueriesOrder() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "USB Cable", 100, 500L);

                Order order = new Order(customer, Status.PENDING, 1500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey("idem-key");
                order.addOrderItem(new OrderItem(order, product, 3, 500L));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act
                orderService.getOrder(orderId);

                // Assert — verify queries order
                var inOrder = inOrder(customerRepository, orderRepository);

                inOrder.verify(customerRepository).findByEmailWithCart(VALID_EMAIL.toLowerCase());
                inOrder.verify(orderRepository).findByCustomerIdAndOrderId(customerId, orderId);
            }
        }

        // ==================== getOrder() — CUSTOMER NOT FOUND ====================

        @Nested
        @DisplayName("getOrder() — Customer not found")
        class GetOrderCustomerNotFound {

            @Test
            @DisplayName("Should throw CustomerNotFoundException when the authenticated customer does not exist")
            void shouldThrowCustomerNotFoundException_whenCustomerDoesNotExist() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                // Act & Assert
                CustomerNotFoundException exception = assertThrows(
                        CustomerNotFoundException.class,
                        () -> orderService.getOrder(UUID.randomUUID())
                );
                assertEquals("Profile not found", exception.getMessage());
            }

            @Test
            @DisplayName("Should not interact with orderRepository when customer is not found")
            void shouldNotInteractWithOrderRepository_whenCustomerIsNotFound() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                // Act & Assert
                assertThrows(CustomerNotFoundException.class,
                        () -> orderService.getOrder(UUID.randomUUID()));

                verifyNoInteractions(orderRepository);
            }
        }

        // ==================== getOrder() — ORDER NOT FOUND ====================

        @Nested
        @DisplayName("getOrder() — Order not found")
        class GetOrderNotFound {

            @Test
            @DisplayName("Should throw OrderNotFoundException when order does not exist for the customer")
            void shouldThrowOrderNotFoundException_whenOrderDoesNotExist() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.empty());

                // Act & Assert
                OrderNotFoundException exception = assertThrows(
                        OrderNotFoundException.class,
                        () -> orderService.getOrder(orderId)
                );
                assertEquals("Order not found", exception.getMessage());
            }
        }

        // ==================== getOrder() — EMAIL LOWERCASING ====================

        @Nested
        @DisplayName("getOrder() — Email lowercasing")
        class GetOrderEmailLowercasing {

            @Test
            @DisplayName("Should lowercase the email before looking up the customer")
            void shouldLowercaseEmail_beforeLookingUpCustomer() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey("idem-key-lowercase");
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                // Simulate an uppercase email coming from the authentication context
                stubAuthenticatedUser("JOHN.DOE@EXAMPLE.COM");
                when(customerRepository.findByEmailWithCart("john.doe@example.com")).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act
                OrderResponseDTO response = orderService.getOrder(orderId);

                // Assert
                assertNotNull(response);
                verify(customerRepository).findByEmailWithCart("john.doe@example.com");
            }

            @Test
            @DisplayName("Should lowercase a mixed-case email before looking up the customer")
            void shouldLowercaseMixedCaseEmail_beforeLookingUpCustomer() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Laptop Stand", 20, 4000L);

                Order order = new Order(customer, Status.PENDING, 4000L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey("idem-key-mixed");
                order.addOrderItem(new OrderItem(order, product, 1, 4000L));

                stubAuthenticatedUser("John.Doe@Example.COM");
                when(customerRepository.findByEmailWithCart("john.doe@example.com")).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act
                OrderResponseDTO response = orderService.getOrder(orderId);

                // Assert
                assertNotNull(response);
                verify(customerRepository).findByEmailWithCart("john.doe@example.com");
            }
        }

        // ==================== getOrder() — SECURITY CONTEXT FAILURES ====================

        @Nested
        @DisplayName("getOrder() — Security context failures")
        class GetOrderSecurityContextFailures {

            @Test
            @DisplayName("Should throw NullPointerException when authentication is null")
            void shouldThrowNullPointerException_whenAuthenticationIsNull() {
                // Arrange
                SecurityContext securityContext = mock(SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(null);
                SecurityContextHolder.setContext(securityContext);

                // Act & Assert
                assertThrows(NullPointerException.class,
                        () -> orderService.getOrder(UUID.randomUUID()));

                verifyNoInteractions(orderRepository);
                verifyNoInteractions(customerRepository);
            }
        }

        // ==================== getOrder() — SERVER FAILURES ====================

        @Nested
        @DisplayName("getOrder() — Server failures")
        class GetOrderServerFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when findByEmailWithCart throws")
            void shouldPropagateException_whenFindByEmailWithCartThrowsRuntimeException() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL))
                        .thenThrow(new RuntimeException("Database unavailable"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> orderService.getOrder(UUID.randomUUID())
                );
                assertEquals("Database unavailable", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate RuntimeException when findByCustomerIdAndOrderId throws")
            void shouldPropagateException_whenFindByCustomerIdAndOrderIdThrowsRuntimeException() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId))
                        .thenThrow(new RuntimeException("Database timeout"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> orderService.getOrder(orderId)
                );
                assertEquals("Database timeout", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when findByEmailWithCart throws")
            void shouldPropagateException_whenFindByEmailWithCartThrowsIllegalStateException() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL))
                        .thenThrow(new IllegalStateException("Connection pool exhausted"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> orderService.getOrder(UUID.randomUUID())
                );
                assertEquals("Connection pool exhausted", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when findByCustomerIdAndOrderId throws")
            void shouldPropagateException_whenFindByCustomerIdAndOrderIdThrowsIllegalStateException() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId))
                        .thenThrow(new IllegalStateException("Database connection lost"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> orderService.getOrder(orderId)
                );
                assertEquals("Database connection lost", exception.getMessage());
            }
        }

        // ==================== getOrder() — REPOSITORY INTERACTION VERIFICATION ====================

        @Nested
        @DisplayName("getOrder() — Repository interaction verification")
        class GetOrderRepositoryInteraction {

            @Test
            @DisplayName("Should call findByEmailWithCart exactly once")
            void shouldCallFindByEmailWithCartExactlyOnce() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey("idem-key-verify");
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act
                orderService.getOrder(orderId);

                // Assert
                verify(customerRepository).findByEmailWithCart(VALID_EMAIL);
                verifyNoMoreInteractions(customerRepository);
            }

            @Test
            @DisplayName("Should call findByCustomerIdAndOrderId with the correct customer ID and order ID")
            void shouldCallFindByCustomerIdAndOrderId_withCorrectIds() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey("idem-key-verify-ids");
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act
                orderService.getOrder(orderId);

                // Assert
                verify(orderRepository).findByCustomerIdAndOrderId(customerId, orderId);
            }

            @Test
            @DisplayName("Should not interact with cartItemRepository")
            void shouldNotInteractWithCartItemRepository() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey("idem-key-no-cart");
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act
                orderService.getOrder(orderId);

                // Assert — getOrder is a read-only operation, should never touch the cart
                verifyNoInteractions(cartItemRepository);
            }

            @Test
            @DisplayName("Should call findByCustomerIdAndOrderId exactly once")
            void shouldCallFindByCustomerIdAndOrderIdExactlyOnce() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey("idem-key-once");
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act
                orderService.getOrder(orderId);

                // Assert
                verify(orderRepository).findByCustomerIdAndOrderId(customerId, orderId);
                verifyNoMoreInteractions(orderRepository);
            }
        }
    }

    // ==================== cancelOrder() ====================
    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrder {

        // ==================== cancelOrder() — SUCCESS CASES ====================

        @Nested
        @DisplayName("cancelOrder() — Success cases")
        class CancelOrderSuccess {

            @Test
            @DisplayName("Should return OrderResponseDTO with CANCELED status when a PENDING order is canceled")
            void shouldReturnOrderResponseDTO_withCanceledStatus_whenPendingOrderIsCanceled() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                OrderResponseDTO response = orderService.cancelOrder(orderId);

                // Assert
                assertNotNull(response);
                assertEquals(orderId, response.id());
                assertEquals(Status.CANCELED, response.status());
                assertEquals(7500L, response.totalPrice());
                assertEquals(VALID_SHIPPING_ADDRESS, response.shippingAddress());
            }

            @Test
            @DisplayName("Should return order items in the response DTO")
            void shouldReturnOrderItems_inResponseDTO() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Mechanical Keyboard", 30, 8000L);

                Order order = new Order(customer, Status.PENDING, 8000L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey("idem-key-cancel-items");
                order.addOrderItem(new OrderItem(order, product, 1, 8000L));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                OrderResponseDTO response = orderService.cancelOrder(orderId);

                // Assert
                assertNotNull(response.orderItems());
                assertEquals(1, response.orderItems().size());

                OrderItemResponseDTO item = response.orderItems().iterator().next();
                assertEquals(productId, item.productId());
                assertEquals("Mechanical Keyboard", item.productTitle());
                assertEquals(1, item.quantity());
                assertEquals(8000L, item.lockedPrice());
            }

            @Test
            @DisplayName("Should save an InventoryOutbox entry for the canceled order")
            void shouldSaveInventoryOutboxEntry_forCanceledOrder() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                orderService.cancelOrder(orderId);

                // Assert
                verify(inventoryOutboxRepository).save(any(InventoryOutbox.class));
                verifyNoMoreInteractions(inventoryOutboxRepository);
            }

            @Test
            @DisplayName("Should set order status to CANCELED before saving")
            void shouldSetOrderStatusToCanceled_beforeSaving() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
                    Order capturedOrder = inv.getArgument(0);

                    // Verify the status was set to CANCELED before save
                    assertEquals(Status.CANCELED, capturedOrder.getStatus());

                    return capturedOrder;
                });

                // Act
                orderService.cancelOrder(orderId);

                // Assert
                verify(orderRepository).saveAndFlush(any(Order.class));
            }

            @Test
            @DisplayName("Should call saveAndFlush on the order repository exactly once")
            void shouldCallSaveAndFlushExactlyOnce() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                orderService.cancelOrder(orderId);

                // Assert
                verify(orderRepository).saveAndFlush(order);
                verifyNoMoreInteractions(orderRepository);
            }

            @Test
            @DisplayName("Should check queries order")
            void shouldQueriesOrder() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                orderService.cancelOrder(orderId);

                // Assert — verify queries order
                var inOrder = inOrder(customerRepository, orderRepository, inventoryOutboxRepository);

                inOrder.verify(customerRepository).findByEmailWithCart(VALID_EMAIL.toLowerCase());
                inOrder.verify(orderRepository).findByCustomerIdAndOrderId(customerId, orderId);
                inOrder.verify(inventoryOutboxRepository).save(any(InventoryOutbox.class));
                inOrder.verify(orderRepository).saveAndFlush(order);
            }
        }

        // ==================== cancelOrder() — EMAIL LOWERCASING ====================

        @Nested
        @DisplayName("cancelOrder() — Email lowercasing")
        class CancelOrderEmailLowercasing {

            @Test
            @DisplayName("Should lowercase the email before looking up the customer")
            void shouldLowercaseEmail_beforeLookingUpCustomer() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                // Simulate an uppercase email coming from the authentication context
                stubAuthenticatedUser("JOHN.DOE@EXAMPLE.COM");
                when(customerRepository.findByEmailWithCart("john.doe@example.com")).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                OrderResponseDTO response = orderService.cancelOrder(orderId);

                // Assert
                assertNotNull(response);
                verify(customerRepository).findByEmailWithCart("john.doe@example.com");
            }
        }

        // ==================== cancelOrder() — NOT FOUND CASES (CustomerNotFoundException) ====================

        @Nested
        @DisplayName("cancelOrder() — Customer not found")
        class CancelOrderCustomerNotFound {

            @Test
            @DisplayName("Should throw CustomerNotFoundException when the authenticated customer does not exist")
            void shouldThrowCustomerNotFoundException_whenCustomerDoesNotExist() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                // Act & Assert
                CustomerNotFoundException exception = assertThrows(
                        CustomerNotFoundException.class,
                        () -> orderService.cancelOrder(UUID.randomUUID())
                );
                assertEquals("Profile not found", exception.getMessage());
            }

            @Test
            @DisplayName("Should not interact with orderRepository or inventoryOutboxRepository when customer is not found")
            void shouldNotInteractWithRepositories_whenCustomerIsNotFound() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                // Act
                assertThrows(CustomerNotFoundException.class,
                        () -> orderService.cancelOrder(UUID.randomUUID()));

                // Assert
                verifyNoInteractions(orderRepository);
                verifyNoInteractions(inventoryOutboxRepository);
            }
        }

        // ==================== cancelOrder() — NOT FOUND CASES (OrderNotFoundException) ====================

        @Nested
        @DisplayName("cancelOrder() — Order not found")
        class CancelOrderNotFound {

            @Test
            @DisplayName("Should throw OrderNotFoundException when order does not exist for the customer")
            void shouldThrowOrderNotFoundException_whenOrderDoesNotExist() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.empty());

                // Act & Assert
                OrderNotFoundException exception = assertThrows(
                        OrderNotFoundException.class,
                        () -> orderService.cancelOrder(orderId)
                );
                assertEquals("Order not found", exception.getMessage());
            }

            @Test
            @DisplayName("Should not save anything when order is not found")
            void shouldNotSaveAnything_whenOrderNotFound() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.empty());

                // Act
                assertThrows(OrderNotFoundException.class,
                        () -> orderService.cancelOrder(orderId));

                // Assert
                verify(orderRepository, never()).saveAndFlush(any(Order.class));
                verifyNoInteractions(inventoryOutboxRepository);
            }
        }

        // ==================== cancelOrder() — ORDER STATE CONFLICT (non-PENDING) ====================

        @Nested
        @DisplayName("cancelOrder() — Order state conflict (non-PENDING)")
        class CancelOrderStateConflict {

            @Test
            @DisplayName("Should throw OrderStateConflictException when order status is SHIPPED")
            void shouldThrowOrderStateConflictException_whenOrderIsShipped() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.SHIPPED, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act & Assert
                OrderStateConflictException exception = assertThrows(
                        OrderStateConflictException.class,
                        () -> orderService.cancelOrder(orderId)
                );
                assertEquals("Order cannot be canceled because it has already been shipped", exception.getMessage());
            }

            @Test
            @DisplayName("Should throw OrderStateConflictException when order status is DELIVERED")
            void shouldThrowOrderStateConflictException_whenOrderIsDelivered() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.DELIVERED, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act & Assert
                OrderStateConflictException exception = assertThrows(
                        OrderStateConflictException.class,
                        () -> orderService.cancelOrder(orderId)
                );
                assertEquals("Order cannot be canceled because it has already been delivered", exception.getMessage());
            }

            @Test
            @DisplayName("Should throw OrderStateConflictException when order status is already CANCELED")
            void shouldThrowOrderStateConflictException_whenOrderIsAlreadyCanceled() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.CANCELED, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act & Assert
                OrderStateConflictException exception = assertThrows(
                        OrderStateConflictException.class,
                        () -> orderService.cancelOrder(orderId)
                );
                assertEquals("Order cannot be canceled because it has already been canceled", exception.getMessage());
            }

            @Test
            @DisplayName("Should include the lowercase status name in the exception message")
            void shouldIncludeLowercaseStatusName_inExceptionMessage() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.SHIPPED, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act & Assert
                OrderStateConflictException exception = assertThrows(
                        OrderStateConflictException.class,
                        () -> orderService.cancelOrder(orderId)
                );
                // Verify the status name is lowercased (not "SHIPPED" but "shipped")
                assertTrue(exception.getMessage().contains("shipped"));
                assertFalse(exception.getMessage().contains("SHIPPED"));
            }

            @Test
            @DisplayName("Should not save or update anything when order is not in PENDING status")
            void shouldNotSaveOrUpdate_whenOrderIsNotPending() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.DELIVERED, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                // Act
                assertThrows(OrderStateConflictException.class,
                        () -> orderService.cancelOrder(orderId));

                // Assert — no save operations should occur
                verify(orderRepository, never()).saveAndFlush(any(Order.class));
                verifyNoInteractions(inventoryOutboxRepository);
            }
        }

        // ==================== cancelOrder() — CONFLICT / RACE CONDITION (DataIntegrityViolation) ====================

        @Nested
        @DisplayName("cancelOrder() — Conflict / race condition handling")
        class CancelOrderConflict {

            @Test
            @DisplayName("Should throw OrderStateConflictException when DataIntegrityViolationException occurs on outbox save")
            void shouldThrowOrderStateConflictException_whenDuplicateOutboxEntry() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                when(inventoryOutboxRepository.save(any(InventoryOutbox.class)))
                        .thenThrow(new DataIntegrityViolationException("Duplicate outbox entry for order"));

                // Act & Assert
                OrderStateConflictException exception = assertThrows(
                        OrderStateConflictException.class,
                        () -> orderService.cancelOrder(orderId)
                );
                assertEquals("Order already has been canceled. Please refresh", exception.getMessage());
            }
        }

        // ==================== cancelOrder() — SECURITY CONTEXT FAILURES ====================

        @Nested
        @DisplayName("cancelOrder() — Security context failures")
        class CancelOrderSecurityContextFailures {

            @Test
            @DisplayName("Should throw NullPointerException when authentication is null")
            void shouldThrowNullPointerException_whenAuthenticationIsNull() {
                // Arrange — SecurityContext exists but getAuthentication() returns null
                SecurityContext securityContext = mock(SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(null);
                SecurityContextHolder.setContext(securityContext);

                // Act & Assert — calling getName() on null Authentication throws NPE
                assertThrows(NullPointerException.class,
                        () -> orderService.cancelOrder(UUID.randomUUID()));

                // Verify no business operations occurred
                verifyNoInteractions(customerRepository);
                verifyNoInteractions(orderRepository);
                verifyNoInteractions(inventoryOutboxRepository);
            }
        }

        // ==================== cancelOrder() — SERVER FAILURE CASES ====================

        @Nested
        @DisplayName("cancelOrder() — Server failure simulation")
        class CancelOrderServerFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when findByEmailWithCart throws")
            void shouldPropagateException_whenFindByEmailWithCartThrowsRuntimeException() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL))
                        .thenThrow(new RuntimeException("Database unavailable"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> orderService.cancelOrder(UUID.randomUUID())
                );
                assertEquals("Database unavailable", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate RuntimeException when findByCustomerIdAndOrderId throws")
            void shouldPropagateException_whenFindByCustomerIdAndOrderIdThrowsRuntimeException() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId))
                        .thenThrow(new RuntimeException("Database timeout"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> orderService.cancelOrder(orderId)
                );
                assertEquals("Database timeout", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate RuntimeException when inventoryOutboxRepository.save throws a non-DIV exception")
            void shouldPropagateException_whenInventoryOutboxSaveThrowsRuntimeException() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));

                when(inventoryOutboxRepository.save(any(InventoryOutbox.class)))
                        .thenThrow(new RuntimeException("Outbox table unavailable"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> orderService.cancelOrder(orderId)
                );
                assertEquals("Outbox table unavailable", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when findByEmailWithCart throws")
            void shouldPropagateException_whenFindByEmailWithCartThrowsIllegalStateException() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL))
                        .thenThrow(new IllegalStateException("Connection pool exhausted"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> orderService.cancelOrder(UUID.randomUUID())
                );
                assertEquals("Connection pool exhausted", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when findByCustomerIdAndOrderId throws")
            void shouldPropagateException_whenFindByCustomerIdAndOrderIdThrowsIllegalStateException() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId))
                        .thenThrow(new IllegalStateException("Database connection lost"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> orderService.cancelOrder(orderId)
                );
                assertEquals("Database connection lost", exception.getMessage());
            }
        }

        // ==================== cancelOrder() — REPOSITORY INTERACTION VERIFICATION ====================

        @Nested
        @DisplayName("cancelOrder() — Repository interaction verification")
        class CancelOrderRepositoryInteraction {

            @Test
            @DisplayName("Should call findByEmailWithCart exactly once")
            void shouldCallFindByEmailWithCartExactlyOnce() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                orderService.cancelOrder(orderId);

                // Assert
                verify(customerRepository).findByEmailWithCart(VALID_EMAIL);
                verifyNoMoreInteractions(customerRepository);
            }

            @Test
            @DisplayName("Should call findByCustomerIdAndOrderId with the correct customer ID and order ID")
            void shouldCallFindByCustomerIdAndOrderId_withCorrectIds() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                orderService.cancelOrder(orderId);

                // Assert
                verify(orderRepository).findByCustomerIdAndOrderId(customerId, orderId);
            }

            @Test
            @DisplayName("Should not interact with cartItemRepository")
            void shouldNotInteractWithCartItemRepository() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID orderId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, UUID.randomUUID());
                Product product = buildActiveProduct(productId, "Wireless Headphones", PRODUCT_STOCK, PRODUCT_PRICE);

                Order order = new Order(customer, Status.PENDING, 7500L, VALID_SHIPPING_ADDRESS);
                EntityTestUtil.setId(order, orderId);
                order.setIdempotencyKey(VALID_IDEMPOTENCY_KEY);
                order.addOrderItem(new OrderItem(order, product, CART_ITEM_QUANTITY, PRODUCT_PRICE));

                stubAuthenticatedUser(VALID_EMAIL);
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(orderRepository.findByCustomerIdAndOrderId(customerId, orderId)).thenReturn(Optional.of(order));
                when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

                // Act
                orderService.cancelOrder(orderId);

                // Assert — cancelOrder should never touch the cart
                verifyNoInteractions(cartItemRepository);
            }
        }
    }
}
