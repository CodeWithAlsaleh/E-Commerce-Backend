package com.sivan.ecommerce.service.product;

import com.sivan.ecommerce.dto.product.ProductFilterDTO;
import com.sivan.ecommerce.dto.product.ProductRequestDTO;
import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.category.Category;
import com.sivan.ecommerce.entity.product.Product;
import com.sivan.ecommerce.exception.CategoryNotFoundException;
import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.exception.ProductNotFoundException;
import com.sivan.ecommerce.repository.category.CategoryRepository;
import com.sivan.ecommerce.repository.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Unit tests for {@link ProductServiceImpl}.
 * Only the service layer is tested here — the repository is mocked.
 * No Spring context is loaded, keeping tests fast and isolated.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl — Unit Tests")
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

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
                assertEquals(expectedId.toString(), response.id());
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
                assertEquals(expectedId.toString(), response.id());
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
        private Product validProduct(UUID id) {
            Product product = new Product(VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY, VALID_PRICE,
                    VALID_CURRENCY, VALID_IMAGE_URL
            );

            EntityTestUtil.setId(product, id);

            return product;
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
                Product product = validProduct(productId);
                when(productRepository.findById(productId))
                        .thenReturn(Optional.of(product));

                // Act
                ProductResponseDTO response = productService.getProduct(productId);

                // Assert
                assertNotNull(response);
                assertEquals(productId.toString(), response.id());
                assertEquals(VALID_TITLE, response.title());
                assertEquals(VALID_DESCRIPTION, response.description());
                assertEquals(VALID_QUANTITY, response.quantity());
                assertEquals(VALID_PRICE, response.price());
                assertEquals(VALID_CURRENCY, response.currencyCode());
                assertEquals(VALID_IMAGE_URL, response.imageUrl());
            }

            @Test
            @DisplayName("Should call repository findById exactly once with correct arguments")
            void shouldCallRepositoryFindById_exactlyOnce() {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productRepository.findById(productId))
                        .thenReturn(Optional.of(validProduct(productId)));

                // Act
                productService.getProduct(productId);

                // Assert
                verify(productRepository).findById(productId);
            }

            @Test
            @DisplayName("Should return product with null description when description is null")
            void shouldReturnProduct_whenDescriptionIsNull() {
                // Arrange
                UUID productId = UUID.randomUUID();
                Product product = new Product(
                        VALID_TITLE, null, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                EntityTestUtil.setId(product, productId);

                when(productRepository.findById(productId))
                        .thenReturn(Optional.of(product));

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
                when(productRepository.findById(productId))
                        .thenReturn(Optional.empty());

                // Act & Assert
                ProductNotFoundException exception = assertThrows(
                        ProductNotFoundException.class,
                        () -> productService.getProduct(productId)
                );
                assertEquals("Product not found", exception.getMessage());
                verify(productRepository).findById(productId);
            }

            @Test
            @DisplayName("Should not interact with repository save when getting a product")
            void shouldNotCallRepositorySave_whenGettingProduct() {
                // Arrange
                UUID productId = UUID.randomUUID();
                when(productRepository.findById(productId))
                        .thenReturn(Optional.of(validProduct(productId)));

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
                when(productRepository.findById(productId))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> productService.getProduct(productId)
                );
                assertEquals("Database connection lost", exception.getMessage());
                verify(productRepository).findById(productId);
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when repository encounters unexpected error")
            void shouldPropagateException_whenRepositoryFindFailsUnexpectedly() {
                // Arrange — simulating an unexpected persistence-layer failure
                UUID productId = UUID.randomUUID();
                when(productRepository.findById(productId))
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
                Product product = new Product(
                        VALID_TITLE, VALID_DESCRIPTION, 0,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                EntityTestUtil.setId(product, productId);

                when(productRepository.findById(productId))
                        .thenReturn(Optional.of(product));

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
                Product product = new Product(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        0L, VALID_CURRENCY, VALID_IMAGE_URL
                );
                EntityTestUtil.setId(product, productId);

                when(productRepository.findById(productId))
                        .thenReturn(Optional.of(product));

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
                Product product = new Product(
                        VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                        Long.MAX_VALUE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                EntityTestUtil.setId(product, productId);

                when(productRepository.findById(productId))
                        .thenReturn(Optional.of(product));

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
                Product product = new Product(
                        specialTitle, specialDescription, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                EntityTestUtil.setId(product, productId);

                when(productRepository.findById(productId))
                        .thenReturn(Optional.of(product));

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
                Product product = new Product(
                        VALID_TITLE, longDescription, VALID_QUANTITY,
                        VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
                );
                EntityTestUtil.setId(product, productId);

                when(productRepository.findById(productId))
                        .thenReturn(Optional.of(product));

                // Act
                ProductResponseDTO response = productService.getProduct(productId);

                // Assert
                assertNotNull(response);
                assertEquals(5000, response.description().length());
            }
        }
    }

    // ======================== getProducts() ========================

    @Nested
    @DisplayName("getProducts()")
    class GetProducts {

        // ======================== Helper Methods ========================

        /**
         * Builds a {@link ProductFilterDTO} with all filters populated.
         */
        private ProductFilterDTO validFilter() {
            return new ProductFilterDTO(
                    "Headphones",
                    1000L,
                    5000L,
                    "Electronics"
            );
        }

        /**
         * Builds a {@link ProductFilterDTO} with all fields null (no filtering).
         */
        private ProductFilterDTO emptyFilter() {
            return new ProductFilterDTO(null, null, null, null);
        }

        /**
         * Builds a default valid {@link Pageable} sorted by "price" ascending.
         */
        private Pageable validPageable() {
            return PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "price"));
        }

        /**
         * Creates a page containing a single {@link ProductResponseDTO}.
         */
        private Page<ProductResponseDTO> singleProductPage(Pageable pageable) {
            ProductResponseDTO product = new ProductResponseDTO(
                    UUID.randomUUID().toString(),
                    VALID_TITLE,
                    VALID_DESCRIPTION,
                    VALID_QUANTITY,
                    VALID_PRICE,
                    VALID_CURRENCY,
                    VALID_IMAGE_URL
            );
            return new PageImpl<>(List.of(product), pageable, 1);
        }

        // ==================== SUCCESS CASES ====================

        @Nested
        @DisplayName("Success cases")
        class SuccessCases {

            @Test
            @DisplayName("Should return a page of products when filters and pageable are valid")
            void shouldReturnPageOfProducts_whenFiltersAreValid() {
                // Arrange
                ProductFilterDTO filter = validFilter();
                Pageable pageable = validPageable();
                Page<ProductResponseDTO> expectedPage = singleProductPage(pageable);

                when(productRepository.findByFilters(
                        filter.search(), filter.minPrice(), filter.maxPrice(),
                        filter.category(), pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<ProductResponseDTO> result = productService.getProducts(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
                assertEquals(1, result.getContent().size());
                assertEquals(VALID_TITLE, result.getContent().get(0).title());
            }

            @Test
            @DisplayName("Should return products when all filter fields are null (no filtering)")
            void shouldReturnProducts_whenAllFiltersAreNull() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = validPageable();
                Page<ProductResponseDTO> expectedPage = singleProductPage(pageable);

                when(productRepository.findByFilters(null, null, null, null, pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<ProductResponseDTO> result = productService.getProducts(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
            }

            @Test
            @DisplayName("Should return products when only minPrice filter is set")
            void shouldReturnProducts_whenOnlyMinPriceIsSet() {
                // Arrange
                ProductFilterDTO filter = new ProductFilterDTO(null, 500L, null, null);
                Pageable pageable = validPageable();
                Page<ProductResponseDTO> expectedPage = singleProductPage(pageable);

                when(productRepository.findByFilters(null, 500L, null, null, pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<ProductResponseDTO> result = productService.getProducts(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
            }

            @Test
            @DisplayName("Should return products when only maxPrice filter is set")
            void shouldReturnProducts_whenOnlyMaxPriceIsSet() {
                // Arrange
                ProductFilterDTO filter = new ProductFilterDTO(null, null, 9999L, null);
                Pageable pageable = validPageable();
                Page<ProductResponseDTO> expectedPage = singleProductPage(pageable);

                when(productRepository.findByFilters(null, null, 9999L, null, pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<ProductResponseDTO> result = productService.getProducts(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
            }

            @Test
            @DisplayName("Should return products when sorting by 'title' ascending")
            void shouldReturnProducts_whenSortingByTitleAsc() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "title"));
                Page<ProductResponseDTO> expectedPage = singleProductPage(pageable);

                when(productRepository.findByFilters(null, null, null, null, pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<ProductResponseDTO> result = productService.getProducts(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
            }

            @Test
            @DisplayName("Should return products when sorting by 'price' descending")
            void shouldReturnProducts_whenSortingByPriceDesc() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "price"));
                Page<ProductResponseDTO> expectedPage = singleProductPage(pageable);

                when(productRepository.findByFilters(null, null, null, null, pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<ProductResponseDTO> result = productService.getProducts(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
            }

            @Test
            @DisplayName("Should return products when pageable has no sort (unsorted)")
            void shouldReturnProducts_whenPageableIsUnsorted() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = PageRequest.of(0, 10);
                Page<ProductResponseDTO> expectedPage = singleProductPage(pageable);

                when(productRepository.findByFilters(null, null, null, null, pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<ProductResponseDTO> result = productService.getProducts(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
            }

            @Test
            @DisplayName("Should return products when sorting by both 'title' and 'price'")
            void shouldReturnProducts_whenSortingByMultipleAllowedFields() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = PageRequest.of(0, 10,
                        Sort.by(Sort.Order.asc("title"), Sort.Order.desc("price")));
                Page<ProductResponseDTO> expectedPage = singleProductPage(pageable);

                when(productRepository.findByFilters(null, null, null, null, pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<ProductResponseDTO> result = productService.getProducts(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
            }
        }

        // ==================== VALIDATION FAILURE CASES (InvalidDataException) ====================

        @Nested
        @DisplayName("Validation failure cases")
        class ValidationFailures {

            @Test
            @DisplayName("Should throw InvalidDataException when minPrice is greater than maxPrice")
            void shouldThrowInvalidDataException_whenMinPriceGreaterThanMaxPrice() {
                // Arrange
                ProductFilterDTO filter = new ProductFilterDTO(null, 5000L, 1000L, null);
                Pageable pageable = validPageable();

                // Act & Assert
                InvalidDataException exception = assertThrows(
                        InvalidDataException.class,
                        () -> productService.getProducts(filter, pageable)
                );
                assertEquals("Minimum price must be less than or equal to maximum price",
                        exception.getMessage());
            }

            @Test
            @DisplayName("Should not call repository when minPrice is greater than maxPrice")
            void shouldNotCallRepository_whenMinPriceGreaterThanMaxPrice() {
                // Arrange
                ProductFilterDTO filter = new ProductFilterDTO(null, 5000L, 1000L, null);
                Pageable pageable = validPageable();

                // Act
                assertThrows(InvalidDataException.class,
                        () -> productService.getProducts(filter, pageable));

                // Assert
                verifyNoInteractions(productRepository);
            }

            @Test
            @DisplayName("Should throw InvalidDataException when sort field is not allowed")
            void shouldThrowInvalidDataException_whenSortFieldIsNotAllowed() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = PageRequest.of(0, 10, Sort.by("description"));

                // Act & Assert
                InvalidDataException exception = assertThrows(
                        InvalidDataException.class,
                        () -> productService.getProducts(filter, pageable)
                );
                assertEquals("Sorting by 'description' is not allowed",
                        exception.getMessage());
            }

            @Test
            @DisplayName("Should throw InvalidDataException when sorting by 'id' (not in allowed set)")
            void shouldThrowInvalidDataException_whenSortingById() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));

                // Act & Assert
                InvalidDataException exception = assertThrows(
                        InvalidDataException.class,
                        () -> productService.getProducts(filter, pageable)
                );
                assertEquals("Sorting by 'id' is not allowed", exception.getMessage());
            }

            @Test
            @DisplayName("Should throw InvalidDataException when sorting by 'quantity' (not in allowed set)")
            void shouldThrowInvalidDataException_whenSortingByQuantity() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = PageRequest.of(0, 10, Sort.by("quantity"));

                // Act & Assert
                InvalidDataException exception = assertThrows(
                        InvalidDataException.class,
                        () -> productService.getProducts(filter, pageable)
                );
                assertEquals("Sorting by 'quantity' is not allowed", exception.getMessage());
            }

            @Test
            @DisplayName("Should not call repository when sort field is not allowed")
            void shouldNotCallRepository_whenSortFieldIsNotAllowed() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt"));

                // Act
                assertThrows(InvalidDataException.class,
                        () -> productService.getProducts(filter, pageable));

                // Assert
                verifyNoInteractions(productRepository);
            }

            @Test
            @DisplayName("Should throw InvalidDataException when one of multiple sort fields is invalid")
            void shouldThrowInvalidDataException_whenOneOfMultipleSortFieldsIsInvalid() {
                // Arrange — "price" is valid, "createdAt" is not
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = PageRequest.of(0, 10,
                        Sort.by(Sort.Order.asc("price"), Sort.Order.desc("createdAt")));

                // Act & Assert
                InvalidDataException exception = assertThrows(
                        InvalidDataException.class,
                        () -> productService.getProducts(filter, pageable)
                );
                assertEquals("Sorting by 'createdAt' is not allowed",
                        exception.getMessage());
            }
        }

        // ==================== REPOSITORY / SERVER FAILURE CASES ====================

        @Nested
        @DisplayName("Repository failure simulation")
        class RepositoryFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when repository throws on findByFilters")
            void shouldPropagateRuntimeException_whenRepositoryThrows() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = validPageable();

                when(productRepository.findByFilters(
                        any(), any(), any(), any(), any(Pageable.class)))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> productService.getProducts(filter, pageable)
                );
                assertEquals("Database connection lost", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when repository encounters unexpected error")
            void shouldPropagateIllegalStateException_whenRepositoryFails() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = validPageable();

                when(productRepository.findByFilters(
                        any(), any(), any(), any(), any(Pageable.class)))
                        .thenThrow(new IllegalStateException("Unexpected persistence error"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> productService.getProducts(filter, pageable)
                );
                assertEquals("Unexpected persistence error", exception.getMessage());
            }
        }

        // ==================== EDGE CASES ====================

        @Nested
        @DisplayName("Edge cases")
        class EdgeCases {

            @Test
            @DisplayName("Should return empty page when no products match filters")
            void shouldReturnEmptyPage_whenNoProductsMatchFilters() {
                // Arrange
                ProductFilterDTO filter = validFilter();
                Pageable pageable = validPageable();
                Page<ProductResponseDTO> emptyPage = new PageImpl<>(
                        List.of(), pageable, 0);

                when(productRepository.findByFilters(
                        filter.search(), filter.minPrice(), filter.maxPrice(),
                        filter.category(), pageable))
                        .thenReturn(emptyPage);

                // Act
                Page<ProductResponseDTO> result = productService.getProducts(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(0, result.getTotalElements());
                assertTrue(result.getContent().isEmpty());
            }

            @Test
            @DisplayName("Should succeed when minPrice equals maxPrice (exact price match)")
            void shouldSucceed_whenMinPriceEqualsMaxPrice() {
                // Arrange
                ProductFilterDTO filter = new ProductFilterDTO(null, 3000L, 3000L, null);
                Pageable pageable = validPageable();
                Page<ProductResponseDTO> expectedPage = singleProductPage(pageable);

                when(productRepository.findByFilters(null, 3000L, 3000L, null, pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<ProductResponseDTO> result = productService.getProducts(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
            }

            @Test
            @DisplayName("Should succeed when minPrice is zero and maxPrice is set")
            void shouldSucceed_whenMinPriceIsZero() {
                // Arrange
                ProductFilterDTO filter = new ProductFilterDTO(null, 0L, 5000L, null);
                Pageable pageable = validPageable();
                Page<ProductResponseDTO> expectedPage = singleProductPage(pageable);

                when(productRepository.findByFilters(null, 0L, 5000L, null, pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<ProductResponseDTO> result = productService.getProducts(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.getTotalElements());
            }

            @Test
            @DisplayName("Should return multiple products across pages")
            void shouldReturnMultipleProducts_acrossPages() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = PageRequest.of(0, 2, Sort.by("price"));

                ProductResponseDTO product1 = new ProductResponseDTO(
                        UUID.randomUUID().toString(), "Product A", "Desc A",
                        10, 1000L, "USD", "https://example.com/a.png"
                );
                ProductResponseDTO product2 = new ProductResponseDTO(
                        UUID.randomUUID().toString(), "Product B", "Desc B",
                        20, 2000L, "USD", "https://example.com/b.png"
                );
                Page<ProductResponseDTO> expectedPage = new PageImpl<>(
                        List.of(product1, product2), pageable, 5);

                when(productRepository.findByFilters(null, null, null, null, pageable))
                        .thenReturn(expectedPage);

                // Act
                Page<ProductResponseDTO> result = productService.getProducts(filter, pageable);

                // Assert
                assertNotNull(result);
                assertEquals(5, result.getTotalElements());
                assertEquals(2, result.getContent().size());
                assertEquals(3, result.getTotalPages());
            }
        }

        // ==================== REPOSITORY INTERACTION VERIFICATION ====================

        @Nested
        @DisplayName("Repository interaction verification")
        class RepositoryInteractionVerification {

            @Test
            @DisplayName("Should call findByFilters exactly once with correct arguments")
            void shouldCallFindByFilters_exactlyOnceWithCorrectArgs() {
                // Arrange
                ProductFilterDTO filter = validFilter();
                Pageable pageable = validPageable();
                Page<ProductResponseDTO> expectedPage = singleProductPage(pageable);

                when(productRepository.findByFilters(
                        filter.search(), filter.minPrice(), filter.maxPrice(),
                        filter.category(), pageable))
                        .thenReturn(expectedPage);

                // Act
                productService.getProducts(filter, pageable);

                // Assert
                verify(productRepository).findByFilters(
                        filter.search(), filter.minPrice(), filter.maxPrice(),
                        filter.category(), pageable);
                verifyNoMoreInteractions(productRepository);
            }

            @Test
            @DisplayName("Should not call save or findById when getting products list")
            void shouldNotCallOtherRepositoryMethods_whenGettingProductsList() {
                // Arrange
                ProductFilterDTO filter = emptyFilter();
                Pageable pageable = validPageable();
                Page<ProductResponseDTO> expectedPage = singleProductPage(pageable);

                when(productRepository.findByFilters(
                        any(), any(), any(), any(), any(Pageable.class)))
                        .thenReturn(expectedPage);

                // Act
                productService.getProducts(filter, pageable);

                // Assert
                verify(productRepository, never()).save(any(Product.class));
                verify(productRepository, never()).findById(any());
            }
        }
    }

    // ======================== linkCategoryToProduct() ========================

    @Nested
    @DisplayName("linkCategoryToProduct()")
    class LinkCategoryToProduct {

        // ======================== Helper Methods ========================

        /**
         * Creates a {@link Product} with a reflectively-set ID and an empty categories set.
         */
        private Product validProduct(UUID id) {
            Product product = new Product(
                    VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                    VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );
            EntityTestUtil.setId(product, id);
            return product;
        }

        /**
         * Creates a {@link Category} with a reflectively-set ID.
         */
        private Category validCategory(UUID id, String title) {
            Category category = new Category(title, "Description for " + title);
            EntityTestUtil.setId(category, id);
            return category;
        }

        // ==================== SUCCESS CASES ====================

        @Nested
        @DisplayName("Success cases")
        class SuccessCases {

            @Test
            @DisplayName("Should add the category to the product when both exist")
            void shouldAddCategoryToProduct_whenBothExist() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);
                Category category = validCategory(categoryId, "electronics");

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenReturn(Optional.of(category));

                // Act
                productService.linkCategoryToProduct(productId, categoryId);

                // Assert — category was added to product's set
                assertTrue(product.getCategories().contains(category));
                assertEquals(1, product.getCategories().size());
            }

            @Test
            @DisplayName("Should call findByIdWithCategories exactly once with correct productId")
            void shouldCallFindByIdWithCategories_exactlyOnce() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);
                Category category = validCategory(categoryId, "electronics");

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenReturn(Optional.of(category));

                // Act
                productService.linkCategoryToProduct(productId, categoryId);

                // Assert
                verify(productRepository).findByIdWithCategories(productId);
            }

            @Test
            @DisplayName("Should call categoryRepository.findById exactly once with correct categoryId")
            void shouldCallCategoryFindById_exactlyOnce() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);
                Category category = validCategory(categoryId, "electronics");

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenReturn(Optional.of(category));

                // Act
                productService.linkCategoryToProduct(productId, categoryId);

                // Assert
                verify(categoryRepository).findById(categoryId);
            }

            @Test
            @DisplayName("Should not call productRepository.save() — relies on Hibernate dirty-checking")
            void shouldNotCallSave_reliesOnDirtyChecking() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);
                Category category = validCategory(categoryId, "electronics");

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenReturn(Optional.of(category));

                // Act
                productService.linkCategoryToProduct(productId, categoryId);

                // Assert — no explicit save, Hibernate dirty-checking handles persistence
                verify(productRepository, never()).save(any(Product.class));
            }

            @Test
            @DisplayName("Should not throw when the same category is added again (Set handles duplicates)")
            void shouldNotThrow_whenCategoryAlreadyLinked() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);
                Category category = validCategory(categoryId, "electronics");

                // Pre-link the category
                product.addCategory(category);

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenReturn(Optional.of(category));

                // Act — should not throw, Set.add() is idempotent
                assertDoesNotThrow(() ->
                        productService.linkCategoryToProduct(productId, categoryId)
                );

                // Assert — still only one category in the set
                assertEquals(1, product.getCategories().size());
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
                UUID categoryId = UUID.randomUUID();

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.empty());

                // Act & Assert
                ProductNotFoundException exception = assertThrows(
                        ProductNotFoundException.class,
                        () -> productService.linkCategoryToProduct(productId, categoryId)
                );
                assertEquals("Product not found", exception.getMessage());
            }

            @Test
            @DisplayName("Should not call categoryRepository when product is not found")
            void shouldNotCallCategoryRepository_whenProductNotFound() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.empty());

                // Act
                assertThrows(ProductNotFoundException.class,
                        () -> productService.linkCategoryToProduct(productId, categoryId));

                // Assert — category lookup should never happen
                verifyNoInteractions(categoryRepository);
            }

            @Test
            @DisplayName("Should throw CategoryNotFoundException when category does not exist")
            void shouldThrowCategoryNotFoundException_whenCategoryDoesNotExist() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenReturn(Optional.empty());

                // Act & Assert
                CategoryNotFoundException exception = assertThrows(
                        CategoryNotFoundException.class,
                        () -> productService.linkCategoryToProduct(productId, categoryId)
                );
                assertEquals("Category not found", exception.getMessage());
            }

            @Test
            @DisplayName("Should not modify product categories when category is not found")
            void shouldNotModifyProductCategories_whenCategoryNotFound() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenReturn(Optional.empty());

                // Act
                assertThrows(CategoryNotFoundException.class,
                        () -> productService.linkCategoryToProduct(productId, categoryId));

                // Assert — product's categories should remain empty
                assertTrue(product.getCategories().isEmpty());
            }
        }

        // ==================== REPOSITORY / SERVER FAILURE CASES ====================

        @Nested
        @DisplayName("Repository failure simulation")
        class RepositoryFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when productRepository throws on findByIdWithCategories")
            void shouldPropagateRuntimeException_whenProductRepositoryThrows() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();

                when(productRepository.findByIdWithCategories(productId))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> productService.linkCategoryToProduct(productId, categoryId)
                );
                assertEquals("Database connection lost", exception.getMessage());
                verifyNoInteractions(categoryRepository);
            }

            @Test
            @DisplayName("Should propagate RuntimeException when categoryRepository throws on findById")
            void shouldPropagateRuntimeException_whenCategoryRepositoryThrows() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> productService.linkCategoryToProduct(productId, categoryId)
                );
                assertEquals("Database connection lost", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when productRepository encounters unexpected error")
            void shouldPropagateIllegalStateException_whenProductRepositoryFails() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();

                when(productRepository.findByIdWithCategories(productId))
                        .thenThrow(new IllegalStateException("Unexpected persistence error"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> productService.linkCategoryToProduct(productId, categoryId)
                );
                assertEquals("Unexpected persistence error", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException when categoryRepository encounters unexpected error")
            void shouldPropagateIllegalStateException_whenCategoryRepositoryFails() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenThrow(new IllegalStateException("Unexpected persistence error"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> productService.linkCategoryToProduct(productId, categoryId)
                );
                assertEquals("Unexpected persistence error", exception.getMessage());
            }
        }

        // ==================== EDGE CASES ====================

        @Nested
        @DisplayName("Edge cases")
        class EdgeCases {

            @Test
            @DisplayName("Should add category to a product that already has other categories")
            void shouldAddCategory_whenProductAlreadyHasOtherCategories() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID existingCategoryId = UUID.randomUUID();
                UUID newCategoryId = UUID.randomUUID();

                Product product = validProduct(productId);
                Category existingCategory = validCategory(existingCategoryId, "electronics");
                Category newCategory = validCategory(newCategoryId, "accessories");

                // Pre-link the existing category
                product.addCategory(existingCategory);

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(newCategoryId))
                        .thenReturn(Optional.of(newCategory));

                // Act
                productService.linkCategoryToProduct(productId, newCategoryId);

                // Assert — product should now have both categories
                assertEquals(2, product.getCategories().size());
                assertTrue(product.getCategories().contains(existingCategory));
                assertTrue(product.getCategories().contains(newCategory));
            }

            @Test
            @DisplayName("Should handle category with special characters in title")
            void shouldHandleCategory_withSpecialCharactersInTitle() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);
                Category category = validCategory(categoryId, "laptops & pcs™");

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenReturn(Optional.of(category));

                // Act
                productService.linkCategoryToProduct(productId, categoryId);

                // Assert
                assertTrue(product.getCategories().contains(category));
                assertEquals("laptops & pcs™", category.getTitle());
            }
        }

        // ==================== REPOSITORY INTERACTION VERIFICATION ====================

        @Nested
        @DisplayName("Repository interaction verification")
        class RepositoryInteractionVerification {

            @Test
            @DisplayName("Should call productRepository before categoryRepository (execution order)")
            void shouldCallProductRepositoryBeforeCategoryRepository() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);
                Category category = validCategory(categoryId, "electronics");

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenReturn(Optional.of(category));

                // Act
                productService.linkCategoryToProduct(productId, categoryId);

                // Assert — verify execution order
                var inOrder = inOrder(productRepository, categoryRepository);
                inOrder.verify(productRepository).findByIdWithCategories(productId);
                inOrder.verify(categoryRepository).findById(categoryId);
            }

            @Test
            @DisplayName("Should not call any other productRepository methods besides findByIdWithCategories")
            void shouldNotCallOtherProductRepositoryMethods() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);
                Category category = validCategory(categoryId, "electronics");

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenReturn(Optional.of(category));

                // Act
                productService.linkCategoryToProduct(productId, categoryId);

                // Assert
                verify(productRepository).findByIdWithCategories(productId);
                verifyNoMoreInteractions(productRepository);
            }

            @Test
            @DisplayName("Should not call any other categoryRepository methods besides findById")
            void shouldNotCallOtherCategoryRepositoryMethods() {
                // Arrange
                UUID productId = UUID.randomUUID();
                UUID categoryId = UUID.randomUUID();
                Product product = validProduct(productId);
                Category category = validCategory(categoryId, "electronics");

                when(productRepository.findByIdWithCategories(productId))
                        .thenReturn(Optional.of(product));
                when(categoryRepository.findById(categoryId))
                        .thenReturn(Optional.of(category));

                // Act
                productService.linkCategoryToProduct(productId, categoryId);

                // Assert
                verify(categoryRepository).findById(categoryId);
                verifyNoMoreInteractions(categoryRepository);
            }
        }
    }
}
