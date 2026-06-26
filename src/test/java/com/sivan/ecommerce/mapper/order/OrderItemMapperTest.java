package com.sivan.ecommerce.mapper.order;

import com.sivan.ecommerce.dto.order.OrderItemResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.order.Order;
import com.sivan.ecommerce.entity.order.OrderItem;
import com.sivan.ecommerce.entity.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OrderItemMapper}.
 * Tests mapping Entity → DTO.
 * No Spring context is loaded — pure unit tests.
 */
@DisplayName("OrderItemMapper — mapping methods")
class OrderItemMapperTest {

    private static final UUID VALID_PRODUCT_ID = UUID.randomUUID();
    private static final String VALID_PRODUCT_TITLE = "Smartphone Pro Max";
    private static final int VALID_QUANTITY = 2;
    private static final long VALID_LOCKED_PRICE = 99999L;

    @Nested
    @DisplayName("mapOrderItemToOrderItemResponse()")
    class MapOrderItemToOrderItemResponse {

        @Test
        @DisplayName("Should map all fields correctly from entity to response DTO")
        void shouldMapAllFieldsCorrectly() {
            // Arrange
            Product product = new Product(
                    VALID_PRODUCT_TITLE, "Description", 100,
                    120000L, "USD", "https://example.com/phone.png"
            );
            EntityTestUtil.setId(product, VALID_PRODUCT_ID);

            Order order = new Order();
            EntityTestUtil.setId(order, UUID.randomUUID());

            OrderItem orderItem = new OrderItem(order, product, VALID_QUANTITY, VALID_LOCKED_PRICE);
            EntityTestUtil.setId(orderItem, UUID.randomUUID());

            // Act
            OrderItemResponseDTO response = OrderItemMapper.mapOrderItemToOrderItemResponse(orderItem);

            // Assert
            assertNotNull(response);
            assertEquals(VALID_PRODUCT_ID, response.productId());
            assertEquals(VALID_PRODUCT_TITLE, response.productTitle());
            assertEquals(VALID_QUANTITY, response.quantity());
            assertEquals(VALID_LOCKED_PRICE, response.lockedPrice());
        }

        @Test
        @DisplayName("Should handle product with null ID (pre-persist state)")
        void shouldHandleProductWithNullId() {
            // Arrange
            Product product = new Product(
                    VALID_PRODUCT_TITLE, "Description", 100,
                    120000L, "USD", "https://example.com/phone.png"
            );
            // Product ID is deliberately left null

            Order order = new Order();
            OrderItem orderItem = new OrderItem(order, product, VALID_QUANTITY, VALID_LOCKED_PRICE);

            // Act
            OrderItemResponseDTO response = OrderItemMapper.mapOrderItemToOrderItemResponse(orderItem);

            // Assert
            assertNotNull(response);
            assertNull(response.productId());
            assertEquals(VALID_PRODUCT_TITLE, response.productTitle());
            assertEquals(VALID_QUANTITY, response.quantity());
            assertEquals(VALID_LOCKED_PRICE, response.lockedPrice());
        }

        @Test
        @DisplayName("Should handle special characters in product title")
        void shouldHandleSpecialCharactersInProductTitle() {
            // Arrange
            String specialTitle = "Gaming Monitor 27\" @144Hz!";
            Product product = new Product(
                    specialTitle, "Description", 10,
                    30000L, "USD", "https://example.com/monitor.png"
            );
            EntityTestUtil.setId(product, VALID_PRODUCT_ID);

            OrderItem orderItem = new OrderItem(new Order(), product, 1, 30000L);

            // Act
            OrderItemResponseDTO response = OrderItemMapper.mapOrderItemToOrderItemResponse(orderItem);

            // Assert
            assertEquals(specialTitle, response.productTitle());
        }

        @Test
        @DisplayName("Should handle Unicode characters in product title")
        void shouldHandleUnicodeCharactersInProductTitle() {
            // Arrange
            String unicodeTitle = "スマートウォッチ";
            Product product = new Product(
                    unicodeTitle, "Description", 10,
                    30000L, "JPY", "https://example.com/watch.png"
            );
            EntityTestUtil.setId(product, VALID_PRODUCT_ID);

            OrderItem orderItem = new OrderItem(new Order(), product, 1, 30000L);

            // Act
            OrderItemResponseDTO response = OrderItemMapper.mapOrderItemToOrderItemResponse(orderItem);

            // Assert
            assertEquals(unicodeTitle, response.productTitle());
        }
    }

    @Nested
    @DisplayName("mapOrderItemsToOrderItemsResponse()")
    class MapOrderItemsToOrderItemsResponse {

        @Test
        @DisplayName("Should correctly map a set of OrderItems")
        void shouldCorrectlyMapSetOfOrderItems() {
            // Arrange
            Product product1 = new Product("Product 1", "Desc", 10, 1000L, "USD", "url1");
            EntityTestUtil.setId(product1, UUID.randomUUID());
            
            Product product2 = new Product("Product 2", "Desc", 10, 2000L, "USD", "url2");
            EntityTestUtil.setId(product2, UUID.randomUUID());

            Order order = new Order();
            EntityTestUtil.setId(order, UUID.randomUUID());

            OrderItem item1 = new OrderItem(order, product1, 1, 1000L);
            OrderItem item2 = new OrderItem(order, product2, 2, 2000L);
            
            Set<OrderItem> items = new HashSet<>();
            items.add(item1);
            items.add(item2);

            // Act
            Set<OrderItemResponseDTO> responses = OrderItemMapper.mapOrderItemsToOrderItemsResponse(items);

            // Assert
            assertNotNull(responses);
            assertEquals(2, responses.size());
            
            boolean containsProduct1 = responses.stream().anyMatch(r -> r.productTitle().equals("Product 1"));
            boolean containsProduct2 = responses.stream().anyMatch(r -> r.productTitle().equals("Product 2"));
            
            assertTrue(containsProduct1);
            assertTrue(containsProduct2);
        }

        @Test
        @DisplayName("Should handle an empty set of OrderItems")
        void shouldHandleEmptySetOfOrderItems() {
            // Arrange
            Set<OrderItem> emptyItems = new HashSet<>();

            // Act
            Set<OrderItemResponseDTO> responses = OrderItemMapper.mapOrderItemsToOrderItemsResponse(emptyItems);

            // Assert
            assertNotNull(responses);
            assertTrue(responses.isEmpty());
        }
    }
}
