package com.sivan.ecommerce.mapper.cart;

import com.sivan.ecommerce.dto.cart.CartItemRequestDTO;
import com.sivan.ecommerce.dto.cart.CartItemResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.cart.CartItem;
import com.sivan.ecommerce.entity.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CartItemMapper}.
 * Tests both mapping directions: DTO → Entity and Entity → DTO.
 * No Spring context is loaded — pure unit tests.
 */
@DisplayName("CartItemMapper — mapping methods")
class CartItemMapperTest {

    // ======================== Constants ========================

    private static final UUID VALID_PRODUCT_ID = UUID.randomUUID();
    private static final int VALID_QUANTITY = 3;
    private static final String VALID_PRODUCT_TITLE = "Wireless Bluetooth Headphones";

    // ==================== mapCartItemRequestToCartItem() ====================

    @Nested
    @DisplayName("mapCartItemRequestToCartItem()")
    class MapRequestToEntity {

        @Test
        @DisplayName("Should map quantity correctly from DTO to entity")
        void shouldMapQuantityCorrectly() {
            // Arrange
            CartItemRequestDTO request = new CartItemRequestDTO(VALID_PRODUCT_ID, VALID_QUANTITY);

            // Act
            CartItem cartItem = CartItemMapper.mapCartItemRequestToCartItem(request);

            // Assert
            assertNotNull(cartItem);
            assertEquals(VALID_QUANTITY, cartItem.getQuantity());
        }

        @Test
        @DisplayName("Should handle quantity of 1 (minimum valid quantity)")
        void shouldHandleMinimumQuantity() {
            // Arrange
            CartItemRequestDTO request = new CartItemRequestDTO(VALID_PRODUCT_ID, 1);

            // Act
            CartItem cartItem = CartItemMapper.mapCartItemRequestToCartItem(request);

            // Assert
            assertNotNull(cartItem);
            assertEquals(1, cartItem.getQuantity());
        }

        @Test
        @DisplayName("Should handle large quantity value")
        void shouldHandleLargeQuantity() {
            // Arrange
            CartItemRequestDTO request = new CartItemRequestDTO(VALID_PRODUCT_ID, Integer.MAX_VALUE);

            // Act
            CartItem cartItem = CartItemMapper.mapCartItemRequestToCartItem(request);

            // Assert
            assertNotNull(cartItem);
            assertEquals(Integer.MAX_VALUE, cartItem.getQuantity());
        }

        @Test
        @DisplayName("Should not set product on mapped entity (product is assigned by the service)")
        void shouldNotSetProductOnMappedEntity() {
            // Arrange
            CartItemRequestDTO request = new CartItemRequestDTO(VALID_PRODUCT_ID, VALID_QUANTITY);

            // Act
            CartItem cartItem = CartItemMapper.mapCartItemRequestToCartItem(request);

            // Assert
            assertNull(cartItem.getProduct());
        }

        @Test
        @DisplayName("Should not set cart on mapped entity (cart is assigned by the service)")
        void shouldNotSetCartOnMappedEntity() {
            // Arrange
            CartItemRequestDTO request = new CartItemRequestDTO(VALID_PRODUCT_ID, VALID_QUANTITY);

            // Act
            CartItem cartItem = CartItemMapper.mapCartItemRequestToCartItem(request);

            // Assert
            assertNull(cartItem.getCart());
        }

        @Test
        @DisplayName("Should not set ID on mapped entity (ID is assigned by Hibernate)")
        void shouldNotSetIdOnMappedEntity() {
            // Arrange
            CartItemRequestDTO request = new CartItemRequestDTO(VALID_PRODUCT_ID, VALID_QUANTITY);

            // Act
            CartItem cartItem = CartItemMapper.mapCartItemRequestToCartItem(request);

            // Assert
            assertNull(cartItem.getId());
        }
    }

    // ==================== mapCartItemToCartItemResponse() ====================

    @Nested
    @DisplayName("mapCartItemToCartItemResponse()")
    class MapEntityToResponse {

        @Test
        @DisplayName("Should map all fields correctly from entity to response DTO")
        void shouldMapAllFieldsCorrectly() {
            // Arrange
            Product product = new Product(
                    VALID_PRODUCT_TITLE, "Premium noise-cancelling headphones", 50,
                    7999L, "USD", "https://example.com/headphones.png"
            );
            EntityTestUtil.setId(product, VALID_PRODUCT_ID);

            CartItem cartItem = new CartItem(VALID_QUANTITY);
            cartItem.setProduct(product);

            // Act
            CartItemResponseDTO response = CartItemMapper.mapCartItemToCartItemResponse(cartItem);

            // Assert
            assertNotNull(response);
            assertEquals(VALID_PRODUCT_ID, response.productId());
            assertEquals(VALID_PRODUCT_TITLE, response.productTitle());
            assertEquals(VALID_QUANTITY, response.quantity());
        }

        @Test
        @DisplayName("Should map product with null ID (pre-persist state)")
        void shouldMapProductWithNullId() {
            // Arrange
            Product product = new Product(
                    VALID_PRODUCT_TITLE, "A great product", 10,
                    1999L, "USD", "https://example.com/product.png"
            );
            // ID is null — product not yet persisted

            CartItem cartItem = new CartItem(VALID_QUANTITY);
            cartItem.setProduct(product);

            // Act
            CartItemResponseDTO response = CartItemMapper.mapCartItemToCartItemResponse(cartItem);

            // Assert
            assertNotNull(response);
            assertNull(response.productId());
            assertEquals(VALID_PRODUCT_TITLE, response.productTitle());
        }

        @Test
        @DisplayName("Should map quantity of 1 correctly")
        void shouldMapMinimumQuantity() {
            // Arrange
            Product product = new Product(
                    VALID_PRODUCT_TITLE, "Description", 100,
                    4999L, "USD", "https://example.com/img.png"
            );
            EntityTestUtil.setId(product, VALID_PRODUCT_ID);

            CartItem cartItem = new CartItem(1);
            cartItem.setProduct(product);

            // Act
            CartItemResponseDTO response = CartItemMapper.mapCartItemToCartItemResponse(cartItem);

            // Assert
            assertEquals(1, response.quantity());
        }

        @Test
        @DisplayName("Should handle special characters in product title")
        void shouldHandleSpecialCharacters() {
            // Arrange
            Product product = new Product(
                    "MacBook Pro 16\"", "With M3 Max chip & 64GB RAM!", 5,
                    349999L, "USD", "https://example.com/macbook.png"
            );
            EntityTestUtil.setId(product, VALID_PRODUCT_ID);

            CartItem cartItem = new CartItem(VALID_QUANTITY);
            cartItem.setProduct(product);

            // Act
            CartItemResponseDTO response = CartItemMapper.mapCartItemToCartItemResponse(cartItem);

            // Assert
            assertEquals("MacBook Pro 16\"", response.productTitle());
        }

        @Test
        @DisplayName("Should handle Unicode characters in product title")
        void shouldHandleUnicodeCharacters() {
            // Arrange
            Product product = new Product(
                    "ワイヤレスイヤホン", "高品質のノイズキャンセリング", 20,
                    5999L, "JPY", "https://example.com/earphones.png"
            );
            EntityTestUtil.setId(product, VALID_PRODUCT_ID);

            CartItem cartItem = new CartItem(VALID_QUANTITY);
            cartItem.setProduct(product);

            // Act
            CartItemResponseDTO response = CartItemMapper.mapCartItemToCartItemResponse(cartItem);

            // Assert
            assertEquals("ワイヤレスイヤホン", response.productTitle());
        }
    }
}
