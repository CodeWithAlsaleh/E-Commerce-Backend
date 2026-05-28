package com.sivan.ecommerce.entity.order;

import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.product.Product;
import com.sivan.ecommerce.entity.customer.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/*
 *  Strategy 2: Composite FK Identity — OrderItem uses (order.id + product.id)
 */
@DisplayName("OrderItem equals() & hashCode()")
class OrderItemEqualsHashCodeTest {

    private Order createOrderWithId(UUID id) {
        Customer customer = new Customer("F", "L", "e@m.com", null, "p");
        Order order = new Order(customer, Status.PENDING, 5000L, "123 Street");
        EntityTestUtil.setId(order, id);
        return order;
    }

    private Product createProductWithId(UUID id) {
        Product product = new Product("Title", "Desc", 10, 1000L, "USD", "http://img.png");
        EntityTestUtil.setId(product, id);
        return product;
    }

    // ==================== equals() ====================

    @Nested
    @DisplayName("equals()")
    class Equals {

        @Test
        @DisplayName("same order + same product → equal")
        void sameOrderAndProduct_shouldBeEqual() {
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            OrderItem item1 = new OrderItem(createOrderWithId(orderId), createProductWithId(productId), 2, 1000L);
            OrderItem item2 = new OrderItem(createOrderWithId(orderId), createProductWithId(productId), 5, 2000L);

            assertEquals(item1, item2);
        }

        @Test
        @DisplayName("different order + same product → not equal")
        void differentOrder_shouldNotBeEqual() {
            UUID productId = UUID.randomUUID();

            OrderItem item1 = new OrderItem(createOrderWithId(UUID.randomUUID()), createProductWithId(productId), 1, 1000L);
            OrderItem item2 = new OrderItem(createOrderWithId(UUID.randomUUID()), createProductWithId(productId), 1, 1000L);

            assertNotEquals(item1, item2);
        }

        @Test
        @DisplayName("same order + different product → not equal")
        void differentProduct_shouldNotBeEqual() {
            UUID orderId = UUID.randomUUID();

            OrderItem item1 = new OrderItem(createOrderWithId(orderId), createProductWithId(UUID.randomUUID()), 1, 1000L);
            OrderItem item2 = new OrderItem(createOrderWithId(orderId), createProductWithId(UUID.randomUUID()), 1, 1000L);

            assertNotEquals(item1, item2);
        }

        @Test
        @DisplayName("same instance → equal (reflexive)")
        void sameInstance_shouldBeEqual() {
            OrderItem item = new OrderItem(createOrderWithId(UUID.randomUUID()), createProductWithId(UUID.randomUUID()), 1, 1000L);

            assertEquals(item, item);
        }

        @Test
        @DisplayName("null → not equal")
        void null_shouldNotBeEqual() {
            OrderItem item = new OrderItem(createOrderWithId(UUID.randomUUID()), createProductWithId(UUID.randomUUID()), 1, 1000L);

            assertNotEquals(null, item);
        }

        @Test
        @DisplayName("different type → not equal")
        void differentType_shouldNotBeEqual() {
            OrderItem item = new OrderItem(createOrderWithId(UUID.randomUUID()), createProductWithId(UUID.randomUUID()), 1, 1000L);

            assertNotEquals("a string", item);
        }

        @Test
        @DisplayName("different quantity/price but same order+product → still equal")
        void differentQuantityAndPrice_shouldStillBeEqual() {
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            OrderItem item1 = new OrderItem(createOrderWithId(orderId), createProductWithId(productId), 1, 500L);
            OrderItem item2 = new OrderItem(createOrderWithId(orderId), createProductWithId(productId), 99, 9999L);

            assertEquals(item1, item2);
        }
    }

    // ==================== hashCode() ====================

    @Nested
    @DisplayName("hashCode()")
    class HashCode {

        @Test
        @DisplayName("same order + same product → same hashCode")
        void sameOrderAndProduct_shouldHaveSameHashCode() {
            UUID orderId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            OrderItem item1 = new OrderItem(createOrderWithId(orderId), createProductWithId(productId), 1, 1000L);
            OrderItem item2 = new OrderItem(createOrderWithId(orderId), createProductWithId(productId), 5, 2000L);

            assertEquals(item1.hashCode(), item2.hashCode());
        }

        @Test
        @DisplayName("different order or product → different hashCode")
        void differentOrderOrProduct_shouldHaveDifferentHashCode() {
            OrderItem item1 = new OrderItem(createOrderWithId(UUID.randomUUID()), createProductWithId(UUID.randomUUID()), 1, 1000L);
            OrderItem item2 = new OrderItem(createOrderWithId(UUID.randomUUID()), createProductWithId(UUID.randomUUID()), 1, 1000L);

            assertNotEquals(item1.hashCode(), item2.hashCode());
        }

        @Test
        @DisplayName("consistent: multiple calls return same value")
        void shouldBeConsistent() {
            OrderItem item1 = new OrderItem(createOrderWithId(UUID.randomUUID()), createProductWithId(UUID.randomUUID()), 1, 1000L);

            int hash1 = item1.hashCode();
            int hash2 = item1.hashCode();

            assertEquals(hash1, hash2);
        }
    }
}
