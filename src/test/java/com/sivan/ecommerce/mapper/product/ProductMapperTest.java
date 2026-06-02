package com.sivan.ecommerce.mapper.product;

import com.sivan.ecommerce.dto.product.ProductRequestDTO;
import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProductMapper}.
 * Tests both mapping directions: DTO → Entity and Entity → DTO.
 * No Spring context is loaded — pure unit tests.
 */
@DisplayName("ProductMapper — mapping methods")
class ProductMapperTest {

    // ======================== Constants ========================

    private static final String VALID_TITLE = "Wireless Bluetooth Headphones";
    private static final String VALID_DESCRIPTION = "Premium noise-cancelling headphones with deep bass.";
    private static final int VALID_QUANTITY = 50;
    private static final long VALID_PRICE = 7999L;
    private static final String VALID_CURRENCY = "USD";
    private static final String VALID_IMAGE_URL = "https://example.com/images/headphones.png";

    // ==================== mapProductRequestToProduct() ====================

    @Nested
    @DisplayName("mapProductRequestToProduct()")
    class MapRequestToEntity {

        @Test
        @DisplayName("Should map all fields correctly from DTO to entity")
        void shouldMapAllFieldsCorrectly() {
            // Arrange
            ProductRequestDTO request = new ProductRequestDTO(
                    VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                    VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );

            // Act
            Product product = ProductMapper.mapProductRequestToProduct(request);

            // Assert
            assertNotNull(product);
            assertEquals(VALID_TITLE, product.getTitle());
            assertEquals(VALID_DESCRIPTION, product.getDescription());
            assertEquals(VALID_QUANTITY, product.getQuantity());
            assertEquals(VALID_PRICE, product.getPrice());
            assertEquals(VALID_CURRENCY, product.getCurrencyCode());
            assertEquals(VALID_IMAGE_URL, product.getImageUrl());
        }

        @Test
        @DisplayName("Should handle null description (optional field)")
        void shouldHandleNullDescription() {
            // Arrange
            ProductRequestDTO request = new ProductRequestDTO(
                    VALID_TITLE, null, VALID_QUANTITY,
                    VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );

            // Act
            Product product = ProductMapper.mapProductRequestToProduct(request);

            // Assert
            assertNotNull(product);
            assertNull(product.getDescription());
            // Other fields should still be mapped
            assertEquals(VALID_TITLE, product.getTitle());
            assertEquals(VALID_QUANTITY, product.getQuantity());
            assertEquals(VALID_PRICE, product.getPrice());
            assertEquals(VALID_CURRENCY, product.getCurrencyCode());
            assertEquals(VALID_IMAGE_URL, product.getImageUrl());
        }

        @Test
        @DisplayName("Should preserve trimmed/uppercased values from compact constructor")
        void shouldPreserveTrimmedValues() {
            // Arrange — the DTO compact constructor trims string fields and uppercases currency
            ProductRequestDTO request = new ProductRequestDTO(
                    "  Wireless Mouse  ", "  Ergonomic design  ", 100,
                    2999L, "  eur  ", "  https://example.com/mouse.png  "
            );

            // Act
            Product product = ProductMapper.mapProductRequestToProduct(request);

            // Assert — values should already be trimmed/uppercased by the DTO constructor
            assertEquals("Wireless Mouse", product.getTitle());
            assertEquals("Ergonomic design", product.getDescription());
            assertEquals("EUR", product.getCurrencyCode()); // Trimmed and Uppercased
            assertEquals("https://example.com/mouse.png", product.getImageUrl());
        }

        @Test
        @DisplayName("Should handle special characters in title and description")
        void shouldHandleSpecialCharacters() {
            // Arrange
            ProductRequestDTO request = new ProductRequestDTO(
                    "MacBook Pro 16\"", "With M3 Max chip & 64GB RAM!", VALID_QUANTITY,
                    VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );

            // Act
            Product product = ProductMapper.mapProductRequestToProduct(request);

            // Assert
            assertEquals("MacBook Pro 16\"", product.getTitle());
            assertEquals("With M3 Max chip & 64GB RAM!", product.getDescription());
        }

        @Test
        @DisplayName("Should handle Unicode characters in text fields")
        void shouldHandleUnicodeCharacters() {
            // Arrange
            ProductRequestDTO request = new ProductRequestDTO(
                    "ワイヤレスイヤホン", "高品質のノイズキャンセリング", VALID_QUANTITY,
                    VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );

            // Act
            Product product = ProductMapper.mapProductRequestToProduct(request);

            // Assert
            assertEquals("ワイヤレスイヤホン", product.getTitle());
            assertEquals("高品質のノイズキャンセリング", product.getDescription());
        }

        @Test
        @DisplayName("Should not set ID on mapped entity (ID is assigned by Hibernate)")
        void shouldNotSetIdOnMappedEntity() {
            // Arrange
            ProductRequestDTO request = new ProductRequestDTO(
                    VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                    VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );

            // Act
            Product product = ProductMapper.mapProductRequestToProduct(request);

            // Assert
            assertNull(product.getId());
        }

        @Test
        @DisplayName("Should not set categories on mapped entity (managed externally)")
        void shouldNotSetCategoriesOnMappedEntity() {
            // Arrange
            ProductRequestDTO request = new ProductRequestDTO(
                    VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                    VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );

            // Act
            Product product = ProductMapper.mapProductRequestToProduct(request);

            // Assert
            assertNotNull(product.getCategories());
            assertTrue(product.getCategories().isEmpty());
        }
    }

    // ==================== mapProductToProductResponse() ====================

    @Nested
    @DisplayName("mapProductToProductResponse()")
    class MapEntityToResponse {

        @Test
        @DisplayName("Should map all fields correctly from entity to response DTO")
        void shouldMapAllFieldsCorrectly() {
            // Arrange
            UUID expectedId = UUID.randomUUID();
            Product product = new Product(
                    VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                    VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );
            EntityTestUtil.setId(product, expectedId);

            // Act
            ProductResponseDTO response = ProductMapper.mapProductToProductResponse(product);

            // Assert
            assertNotNull(response);
            assertEquals(expectedId, response.id());
            assertEquals(VALID_TITLE, response.title());
            assertEquals(VALID_DESCRIPTION, response.description());
            assertEquals(VALID_QUANTITY, response.quantity());
            assertEquals(VALID_PRICE, response.price());
            assertEquals(VALID_CURRENCY, response.currencyCode());
            assertEquals(VALID_IMAGE_URL, response.imageUrl());
        }

        @Test
        @DisplayName("Should map null description correctly")
        void shouldMapNullDescription() {
            // Arrange
            UUID expectedId = UUID.randomUUID();
            Product product = new Product(
                    VALID_TITLE, null, VALID_QUANTITY,
                    VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );
            EntityTestUtil.setId(product, expectedId);

            // Act
            ProductResponseDTO response = ProductMapper.mapProductToProductResponse(product);

            // Assert
            assertNull(response.description());
            assertEquals(VALID_TITLE, response.title());
            assertEquals(VALID_IMAGE_URL, response.imageUrl());
        }

        @Test
        @DisplayName("Should map entity with null ID (pre-persist state)")
        void shouldMapEntityWithNullId() {
            // Arrange
            Product product = new Product(
                    VALID_TITLE, VALID_DESCRIPTION, VALID_QUANTITY,
                    VALID_PRICE, VALID_CURRENCY, VALID_IMAGE_URL
            );
            // ID is null — entity not yet persisted

            // Act
            ProductResponseDTO response = ProductMapper.mapProductToProductResponse(product);

            // Assert
            assertNotNull(response);
            assertNull(response.id());
        }
    }
}
