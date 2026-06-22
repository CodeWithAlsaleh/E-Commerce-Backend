package com.sivan.ecommerce.service.cart;

import com.sivan.ecommerce.dto.cart.CartItemRequestDTO;
import com.sivan.ecommerce.dto.cart.CartItemResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.cart.Cart;
import com.sivan.ecommerce.entity.cart.CartItem;
import com.sivan.ecommerce.entity.customer.Customer;
import com.sivan.ecommerce.entity.product.Product;
import com.sivan.ecommerce.exception.CustomerNotFoundException;
import com.sivan.ecommerce.exception.InsufficientStockException;
import com.sivan.ecommerce.exception.ProductNotFoundException;
import com.sivan.ecommerce.repository.cart.CartItemRepository;
import com.sivan.ecommerce.repository.customer.CustomerRepository;
import com.sivan.ecommerce.repository.product.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CartItemServiceImpl}.
 * Only the service layer is tested — repositories are mocked.
 * No Spring context is loaded, keeping tests fast and isolated.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartItemServiceImpl")
class CartItemServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CartItemServiceImpl cartItemService;

    // ======================== Helper Constants ========================

    private static final String VALID_EMAIL = "john.doe@example.com";
    private static final String VALID_PRODUCT_TITLE = "Wireless Bluetooth Headphones";
    private static final int VALID_PRODUCT_STOCK = 50;
    private static final int VALID_REQUEST_QUANTITY = 3;

    // ==================== createCartItem() ====================
    @Nested
    @DisplayName("createCartItem()")
    class CreateCartItem {

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
         * Builds a valid {@link CartItemRequestDTO} with all fields populated.
         */
        private CartItemRequestDTO validRequest(UUID productId) {
            return new CartItemRequestDTO(productId, VALID_REQUEST_QUANTITY);
        }

        /**
         * Creates an active {@link Product} with an ID assigned via reflection,
         * mimicking Hibernate's @UuidGenerator behavior on persist.
         */
        private Product buildActiveProduct(UUID id, int stock) {
            Product product = new Product(
                    VALID_PRODUCT_TITLE, "A great product",
                    stock, 7999L, "USD",
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
         * Creates a saved {@link CartItem} with product and cart relationships
         * and an ID assigned via reflection.
         */
        private CartItem buildSavedCartItem(UUID cartItemId, Product product, Cart cart, int quantity) {
            CartItem cartItem = new CartItem(quantity);
            cartItem.setProduct(product);
            cartItem.setCart(cart);
            EntityTestUtil.setId(cartItem, cartItemId);

            return cartItem;
        }

        /**
         * Sets up the common mocking for a successful createCartItem() flow
         * where the cart item does NOT already exist (brand-new item):
         * - product exists and is active
         * - authenticated user email is stubbed
         * - customer with cart is found
         * - no existing cart item for the product
         * - repository.save() returns the saved cart item
         */
        private StubResult stubCreateNewCartItemSuccess(int requestQuantity) {
            UUID productId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            UUID cartId = UUID.randomUUID();
            UUID cartItemId = UUID.randomUUID();

            Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
            Customer customer = buildCustomerWithCart(customerId, cartId);

            stubAuthenticatedUser(VALID_EMAIL);

            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
            when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());

            CartItem savedCartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), requestQuantity);
            when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedCartItem);

            return new StubResult(productId, customerId, cartId, cartItemId, product, customer);
        }

        /**
         * Holds references to entities created during test setup,
         * so tests can assert against them without re-querying.
         */
        private record StubResult(UUID productId, UUID customerId, UUID cartId,
                                  UUID cartItemId, Product product, Customer customer) {
        }

        // ==================== createCartItem() — SUCCESS CASES ====================

        @Nested
        @DisplayName("createCartItem() — Success cases (new cart item)")
        class CreateCartItemNewSuccess {

            @Test
            @DisplayName("Should return a valid CartItemResponseDTO when adding a new product to cart")
            void shouldReturnCartItemResponseDTO_whenAddingNewProductToCart() {
                // Arrange
                StubResult stub = stubCreateNewCartItemSuccess(VALID_REQUEST_QUANTITY);
                CartItemRequestDTO request = validRequest(stub.productId());

                // Act
                CartItemResponseDTO response = cartItemService.createCartItem(request);

                // Assert
                assertNotNull(response);
                assertEquals(stub.productId(), response.productId());
                assertEquals(VALID_PRODUCT_TITLE, response.productTitle());
                assertEquals(7999L, response.price());
                assertTrue(response.isActive());
                assertEquals(VALID_REQUEST_QUANTITY, response.quantity());
            }

            @Test
            @DisplayName("Should save the cart item exactly once via the repository")
            void shouldCallRepositorySaveExactlyOnce() {
                // Arrange
                StubResult stub = stubCreateNewCartItemSuccess(VALID_REQUEST_QUANTITY);

                // Act
                cartItemService.createCartItem(validRequest(stub.productId()));

                // Assert
                verify(cartItemRepository).save(any(CartItem.class));
                verifyNoMoreInteractions(cartItemRepository);
            }

            @Test
            @DisplayName("Should link the product and cart to the new cart item before saving")
            void shouldLinkProductAndCartToNewCartItem() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();

                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());

                CartItem savedCartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), VALID_REQUEST_QUANTITY);
                when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> {
                    CartItem captured = inv.getArgument(0);

                    // Verify the product was set on the new cart item
                    assertNotNull(captured.getProduct(), "CartItem should have a Product assigned");
                    assertEquals(productId, captured.getProduct().getId());

                    // Verify the cart was set on the new cart item (via Cart.addCartItem)
                    assertNotNull(captured.getCart(), "CartItem should have a Cart assigned");
                    assertEquals(cartId, captured.getCart().getId());

                    return savedCartItem;
                });

                // Act
                cartItemService.createCartItem(validRequest(productId));

                // Assert
                verify(cartItemRepository).save(any(CartItem.class));
            }

            @Test
            @DisplayName("Should verify the execution order: findProduct → findCustomer → findCartItem → save")
            void shouldVerifyExecutionOrder() {
                // Arrange
                StubResult stub = stubCreateNewCartItemSuccess(VALID_REQUEST_QUANTITY);

                // Act
                cartItemService.createCartItem(validRequest(stub.productId()));

                // Assert — verify the order of repository interactions
                var inOrderProduct = inOrder(productRepository);
                inOrderProduct.verify(productRepository).findById(stub.productId());

                var inOrderCustomer = inOrder(customerRepository);
                inOrderCustomer.verify(customerRepository).findByEmailWithCart(VALID_EMAIL);

                var inOrderCartItem = inOrder(cartItemRepository);
                inOrderCartItem.verify(cartItemRepository).findCartItem(stub.productId(), stub.cartId());
                inOrderCartItem.verify(cartItemRepository).save(any(CartItem.class));
            }

            @Test
            @DisplayName("Should set the quantity to the requested amount for a brand-new cart item")
            void shouldSetCorrectQuantity_forNewCartItem() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());

                when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> {
                    CartItem captured = inv.getArgument(0);

                    // A new CartItem starts at 0 (from new CartItem(0)), then gets quantity added
                    assertEquals(VALID_REQUEST_QUANTITY, captured.getQuantity(),
                            "New cart item quantity should equal the requested quantity");

                    return buildSavedCartItem(UUID.randomUUID(), product, customer.getCart(), captured.getQuantity());
                });

                // Act
                cartItemService.createCartItem(validRequest(productId));

                // Assert
                verify(cartItemRepository).save(any(CartItem.class));
            }
        }

        // ==================== createCartItem() — SUCCESS CASES (existing cart item) ====================

        @Nested
        @DisplayName("createCartItem() — Success cases (existing cart item, quantity increment)")
        class CreateCartItemExistingSuccess {

            @Test
            @DisplayName("Should increment quantity when product already exists in cart")
            void shouldIncrementQuantity_whenProductAlreadyExistsInCart() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();
                int existingQuantity = 2;

                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                CartItem existingCartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), existingQuantity);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.of(existingCartItem));

                int expectedTotal = existingQuantity + VALID_REQUEST_QUANTITY;
                CartItem updatedCartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), expectedTotal);
                when(cartItemRepository.save(any(CartItem.class))).thenReturn(updatedCartItem);

                // Act
                CartItemResponseDTO response = cartItemService.createCartItem(validRequest(productId));

                // Assert
                assertNotNull(response);
                assertEquals(7999L, response.price());
                assertTrue(response.isActive());
                assertEquals(expectedTotal, response.quantity());
            }

            @Test
            @DisplayName("Should NOT re-link product or cart when cart item already exists")
            void shouldNotRelinkRelationships_whenCartItemAlreadyExists() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();
                int existingQuantity = 2;

                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                CartItem existingCartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), existingQuantity);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.of(existingCartItem));

                int expectedTotal = existingQuantity + VALID_REQUEST_QUANTITY;
                when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> {
                    CartItem captured = inv.getArgument(0);

                    // The existing managed entity is reused — same reference
                    assertSame(existingCartItem, captured,
                            "Should reuse the existing managed entity, not create a new one");

                    return buildSavedCartItem(cartItemId, product, customer.getCart(), expectedTotal);
                });

                // Act
                cartItemService.createCartItem(validRequest(productId));

                // Assert
                verify(cartItemRepository).save(any(CartItem.class));
            }

            @Test
            @DisplayName("Should add exactly to the boundary when existing + requested equals stock")
            void shouldSucceed_whenExistingPlusRequestedEqualsStock() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();
                int stock = 10;
                int existingQuantity = 7;
                int requestQuantity = 3; // 7 + 3 = 10 = stock (exact boundary)

                Product product = buildActiveProduct(productId, stock);
                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem existingCartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), existingQuantity);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.of(existingCartItem));

                CartItem savedCartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), stock);
                when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedCartItem);

                // Act
                CartItemResponseDTO response = cartItemService.createCartItem(
                        new CartItemRequestDTO(productId, requestQuantity)
                );

                // Assert
                assertNotNull(response);
                assertEquals(7999L, response.price());
                assertTrue(response.isActive());
                assertEquals(stock, response.quantity());
            }
        }

        // ==================== createCartItem() — NOT FOUND CASES (product) ====================

        @Nested
        @DisplayName("createCartItem() — Product not found cases")
        class CreateCartItemProductNotFound {

            @Test
            @DisplayName("Should throw ProductNotFoundException when product does not exist")
            void shouldThrowProductNotFoundException_whenProductDoesNotExist() {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productRepository.findById(productId)).thenReturn(Optional.empty());

                // Act & Assert
                ProductNotFoundException exception = assertThrows(
                        ProductNotFoundException.class,
                        () -> cartItemService.createCartItem(validRequest(productId))
                );
                assertEquals("Product not found", exception.getMessage());
            }

            @Test
            @DisplayName("Should throw ProductNotFoundException when product exists but is inactive")
            void shouldThrowProductNotFoundException_whenProductIsInactive() {
                // Arrange
                UUID productId = UUID.randomUUID();
                Product inactiveProduct = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
                inactiveProduct.setActive(false);

                when(productRepository.findById(productId)).thenReturn(Optional.of(inactiveProduct));

                // Act & Assert
                ProductNotFoundException exception = assertThrows(
                        ProductNotFoundException.class,
                        () -> cartItemService.createCartItem(validRequest(productId))
                );
                assertEquals("Product not found", exception.getMessage());
            }

            @Test
            @DisplayName("Should not interact with customer or cartItem repositories when product not found")
            void shouldNotInteractWithOtherRepos_whenProductNotFound() {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productRepository.findById(productId)).thenReturn(Optional.empty());

                // Act
                assertThrows(ProductNotFoundException.class,
                        () -> cartItemService.createCartItem(validRequest(productId)));

                // Assert
                verify(productRepository).findById(productId);
                verifyNoInteractions(customerRepository);
                verifyNoInteractions(cartItemRepository);
            }

            @Test
            @DisplayName("Should not interact with customer or cartItem repositories when product is inactive")
            void shouldNotInteractWithOtherRepos_whenProductIsInactive() {
                // Arrange
                UUID productId = UUID.randomUUID();
                Product inactiveProduct = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
                inactiveProduct.setActive(false);

                when(productRepository.findById(productId)).thenReturn(Optional.of(inactiveProduct));

                // Act
                assertThrows(ProductNotFoundException.class,
                        () -> cartItemService.createCartItem(validRequest(productId)));

                // Assert
                verify(productRepository).findById(productId);
                verifyNoInteractions(customerRepository);
                verifyNoInteractions(cartItemRepository);
            }
        }

        // ==================== createCartItem() — NOT FOUND CASES (customer) ====================

        @Nested
        @DisplayName("createCartItem() — Customer not found cases")
        class CreateCartItemCustomerNotFound {

            @Test
            @DisplayName("Should throw CustomerNotFoundException when customer profile not found")
            void shouldThrowCustomerNotFoundException_whenCustomerNotFound() {
                // Arrange
                UUID productId = UUID.randomUUID();
                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                // Act & Assert
                CustomerNotFoundException exception = assertThrows(
                        CustomerNotFoundException.class,
                        () -> cartItemService.createCartItem(validRequest(productId))
                );
                assertEquals("Profile not found", exception.getMessage());
            }

            @Test
            @DisplayName("Should not interact with cartItemRepository when customer not found")
            void shouldNotInteractWithCartItemRepo_whenCustomerNotFound() {
                // Arrange
                UUID productId = UUID.randomUUID();
                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                // Act
                assertThrows(CustomerNotFoundException.class,
                        () -> cartItemService.createCartItem(validRequest(productId)));

                // Assert
                verify(customerRepository).findByEmailWithCart(VALID_EMAIL);
                verifyNoInteractions(cartItemRepository);
            }
        }

        // ==================== createCartItem() — INSUFFICIENT STOCK CASES (conflict) ====================

        @Nested
        @DisplayName("createCartItem() — Insufficient stock cases")
        class CreateCartItemInsufficientStock {

            @Test
            @DisplayName("Should throw InsufficientStockException when requested quantity exceeds stock (new item)")
            void shouldThrowInsufficientStockException_whenQuantityExceedsStock_newItem() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                int stock = 5;
                int requestQuantity = 6; // exceeds stock

                Product product = buildActiveProduct(productId, stock);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());

                // Act & Assert
                InsufficientStockException exception = assertThrows(
                        InsufficientStockException.class,
                        () -> cartItemService.createCartItem(new CartItemRequestDTO(productId, requestQuantity))
                );
                assertEquals("Requested quantity is not available in stock", exception.getMessage());
            }

            @Test
            @DisplayName("Should throw InsufficientStockException when existing + requested quantity exceeds stock")
            void shouldThrowInsufficientStockException_whenCumulativeQuantityExceedsStock() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();
                int stock = 10;
                int existingQuantity = 8;
                int requestQuantity = 3; // 8 + 3 = 11 > 10

                Product product = buildActiveProduct(productId, stock);
                Customer customer = buildCustomerWithCart(customerId, cartId);
                CartItem existingCartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), existingQuantity);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.of(existingCartItem));

                // Act & Assert
                InsufficientStockException exception = assertThrows(
                        InsufficientStockException.class,
                        () -> cartItemService.createCartItem(new CartItemRequestDTO(productId, requestQuantity))
                );
                assertEquals("Requested quantity is not available in stock", exception.getMessage());
            }

            @Test
            @DisplayName("Should not call save when stock is insufficient")
            void shouldNotCallSave_whenStockIsInsufficient() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                int stock = 2;
                int requestQuantity = 5;

                Product product = buildActiveProduct(productId, stock);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());

                // Act
                assertThrows(InsufficientStockException.class,
                        () -> cartItemService.createCartItem(new CartItemRequestDTO(productId, requestQuantity)));

                // Assert
                verify(cartItemRepository, never()).save(any(CartItem.class));
            }

            @Test
            @DisplayName("Should throw InsufficientStockException when requesting exactly one more than stock (boundary)")
            void shouldThrowInsufficientStockException_whenOneOverStock() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                int stock = 10;
                int requestQuantity = 11; // exactly one over

                Product product = buildActiveProduct(productId, stock);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());

                // Act & Assert
                assertThrows(InsufficientStockException.class,
                        () -> cartItemService.createCartItem(new CartItemRequestDTO(productId, requestQuantity)));
            }
        }

        // ==================== createCartItem() — SECURITY CONTEXT FAILURES ====================

        @Nested
        @DisplayName("createCartItem() — Security context failures")
        class CreateCartItemSecurityContextFailures {

            @Test
            @DisplayName("Should throw NullPointerException when SecurityContext has no Authentication")
            void shouldThrowNPE_whenAuthenticationIsNull() {
                // Arrange — product is found and active, but no authentication present
                UUID productId = UUID.randomUUID();
                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
                when(productRepository.findById(productId)).thenReturn(Optional.of(product));

                SecurityContext securityContext = mock(SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(null);
                SecurityContextHolder.setContext(securityContext);

                // Act & Assert — calling getName() on null Authentication throws NPE
                assertThrows(NullPointerException.class,
                        () -> cartItemService.createCartItem(validRequest(productId)));

                // Verify customer and cartItem repos are never called
                verifyNoInteractions(customerRepository);
                verifyNoInteractions(cartItemRepository);
            }
        }

        // ==================== createCartItem() — SERVER FAILURE CASES ====================

        @Nested
        @DisplayName("createCartItem() — Server failure simulation")
        class CreateCartItemServerFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when productRepository.findById throws")
            void shouldPropagateException_whenProductRepositoryThrows() {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productRepository.findById(productId))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> cartItemService.createCartItem(validRequest(productId))
                );
                assertEquals("Database connection lost", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate RuntimeException when customerRepository.findByEmailWithCart throws")
            void shouldPropagateException_whenCustomerRepositoryThrows() {
                // Arrange
                UUID productId = UUID.randomUUID();
                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL))
                        .thenThrow(new RuntimeException("Database unavailable"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> cartItemService.createCartItem(validRequest(productId))
                );
                assertEquals("Database unavailable", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate RuntimeException when cartItemRepository.findCartItem throws")
            void shouldPropagateException_whenCartItemRepositoryThrows() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId))
                        .thenThrow(new RuntimeException("Database unavailable"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> cartItemService.createCartItem(validRequest(productId))
                );
                assertEquals("Database unavailable", exception.getMessage());
                verify(cartItemRepository, never()).save(any(CartItem.class));
            }

            @Test
            @DisplayName("Should propagate RuntimeException when cartItemRepository.save throws")
            void shouldPropagateException_whenCartItemRepositorySaveThrows() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());
                when(cartItemRepository.save(any(CartItem.class)))
                        .thenThrow(new RuntimeException("Persistence failure"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> cartItemService.createCartItem(validRequest(productId))
                );
                assertEquals("Persistence failure", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when cartItemRepository.save throws")
            void shouldPropagateIllegalStateException_whenCartItemRepositorySaveThrows() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());
                when(cartItemRepository.save(any(CartItem.class)))
                        .thenThrow(new IllegalStateException("Persistence failure"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> cartItemService.createCartItem(validRequest(productId))
                );
                assertEquals("Persistence failure", exception.getMessage());
            }
        }

        // ==================== createCartItem() — MAPPER INTERACTION ====================

        @Nested
        @DisplayName("createCartItem() — Mapper interaction verification")
        class CreateCartItemMapperVerification {

            @Test
            @DisplayName("Should pass the cart item with correct product to CartItemMapper via repository save")
            void shouldPassCartItemWithCorrectProductToMapper() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();

                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());

                CartItem savedCartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), VALID_REQUEST_QUANTITY);
                when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedCartItem);

                // Act
                CartItemResponseDTO response = cartItemService.createCartItem(validRequest(productId));

                // Assert — verify the mapper output matches what we expect
                assertEquals(productId, response.productId());
                assertEquals(VALID_PRODUCT_TITLE, response.productTitle());
                assertEquals(7999L, response.price());
                assertTrue(response.isActive());
                assertEquals(VALID_REQUEST_QUANTITY, response.quantity());
            }
        }

        // ==================== createCartItem() — EDGE CASES ====================

        @Nested
        @DisplayName("createCartItem() — Edge cases")
        class CreateCartItemEdgeCases {

            @Test
            @DisplayName("Should succeed when requesting exactly the full stock (boundary)")
            void shouldSucceed_whenRequestedQuantityEqualsStock() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();
                int stock = 10;
                int requestQuantity = 10; // exactly equals stock

                Product product = buildActiveProduct(productId, stock);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());

                CartItem savedCartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), requestQuantity);
                when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedCartItem);

                // Act
                CartItemResponseDTO response = cartItemService.createCartItem(
                        new CartItemRequestDTO(productId, requestQuantity)
                );

                // Assert
                assertNotNull(response);
                assertEquals(7999L, response.price());
                assertTrue(response.isActive());
                assertEquals(stock, response.quantity());
            }

            @Test
            @DisplayName("Should succeed when requesting minimum quantity of 1")
            void shouldSucceed_whenRequestedQuantityIsOne() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();

                Product product = buildActiveProduct(productId, VALID_PRODUCT_STOCK);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());

                CartItem savedCartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), 1);
                when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedCartItem);

                // Act
                CartItemResponseDTO response = cartItemService.createCartItem(
                        new CartItemRequestDTO(productId, 1)
                );

                // Assert
                assertNotNull(response);
                assertEquals(7999L, response.price());
                assertTrue(response.isActive());
                assertEquals(1, response.quantity());
            }

            @Test
            @DisplayName("Should succeed when product has stock of exactly 1 and requesting 1")
            void shouldSucceed_whenStockIsOneAndRequestingOne() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();
                int stock = 1;
                int requestQuantity = 1;

                Product product = buildActiveProduct(productId, stock);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());

                CartItem savedCartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), requestQuantity);
                when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedCartItem);

                // Act
                CartItemResponseDTO response = cartItemService.createCartItem(
                        new CartItemRequestDTO(productId, requestQuantity)
                );

                // Assert
                assertNotNull(response);
                assertEquals(7999L, response.price());
                assertTrue(response.isActive());
                assertEquals(1, response.quantity());
            }

            @Test
            @DisplayName("Should throw InsufficientStockException when product has zero stock")
            void shouldThrowInsufficientStockException_whenProductHasZeroStock() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                int stock = 0;

                Product product = buildActiveProduct(productId, stock);
                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(productRepository.findById(productId)).thenReturn(Optional.of(product));
                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findCartItem(productId, cartId)).thenReturn(Optional.empty());

                // Act & Assert
                assertThrows(InsufficientStockException.class,
                        () -> cartItemService.createCartItem(validRequest(productId)));
            }
        }
    }

    // ==================== getCartItems() ====================
    @Nested
    @DisplayName("getCartItems()")
    class GetCartItems {

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
        private Product buildActiveProduct(UUID id, String title, long price, int stock) {
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
         * Creates a saved {@link CartItem} with product and cart relationships
         * and an ID assigned via reflection.
         */
        private CartItem buildSavedCartItem(UUID cartItemId, Product product, Cart cart, int quantity) {
            CartItem cartItem = new CartItem(quantity);
            cartItem.setProduct(product);
            cartItem.setCart(cart);
            EntityTestUtil.setId(cartItem, cartItemId);

            return cartItem;
        }

        // ==================== getCartItems() — SUCCESS CASES ====================

        @Nested
        @DisplayName("getCartItems() — Success cases")
        class GetCartItemsSuccess {

            @Test
            @DisplayName("Should return a list with one CartItemResponseDTO when cart has a single item")
            void shouldReturnSingleItem_whenCartHasOneItem() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);
                Product product = buildActiveProduct(productId, VALID_PRODUCT_TITLE, 7999L, VALID_PRODUCT_STOCK);
                CartItem cartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), VALID_REQUEST_QUANTITY);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                // Act
                List<CartItemResponseDTO> result = cartItemService.getCartItems();

                // Assert
                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals(productId, result.getFirst().productId());
                assertEquals(VALID_PRODUCT_TITLE, result.getFirst().productTitle());
                assertEquals(7999L, result.getFirst().price());
                assertTrue(result.getFirst().isActive());
                assertEquals(VALID_REQUEST_QUANTITY, result.getFirst().quantity());
            }

            @Test
            @DisplayName("Should return a list with multiple CartItemResponseDTOs when cart has several items")
            void shouldReturnMultipleItems_whenCartHasSeveralItems() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);

                UUID productId1 = UUID.randomUUID();
                Product product1 = buildActiveProduct(productId1, "Wireless Bluetooth Headphones", 7999L, 50);
                CartItem cartItem1 = buildSavedCartItem(UUID.randomUUID(), product1, customer.getCart(), 2);

                UUID productId2 = UUID.randomUUID();
                Product product2 = buildActiveProduct(productId2, "USB-C Charging Cable", 1299L, 100);
                CartItem cartItem2 = buildSavedCartItem(UUID.randomUUID(), product2, customer.getCart(), 5);

                UUID productId3 = UUID.randomUUID();
                Product product3 = buildActiveProduct(productId3, "Laptop Stand", 4599L, 30);
                CartItem cartItem3 = buildSavedCartItem(UUID.randomUUID(), product3, customer.getCart(), 1);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId))
                        .thenReturn(List.of(cartItem1, cartItem2, cartItem3));

                // Act
                List<CartItemResponseDTO> result = cartItemService.getCartItems();

                // Assert
                assertNotNull(result);
                assertEquals(3, result.size());

                assertEquals(productId1, result.getFirst().productId());
                assertEquals("Wireless Bluetooth Headphones", result.getFirst().productTitle());
                assertEquals(7999L, result.getFirst().price());
                assertEquals(2, result.getFirst().quantity());

                assertEquals(productId2, result.get(1).productId());
                assertEquals("USB-C Charging Cable", result.get(1).productTitle());
                assertEquals(1299L, result.get(1).price());
                assertEquals(5, result.get(1).quantity());

                assertEquals(productId3, result.get(2).productId());
                assertEquals("Laptop Stand", result.get(2).productTitle());
                assertEquals(4599L, result.get(2).price());
                assertEquals(1, result.get(2).quantity());
            }

            @Test
            @DisplayName("Should return an empty list when cart has no items")
            void shouldReturnEmptyList_whenCartHasNoItems() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of());

                // Act
                List<CartItemResponseDTO> result = cartItemService.getCartItems();

                // Assert
                assertNotNull(result);
                assertTrue(result.isEmpty());
            }

            @Test
            @DisplayName("Should call customerRepository.findByEmailWithCart and cartItemRepository.findAllByCartIdWithProduct exactly once")
            void shouldCallRepositoriesExactlyOnce() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of());

                // Act
                cartItemService.getCartItems();

                // Assert
                verify(customerRepository).findByEmailWithCart(VALID_EMAIL);
                verifyNoMoreInteractions(customerRepository);

                verify(cartItemRepository).findAllByCartIdWithProduct(cartId);
                verifyNoMoreInteractions(cartItemRepository);
            }

            @Test
            @DisplayName("Should not interact with productRepository at all")
            void shouldNotInteractWithProductRepository() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of());

                // Act
                cartItemService.getCartItems();

                // Assert
                verifyNoInteractions(productRepository);
            }

            @Test
            @DisplayName("Should verify the execution order: findCustomer → findAllByCartIdWithProduct")
            void shouldVerifyExecutionOrder() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of());

                // Act
                cartItemService.getCartItems();

                // Assert — verify the order of repository interactions
                var inOrder = inOrder(customerRepository, cartItemRepository);
                inOrder.verify(customerRepository).findByEmailWithCart(VALID_EMAIL);
                inOrder.verify(cartItemRepository).findAllByCartIdWithProduct(cartId);
            }
        }

        // ==================== getCartItems() — NOT FOUND CASES (customer) ====================

        @Nested
        @DisplayName("getCartItems() — Customer not found cases")
        class GetCartItemsCustomerNotFound {

            @Test
            @DisplayName("Should throw CustomerNotFoundException when customer profile not found")
            void shouldThrowCustomerNotFoundException_whenCustomerNotFound() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                // Act & Assert
                CustomerNotFoundException exception = assertThrows(
                        CustomerNotFoundException.class,
                        () -> cartItemService.getCartItems()
                );
                assertEquals("Profile not found", exception.getMessage());
            }

            @Test
            @DisplayName("Should not interact with cartItemRepository when customer not found")
            void shouldNotInteractWithCartItemRepo_whenCustomerNotFound() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                // Act
                assertThrows(CustomerNotFoundException.class,
                        () -> cartItemService.getCartItems());

                // Assert
                verify(customerRepository).findByEmailWithCart(VALID_EMAIL);
                verifyNoInteractions(cartItemRepository);
            }

            @Test
            @DisplayName("Should not interact with productRepository when customer not found")
            void shouldNotInteractWithProductRepo_whenCustomerNotFound() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.empty());

                // Act
                assertThrows(CustomerNotFoundException.class,
                        () -> cartItemService.getCartItems());

                // Assert
                verifyNoInteractions(productRepository);
            }
        }

        // ==================== getCartItems() — SECURITY CONTEXT FAILURES ====================

        @Nested
        @DisplayName("getCartItems() — Security context failures")
        class GetCartItemsSecurityContextFailures {

            @Test
            @DisplayName("Should throw NullPointerException when SecurityContext has no Authentication")
            void shouldThrowNPE_whenAuthenticationIsNull() {
                // Arrange — no authentication present
                SecurityContext securityContext = mock(SecurityContext.class);
                when(securityContext.getAuthentication()).thenReturn(null);
                SecurityContextHolder.setContext(securityContext);

                // Act & Assert — calling getName() on null Authentication throws NPE
                assertThrows(NullPointerException.class,
                        () -> cartItemService.getCartItems());

                // Verify no repositories are called
                verifyNoInteractions(customerRepository);
                verifyNoInteractions(cartItemRepository);
                verifyNoInteractions(productRepository);
            }
        }

        // ==================== getCartItems() — SERVER FAILURE CASES ====================

        @Nested
        @DisplayName("getCartItems() — Server failure simulation")
        class GetCartItemsServerFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when customerRepository.findByEmailWithCart throws")
            void shouldPropagateException_whenCustomerRepositoryThrows() {
                // Arrange
                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> cartItemService.getCartItems()
                );
                assertEquals("Database connection lost", exception.getMessage());

                verifyNoInteractions(cartItemRepository);
            }

            @Test
            @DisplayName("Should propagate RuntimeException when cartItemRepository.findAllByCartIdWithProduct throws")
            void shouldPropagateException_whenCartItemRepositoryThrows() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId))
                        .thenThrow(new RuntimeException("Database unavailable"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> cartItemService.getCartItems()
                );
                assertEquals("Database unavailable", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when customerRepository.findByEmailWithCart throws")
            void shouldPropagateIllegalStateException_whenCustomerRepositoryThrows() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL))
                        .thenThrow(new IllegalStateException("Persistence failure"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> cartItemService.getCartItems()
                );
                assertEquals("Persistence failure", exception.getMessage());
                verifyNoInteractions(cartItemRepository);
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when cartItemRepository.findAllByCartIdWithProduct throws")
            void shouldPropagateIllegalStateException_whenCartItemRepositoryThrows() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId))
                        .thenThrow(new IllegalStateException("Persistence failure"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> cartItemService.getCartItems()
                );
                assertEquals("Persistence failure", exception.getMessage());
            }
        }

        // ==================== getCartItems() — MAPPER INTERACTION ====================

        @Nested
        @DisplayName("getCartItems() — Mapper interaction verification")
        class GetCartItemsMapperVerification {

            @Test
            @DisplayName("Should correctly map CartItem fields through CartItemMapper for each item")
            void shouldCorrectlyMapCartItemFieldsThroughMapper() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);
                Product product = buildActiveProduct(productId, VALID_PRODUCT_TITLE, 7999L, VALID_PRODUCT_STOCK);
                CartItem cartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), VALID_REQUEST_QUANTITY);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                // Act
                List<CartItemResponseDTO> result = cartItemService.getCartItems();

                // Assert — verify the mapper output matches what we expect
                assertEquals(1, result.size());

                CartItemResponseDTO dto = result.getFirst();

                assertEquals(productId, dto.productId());
                assertEquals(VALID_PRODUCT_TITLE, dto.productTitle());
                assertEquals(7999L, dto.price());
                assertTrue(dto.isActive());
                assertEquals(VALID_REQUEST_QUANTITY, dto.quantity());
            }

            @Test
            @DisplayName("Should set isActive to false when product is inactive")
            void shouldSetIsActiveToFalse_whenProductIsInactive() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);
                Product product = buildActiveProduct(productId, VALID_PRODUCT_TITLE, 7999L, VALID_PRODUCT_STOCK);
                product.setActive(false); // Product is inactive
                CartItem cartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), VALID_REQUEST_QUANTITY);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                // Act
                List<CartItemResponseDTO> result = cartItemService.getCartItems();

                // Assert — CartItemMapper sets isActive = product.isActive() && product.getQuantity() > 0
                assertEquals(1, result.size());
                assertFalse(result.getFirst().isActive());
            }

            @Test
            @DisplayName("Should set isActive to false when product has zero stock")
            void shouldSetIsActiveToFalse_whenProductHasZeroStock() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();
                UUID cartItemId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);
                Product product = buildActiveProduct(productId, VALID_PRODUCT_TITLE, 7999L, 0); // Zero stock
                CartItem cartItem = buildSavedCartItem(cartItemId, product, customer.getCart(), VALID_REQUEST_QUANTITY);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                // Act
                List<CartItemResponseDTO> result = cartItemService.getCartItems();

                // Assert — CartItemMapper sets isActive = product.isActive() && product.getQuantity() > 0
                assertEquals(1, result.size());
                assertFalse(result.getFirst().isActive());
            }
        }

        // ==================== getCartItems() — EDGE CASES ====================

        @Nested
        @DisplayName("getCartItems() — Edge cases")
        class GetCartItemsEdgeCases {

            @Test
            @DisplayName("Should return items with correct isActive flag for a mix of active and inactive products")
            void shouldReturnCorrectIsActiveFlags_forMixedProducts() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);

                UUID productId1 = UUID.randomUUID();
                Product activeProduct = buildActiveProduct(productId1, "Active Product", 5999L, 10);

                UUID productId2 = UUID.randomUUID();
                Product inactiveProduct = buildActiveProduct(productId2, "Inactive Product", 3999L, 5);
                inactiveProduct.setActive(false);

                UUID productId3 = UUID.randomUUID();
                Product outOfStockProduct = buildActiveProduct(productId3, "Out of Stock Product", 9999L, 0);

                CartItem cartItem1 = buildSavedCartItem(UUID.randomUUID(), activeProduct, customer.getCart(), 1);
                CartItem cartItem2 = buildSavedCartItem(UUID.randomUUID(), inactiveProduct, customer.getCart(), 2);
                CartItem cartItem3 = buildSavedCartItem(UUID.randomUUID(), outOfStockProduct, customer.getCart(), 3);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId))
                        .thenReturn(List.of(cartItem1, cartItem2, cartItem3));

                // Act
                List<CartItemResponseDTO> result = cartItemService.getCartItems();

                // Assert
                assertEquals(3, result.size());
                assertTrue(result.getFirst().isActive(), "Active product with stock should be isActive=true");
                assertFalse(result.get(1).isActive(), "Inactive product should be isActive=false");
                assertFalse(result.get(2).isActive(), "Out-of-stock product should be isActive=false");
            }

            @Test
            @DisplayName("Should return correct result when cart has exactly one item with quantity of 1")
            void shouldReturnCorrectResult_whenCartHasSingleItemWithQuantityOne() {
                // Arrange
                UUID customerId = UUID.randomUUID();
                UUID cartId = UUID.randomUUID();
                UUID productId = UUID.randomUUID();

                Customer customer = buildCustomerWithCart(customerId, cartId);
                Product product = buildActiveProduct(productId, VALID_PRODUCT_TITLE, 7999L, VALID_PRODUCT_STOCK);
                CartItem cartItem = buildSavedCartItem(UUID.randomUUID(), product, customer.getCart(), 1);

                stubAuthenticatedUser(VALID_EMAIL);

                when(customerRepository.findByEmailWithCart(VALID_EMAIL)).thenReturn(Optional.of(customer));
                when(cartItemRepository.findAllByCartIdWithProduct(cartId)).thenReturn(List.of(cartItem));

                // Act
                List<CartItemResponseDTO> result = cartItemService.getCartItems();

                // Assert
                assertEquals(1, result.size());
                assertEquals(1, result.getFirst().quantity());
            }
        }
    }
}
