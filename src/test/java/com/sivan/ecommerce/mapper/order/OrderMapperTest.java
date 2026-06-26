package com.sivan.ecommerce.mapper.order;

import com.sivan.ecommerce.dto.order.OrderResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.order.Order;
import com.sivan.ecommerce.entity.order.OrderItem;
import com.sivan.ecommerce.entity.order.Status;
import com.sivan.ecommerce.entity.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OrderMapper}.
 * Tests mapping Entity → DTO.
 * No Spring context is loaded — pure unit tests.
 */
@DisplayName("OrderMapper — mapping methods")
class OrderMapperTest {

    private static final UUID VALID_ORDER_ID = UUID.randomUUID();
    private static final Status VALID_STATUS = Status.PENDING; // Assuming Status.PENDING exists, update if different
    private static final long VALID_TOTAL_PRICE = 150000L;
    private static final String VALID_SHIPPING_ADDRESS = "123 Main St, Springfield";

    @Nested
    @DisplayName("mapOrderToOrderResponse()")
    class MapOrderToOrderResponse {

        @Test
        @DisplayName("Should map all basic fields correctly from entity to response DTO")
        void shouldMapAllBasicFieldsCorrectly() {
            // Arrange
            Order order = new Order(null, VALID_STATUS, VALID_TOTAL_PRICE, VALID_SHIPPING_ADDRESS);
            EntityTestUtil.setId(order, VALID_ORDER_ID);

            Instant now = Instant.now();
            ReflectionTestUtils.setField(order, "createdAt", now);

            // Act
            OrderResponseDTO response = OrderMapper.mapOrderToOrderResponse(order);

            // Assert
            assertNotNull(response);
            assertEquals(VALID_ORDER_ID, response.id());
            assertEquals(VALID_STATUS, response.status());
            assertEquals(VALID_TOTAL_PRICE, response.totalPrice());
            assertEquals(VALID_SHIPPING_ADDRESS, response.shippingAddress());
            assertEquals(now, response.createdAt());
            assertNotNull(response.orderItems());
            assertTrue(response.orderItems().isEmpty());
        }

        @Test
        @DisplayName("Should correctly map nested OrderItems via OrderItemMapper")
        void shouldCorrectlyMapNestedOrderItems() {
            // Arrange
            Order order = new Order(null, VALID_STATUS, VALID_TOTAL_PRICE, VALID_SHIPPING_ADDRESS);
            EntityTestUtil.setId(order, VALID_ORDER_ID);

            Product product = new Product(
                    "Sample Product", "Description", 10,
                    5000L, "USD", "url"
            );
            EntityTestUtil.setId(product, UUID.randomUUID());

            OrderItem item = new OrderItem(order, product, 2, 5000L);
            order.addOrderItem(item); // Note: addOrderItem internally sets the order reference on the item

            // Act
            OrderResponseDTO response = OrderMapper.mapOrderToOrderResponse(order);

            // Assert
            assertNotNull(response);
            assertNotNull(response.orderItems());
            assertEquals(1, response.orderItems().size());

            var itemResponse = response.orderItems().iterator().next();
            assertEquals("Sample Product", itemResponse.productTitle());
            assertEquals(2, itemResponse.quantity());
            assertEquals(5000L, itemResponse.lockedPrice());
        }

        @Test
        @DisplayName("Should handle order with null ID (pre-persist state)")
        void shouldHandleOrderWithNullId() {
            // Arrange
            Order order = new Order(null, VALID_STATUS, VALID_TOTAL_PRICE, VALID_SHIPPING_ADDRESS);
            // ID is deliberately left null

            // Act
            OrderResponseDTO response = OrderMapper.mapOrderToOrderResponse(order);

            // Assert
            assertNotNull(response);
            assertNull(response.id());
            assertEquals(VALID_STATUS, response.status());
            assertEquals(VALID_TOTAL_PRICE, response.totalPrice());
            assertEquals(VALID_SHIPPING_ADDRESS, response.shippingAddress());
        }

        @Test
        @DisplayName("Should handle special characters in shipping address")
        void shouldHandleSpecialCharactersInShippingAddress() {
            // Arrange
            String specialAddress = "Apt. 4B, 123 Main St. & Blvd. #55!";
            Order order = new Order(null, VALID_STATUS, VALID_TOTAL_PRICE, specialAddress);

            // Act
            OrderResponseDTO response = OrderMapper.mapOrderToOrderResponse(order);

            // Assert
            assertNotNull(response);
            assertEquals(specialAddress, response.shippingAddress());
        }

        @Test
        @DisplayName("Should handle Unicode characters in shipping address")
        void shouldHandleUnicodeCharactersInShippingAddress() {
            // Arrange
            String unicodeAddress = "東京都渋谷区神南1-1-1"; // Example Unicode address
            Order order = new Order(null, VALID_STATUS, VALID_TOTAL_PRICE, unicodeAddress);

            // Act
            OrderResponseDTO response = OrderMapper.mapOrderToOrderResponse(order);

            // Assert
            assertNotNull(response);
            assertEquals(unicodeAddress, response.shippingAddress());
        }

        @Test
        @DisplayName("Should handle null createdAt (pre-persist state or absent auditing)")
        void shouldHandleNullCreatedAt() {
            // Arrange
            Order order = new Order(null, VALID_STATUS, VALID_TOTAL_PRICE, VALID_SHIPPING_ADDRESS);
            // createdAt is left as null

            // Act
            OrderResponseDTO response = OrderMapper.mapOrderToOrderResponse(order);

            // Assert
            assertNotNull(response);
            assertNull(response.createdAt());
        }
    }
}
