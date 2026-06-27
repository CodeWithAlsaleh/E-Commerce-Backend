package com.sivan.ecommerce.service.order;

import com.sivan.ecommerce.dto.order.OrderItemResponseDTO;
import com.sivan.ecommerce.dto.order.OrderRequestDTO;
import com.sivan.ecommerce.dto.order.OrderResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.cart.Cart;
import com.sivan.ecommerce.entity.cart.CartItem;
import com.sivan.ecommerce.entity.customer.Customer;
import com.sivan.ecommerce.entity.order.Order;
import com.sivan.ecommerce.entity.order.OrderItem;
import com.sivan.ecommerce.entity.order.Status;
import com.sivan.ecommerce.entity.product.Product;
import com.sivan.ecommerce.exception.CustomerNotFoundException;
import com.sivan.ecommerce.exception.InsufficientStockException;
import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.exception.ResourceConflictException;
import com.sivan.ecommerce.repository.cart.CartItemRepository;
import com.sivan.ecommerce.repository.customer.CustomerRepository;
import com.sivan.ecommerce.repository.order.OrderRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

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
                verify(orderRepository, never()).save(any(Order.class));
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
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

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
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

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
                verify(orderRepository).save(any(Order.class));
            }

            @Test
            @DisplayName("Should set the idempotency key on the order before saving")
            void shouldSetIdempotencyKeyOnOrder_beforeSaving() {
                // Arrange
                stubPlaceOrderBase();

                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                    Order capturedOrder = inv.getArgument(0);

                    // Verify the idempotency key was set before save
                    assertEquals(VALID_IDEMPOTENCY_KEY, capturedOrder.getIdempotencyKey());

                    return capturedOrder;
                });

                // Act
                orderService.placeOrder(VALID_IDEMPOTENCY_KEY, validRequest());

                // Assert
                verify(orderRepository).save(any(Order.class));
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
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

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
                inOrder.verify(orderRepository).save(any(Order.class));
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
                inOrder.verify(orderRepository).save(any(Order.class));
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
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

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
                verify(orderRepository, never()).save(any(Order.class));
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
                verify(orderRepository, never()).save(any(Order.class));
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
                verify(orderRepository, never()).save(any(Order.class));
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

                when(orderRepository.save(any(Order.class)))
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

                when(orderRepository.save(any(Order.class)))
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

                when(orderRepository.save(any(Order.class)))
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

                when(orderRepository.save(any(Order.class)))
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
                verify(orderRepository, never()).save(any(Order.class));
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

                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
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
                verify(orderRepository).save(any(Order.class));
            }

            @Test
            @DisplayName("Should lock the product price at order time (snapshot pricing)")
            void shouldLockProductPriceAtOrderTime() {
                // Arrange
                stubPlaceOrderBase();

                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
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
                verify(orderRepository).save(any(Order.class));
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
                when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

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
}
