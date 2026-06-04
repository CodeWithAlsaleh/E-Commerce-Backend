package com.sivan.ecommerce.service.product;

import com.sivan.ecommerce.dto.product.ProductRequestDTO;
import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.product.Product;
import com.sivan.ecommerce.exception.ProductNotFoundException;
import com.sivan.ecommerce.repository.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProductServiceImpl}.
 * Only the service layer is tested here — the repository is mocked.
 * No Spring context is loaded, keeping tests fast and isolated.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl — Unit Tests")
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    // ======================== Shared Helper Constants ========================

    private static final String VALID_TITLE = "Wireless Bluetooth Headphones";
    private static final String VALID_DESCRIPTION =
            "Premium noise-cancelling headphones with 40-hour battery life and deep bass.";
    private static final int VALID_QUANTITY = 50;
    private static final long VALID_PRICE = 7999L;
    private static final String VALID_CURRENCY = "USD";
    private static final String VALID_IMAGE_URL = "https://example.com/images/headphones.png";

    // ======================== createProduct() ========================

    @Nested
    @DisplayName("createProduct()")
    class CreateProduct {

        // ======================== Helper Methods ========================

        /**
         * Builds a valid {@link ProductRequestDTO} with all fields populated.
         */
        private ProductRequestDTO validRequest() {
            return new ProductRequestDTO(
                    VALID_TITLE,
                    VALID_DESCRIPTION,
                    VALID_QUANTITY,
                    VALID_PRICE,
                    VALID_CURRENCY,
                    VALID_IMAGE_URL
            );
        }

        /**
         * Builds a {@link ProductRequestDTO} with a null description.
         */
        private ProductRequestDTO requestWithNullDescription() {
            return new ProductRequestDTO(
                    VALID_TITLE,
                    null,
                    VALID_QUANTITY,
                    VALID_PRICE,
                    VALID_CURRENCY,
                    VALID_IMAGE_URL
            );
        }

        /**
         * Stubs the repository to return the given product with an ID assigned
         * (simulating what Hibernate would do on save).
         */
        private void stubRepositorySave(UUID id) {
            Product savedProduct = new Product(
                    VALID_TITLE.trim(),
                    VALID_DESCRIPTION.trim(),
                    VALID_QUANTITY,
                    VALID_PRICE,
                    VALID_CURRENCY.trim().toUpperCase(),
                    VALID_IMAGE_URL.trim()
            );
            // Use reflection via the test utility to set the ID,
            // mimicking Hibernate's @UuidGenerator behavior on persist.
            EntityTestUtil.setId(savedProduct, id);

            when(productRepository.save(any(Product.class))).thenReturn(savedProduct);
        }

        // ==================== SUCCESS CASES ====================

        @Nested
        @DisplayName("Success cases")
        class SuccessCases {

            @Test
            @DisplayName("Should return a valid ProductResponseDTO when request is valid with description")
            void shouldReturnProductResponseDTO_whenRequestIsValid() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                stubRepositorySave(expectedId);
                ProductRequestDTO request = validRequest();

                // Act
                ProductResponseDTO response = productService.createProduct(request);

                // Assert
                assertNotNull(response);
                assertEquals(expectedId, response.id());
                assertEquals(VALID_TITLE.trim(), response.title());
                assertEquals(VALID_DESCRIPTION.trim(), response.description());
                assertEquals(VALID_QUANTITY, response.quantity());
                assertEquals(VALID_PRICE, response.price());
                assertEquals(VALID_CURRENCY.trim().toUpperCase(), response.currencyCode());
                assertEquals(VALID_IMAGE_URL.trim(), response.imageUrl());
            }

            @Test
            @DisplayName("Should save the product exactly once via the repository")
            void shouldCallRepositorySaveExactlyOnce() {
                // Arrange
                stubRepositorySave(UUID.randomUUID());

                // Act
                productService.createProduct(validRequest());

                // Assert
                verify(productRepository).save(any(Product.class));
            }

            @Test
            @DisplayName("Should succeed when description is null (optional field)")
            void shouldSucceed_whenDescriptionIsNull() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                Product savedProduct = new Product(
                        VALID_TITLE.trim(), null, VALID_QUANTITY, VALID_PRICE,
                        VALID_CURRENCY.trim().toUpperCase(), VALID_IMAGE_URL.trim()
                );
                EntityTestUtil.setId(savedProduct, expectedId);
                when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

                // Act
                ProductResponseDTO response = productService.createProduct(requestWithNullDescription());

                // Assert
                assertNotNull(response);
                assertEquals(expectedId, response.id());
                assertNull(response.description());
                verify(productRepository).save(any(Product.class));
            }

            @Test
            @DisplayName("Should trim and uppercase the currency code before saving")
            void shouldTrimAndUppercaseCurrencyCode() {
                // Arrange
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, " usd ", VALID_IMAGE_URL
                );

                UUID expectedId = UUID.randomUUID();
                Product savedProduct = new Product(
                        VALID_TITLE.trim(), VALID_DESCRIPTION.trim(), VALID_QUANTITY,
                        VALID_PRICE, "USD", VALID_IMAGE_URL.trim()
                );
                EntityTestUtil.setId(savedProduct, expectedId);
                when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

                // Act
                ProductResponseDTO response = productService.createProduct(request);

                // Assert
                assertEquals("USD", response.currencyCode());
            }

            @Test
            @DisplayName("Should trim title whitespace before saving")
            void shouldTrimTitleWhitespace() {
                // Arrange
                ProductRequestDTO request = new ProductRequestDTO(
                        "  Wireless Headphones  ", VALID_DESCRIPTION, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                UUID expectedId = UUID.randomUUID();
                Product savedProduct = new Product(
                        "Wireless Headphones", VALID_DESCRIPTION.trim(), VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY.trim().toUpperCase(), VALID_IMAGE_URL.trim()
                );
                com.sivan.ecommerce.entity.EntityTestUtil.setId(savedProduct, expectedId);
                when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

                // Act
                ProductResponseDTO response = productService.createProduct(request);

                // Assert
                assertEquals("Wireless Headphones", response.title());
            }
        }

        // ==================== REPOSITORY / SERVER FAILURE CASES ====================

        @Nested
        @DisplayName("Repository failure simulation")
        class RepositoryFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when repository throws on save")
            void shouldPropagateRuntimeException_whenRepositoryThrows() {
                // Arrange
                when(productRepository.save(any(Product.class)))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> productService.createProduct(validRequest())
                );
                assertEquals("Database connection lost", exception.getMessage());
                verify(productRepository).save(any(Product.class));
            }

            @Test
            @DisplayName("Should propagate DataAccessException-style errors from repository")
            void shouldPropagateException_whenRepositorySaveFailsUnexpectedly() {
                // Arrange — simulating a constraint violation or DB failure
                when(productRepository.save(any(Product.class)))
                        .thenThrow(new IllegalStateException("Unexpected persistence error"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> productService.createProduct(validRequest())
                );
                assertEquals("Unexpected persistence error", exception.getMessage());
            }
        }

        // ==================== EDGE CASES ====================

        @Nested
        @DisplayName("Edge cases")
        class EdgeCases {

            @Test
            @DisplayName("Should handle zero quantity without error")
            void shouldHandleZeroQuantity() {
                // Arrange
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, 0,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                UUID expectedId = UUID.randomUUID();
                Product savedProduct = new Product(
                        VALID_TITLE.trim(), VALID_DESCRIPTION.trim(), 0,
                        VALID_PRICE, VALID_CURRENCY.trim().toUpperCase(), VALID_IMAGE_URL.trim()
                );
                EntityTestUtil.setId(savedProduct, expectedId);
                when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

                // Act
                ProductResponseDTO response = productService.createProduct(request);

                // Assert
                assertEquals(0, response.quantity());
            }

            @Test
            @DisplayName("Should handle zero price without error")
            void shouldHandleZeroPrice() {
                // Arrange
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        0L, VALID_CURRENCY, VALID_IMAGE_URL
                );

                UUID expectedId = UUID.randomUUID();
                Product savedProduct = new Product(
                        VALID_TITLE.trim(), VALID_DESCRIPTION.trim(), VALID_QUANTITY,
                        0L, VALID_CURRENCY.trim().toUpperCase(), VALID_IMAGE_URL.trim()
                );
                EntityTestUtil.setId(savedProduct, expectedId);
                when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

                // Act
                ProductResponseDTO response = productService.createProduct(request);

                // Assert
                assertEquals(0L, response.price());
            }

            @Test
            @DisplayName("Should handle a very long valid description (5000 characters)")
            void shouldHandleVeryLongDescription() {
                // Arrange
                String longDescription = "X".repeat(5000);
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, longDescription, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                UUID expectedId = UUID.randomUUID();
                Product savedProduct = new Product(
                        VALID_TITLE.trim(), longDescription, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY.trim().toUpperCase(), VALID_IMAGE_URL.trim()
                );
                EntityTestUtil.setId(savedProduct, expectedId);
                when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

                // Act
                ProductResponseDTO response = productService.createProduct(request);

                // Assert
                assertNotNull(response);
                assertEquals(5000, response.description().length());
            }

            @Test
            @DisplayName("Should handle special characters in title and description")
            void shouldHandleSpecialCharacters() {
                // Arrange
                String specialTitle = "Laptop™ — Pro Edition «2024»";
                String specialDescription = "Features: résumé-ready display, naïve AI engine, 日本語サポート & more! @#$%^&*()";
                ProductRequestDTO request = new ProductRequestDTO(
                        specialTitle, specialDescription, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                UUID expectedId = UUID.randomUUID();
                Product savedProduct = new Product(
                        specialTitle.trim(), specialDescription.trim(), VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY.trim().toUpperCase(), VALID_IMAGE_URL.trim()
                );
                EntityTestUtil.setId(savedProduct, expectedId);
                when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

                // Act
                ProductResponseDTO response = productService.createProduct(request);

                // Assert
                assertNotNull(response);
                assertEquals(specialTitle.trim(), response.title());
                assertEquals(specialDescription.trim(), response.description());
            }

            @Test
            @DisplayName("Should handle max long value for price")
            void shouldHandleMaxLongPrice() {
                // Arrange
                ProductRequestDTO request = new ProductRequestDTO(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        Long.MAX_VALUE, VALID_CURRENCY, VALID_IMAGE_URL
                );

                UUID expectedId = UUID.randomUUID();
                Product savedProduct = new Product(
                        VALID_TITLE.trim(), VALID_DESCRIPTION.trim(), VALID_QUANTITY,
                        Long.MAX_VALUE, VALID_CURRENCY.trim().toUpperCase(), VALID_IMAGE_URL.trim()
                );
                EntityTestUtil.setId(savedProduct, expectedId);
                when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

                // Act
                ProductResponseDTO response = productService.createProduct(request);

                // Assert
                assertEquals(Long.MAX_VALUE, response.price());
            }
        }

        // ==================== MAPPER INTERACTION VERIFICATION ====================

        @Nested
        @DisplayName("Mapper integration verification")
        class MapperVerification {

            @Test
            @DisplayName("Should pass the correctly mapped Product entity to repository.save()")
            void shouldPassMappedProductToRepository() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                Product savedProduct = new Product(
                        VALID_TITLE.trim(), VALID_DESCRIPTION.trim(), VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY.trim().toUpperCase(), VALID_IMAGE_URL.trim()
                );
                EntityTestUtil.setId(savedProduct, expectedId);

                when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                    Product captured = inv.getArgument(0);

                    // Verify the mapper correctly transformed the DTO into the entity
                    assertEquals(VALID_TITLE.trim(), captured.getTitle());
                    assertEquals(VALID_DESCRIPTION.trim(), captured.getDescription());
                    assertEquals(VALID_QUANTITY, captured.getQuantity());
                    assertEquals(VALID_PRICE, captured.getPrice());
                    assertEquals(VALID_CURRENCY.trim().toUpperCase(), captured.getCurrencyCode());
                    assertEquals(VALID_IMAGE_URL.trim(), captured.getImageUrl());

                    return savedProduct;
                });

                // Act
                productService.createProduct(validRequest());

                // Assert — verify save was called
                verify(productRepository).save(any(Product.class));
            }
        }
    }

    // ======================== getProduct() ========================

    @Nested
    @DisplayName("getProduct()")
    class GetProduct {

        // ======================== Helper Methods ========================

        /**
         * Builds a valid {@link ProductResponseDTO} with all fields populated.
         */
        private ProductResponseDTO validProductResponse(UUID id) {
            return new ProductResponseDTO(
                    id,
                    VALID_TITLE,
                    VALID_DESCRIPTION,
                    VALID_QUANTITY,
                    VALID_PRICE,
                    VALID_CURRENCY,
                    VALID_IMAGE_URL
            );
        }

        // ==================== SUCCESS CASES ====================

        @Nested
        @DisplayName("Success cases")
        class SuccessCases {

            @Test
            @DisplayName("Should return ProductResponseDTO when product exists and is active")
            void shouldReturnProductResponseDTO_whenProductExistsAndIsActive() {
                // Arrange
                UUID productId = UUID.randomUUID();
                ProductResponseDTO expectedResponse = validProductResponse(productId);
                when(productRepository.findByIdAndIsActive(productId, true))
                        .thenReturn(Optional.of(expectedResponse));

                // Act
                ProductResponseDTO response = productService.getProduct(productId);

                // Assert
                assertNotNull(response);
                assertEquals(productId, response.id());
                assertEquals(VALID_TITLE, response.title());
                assertEquals(VALID_DESCRIPTION, response.description());
                assertEquals(VALID_QUANTITY, response.quantity());
                assertEquals(VALID_PRICE, response.price());
                assertEquals(VALID_CURRENCY, response.currencyCode());
                assertEquals(VALID_IMAGE_URL, response.imageUrl());
            }

            @Test
            @DisplayName("Should call repository findByIdAndIsActive exactly once with correct arguments")
            void shouldCallRepositoryFindByIdAndIsActive_exactlyOnce() {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productRepository.findByIdAndIsActive(productId, true))
                        .thenReturn(Optional.of(validProductResponse(productId)));

                // Act
                productService.getProduct(productId);

                // Assert
                verify(productRepository).findByIdAndIsActive(productId, true);
            }

            @Test
            @DisplayName("Should return product with null description when description is null")
            void shouldReturnProduct_whenDescriptionIsNull() {
                // Arrange
                UUID productId = UUID.randomUUID();
                ProductResponseDTO expectedResponse = new ProductResponseDTO(
                        productId, VALID_TITLE, null, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productRepository.findByIdAndIsActive(productId, true))
                        .thenReturn(Optional.of(expectedResponse));

                // Act
                ProductResponseDTO response = productService.getProduct(productId);

                // Assert
                assertNotNull(response);
                assertNull(response.description());
            }
        }

        // ==================== NOT FOUND CASES ====================

        @Nested
        @DisplayName("Not found cases")
        class NotFoundCases {

            @Test
            @DisplayName("Should throw ProductNotFoundException when product does not exist")
            void shouldThrowProductNotFoundException_whenProductDoesNotExist() {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productRepository.findByIdAndIsActive(productId, true))
                        .thenReturn(Optional.empty());

                // Act & Assert
                ProductNotFoundException exception = assertThrows(
                        ProductNotFoundException.class,
                        () -> productService.getProduct(productId)
                );
                assertEquals("Product not found", exception.getMessage());
                verify(productRepository).findByIdAndIsActive(productId, true);
            }

            @Test
            @DisplayName("Should not interact with repository save when getting a product")
            void shouldNotCallRepositorySave_whenGettingProduct() {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productRepository.findByIdAndIsActive(productId, true))
                        .thenReturn(Optional.of(validProductResponse(productId)));

                // Act
                productService.getProduct(productId);

                // Assert
                verify(productRepository, never()).save(any(Product.class));
            }
        }

        // ==================== REPOSITORY FAILURE CASES ====================

        @Nested
        @DisplayName("Repository failure simulation")
        class RepositoryFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when repository throws on find")
            void shouldPropagateRuntimeException_whenRepositoryThrowsOnFind() {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productRepository.findByIdAndIsActive(productId, true))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> productService.getProduct(productId)
                );
                assertEquals("Database connection lost", exception.getMessage());
                verify(productRepository).findByIdAndIsActive(productId, true);
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when repository encounters unexpected error")
            void shouldPropagateException_whenRepositoryFindFailsUnexpectedly() {
                // Arrange — simulating an unexpected persistence-layer failure
                UUID productId = UUID.randomUUID();
                when(productRepository.findByIdAndIsActive(productId, true))
                        .thenThrow(new IllegalStateException("Unexpected persistence error"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> productService.getProduct(productId)
                );
                assertEquals("Unexpected persistence error", exception.getMessage());
            }
        }

        // ==================== EDGE CASES ====================

        @Nested
        @DisplayName("Edge cases")
        class EdgeCases {

            @Test
            @DisplayName("Should return product with zero quantity")
            void shouldReturnProduct_whenQuantityIsZero() {
                // Arrange
                UUID productId = UUID.randomUUID();
                ProductResponseDTO expectedResponse = new ProductResponseDTO(
                        productId, VALID_TITLE, VALID_DESCRIPTION, 0,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productRepository.findByIdAndIsActive(productId, true))
                        .thenReturn(Optional.of(expectedResponse));

                // Act
                ProductResponseDTO response = productService.getProduct(productId);

                // Assert
                assertNotNull(response);
                assertEquals(0, response.quantity());
            }

            @Test
            @DisplayName("Should return product with zero price")
            void shouldReturnProduct_whenPriceIsZero() {
                // Arrange
                UUID productId = UUID.randomUUID();
                ProductResponseDTO expectedResponse = new ProductResponseDTO(
                        productId, VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        0L, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productRepository.findByIdAndIsActive(productId, true))
                        .thenReturn(Optional.of(expectedResponse));

                // Act
                ProductResponseDTO response = productService.getProduct(productId);

                // Assert
                assertNotNull(response);
                assertEquals(0L, response.price());
            }

            @Test
            @DisplayName("Should return product with max long price")
            void shouldReturnProduct_whenPriceIsMaxLong() {
                // Arrange
                UUID productId = UUID.randomUUID();
                ProductResponseDTO expectedResponse = new ProductResponseDTO(
                        productId, VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        Long.MAX_VALUE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productRepository.findByIdAndIsActive(productId, true))
                        .thenReturn(Optional.of(expectedResponse));

                // Act
                ProductResponseDTO response = productService.getProduct(productId);

                // Assert
                assertNotNull(response);
                assertEquals(Long.MAX_VALUE, response.price());
            }

            @Test
            @DisplayName("Should return product with special characters in title and description")
            void shouldReturnProduct_whenFieldsContainSpecialCharacters() {
                // Arrange
                UUID productId = UUID.randomUUID();
                String specialTitle = "Laptop™ — Pro Edition «2024»";
                String specialDescription = "Features: résumé-ready display, naïve AI engine, 日本語サポート & more!";
                ProductResponseDTO expectedResponse = new ProductResponseDTO(
                        productId, specialTitle, specialDescription, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productRepository.findByIdAndIsActive(productId, true))
                        .thenReturn(Optional.of(expectedResponse));

                // Act
                ProductResponseDTO response = productService.getProduct(productId);

                // Assert
                assertNotNull(response);
                assertEquals(specialTitle, response.title());
                assertEquals(specialDescription, response.description());
            }

            @Test
            @DisplayName("Should return product with very long description (5000 characters)")
            void shouldReturnProduct_whenDescriptionIsVeryLong() {
                // Arrange
                UUID productId = UUID.randomUUID();
                String longDescription = "X".repeat(5000);
                ProductResponseDTO expectedResponse = new ProductResponseDTO(
                        productId, VALID_TITLE, longDescription, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                when(productRepository.findByIdAndIsActive(productId, true))
                        .thenReturn(Optional.of(expectedResponse));

                // Act
                ProductResponseDTO response = productService.getProduct(productId);

                // Assert
                assertNotNull(response);
                assertEquals(5000, response.description().length());
            }
        }
    }
}
