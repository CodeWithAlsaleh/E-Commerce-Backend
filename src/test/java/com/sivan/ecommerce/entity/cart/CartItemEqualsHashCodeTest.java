package com.sivan.ecommerce.entity.cart;

import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.customer.Customer;
import com.sivan.ecommerce.entity.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/*
 *  Strategy 2: Composite FK Identity — CartItem uses (cart.id + product.id)
 */
@DisplayName("CartItem equals() & hashCode()")
class CartItemEqualsHashCodeTest {

    private Cart createCartWithId(UUID id) {
        Cart cart = new Cart(new Customer("F", "L", "e@m.com", null, "p"));
        EntityTestUtil.setId(cart, id);
        return cart;
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
        @DisplayName("same cart + same product → equal")
        void sameCartAndProduct_shouldBeEqual() {
            UUID cartId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            CartItem item1 = new CartItem(createCartWithId(cartId), createProductWithId(productId), 1);
            CartItem item2 = new CartItem(createCartWithId(cartId), createProductWithId(productId), 5);

            assertEquals(item1, item2);
        }

        @Test
        @DisplayName("different cart + same product → not equal")
        void differentCart_shouldNotBeEqual() {
            UUID productId = UUID.randomUUID();

            CartItem item1 = new CartItem(createCartWithId(UUID.randomUUID()), createProductWithId(productId), 1);
            CartItem item2 = new CartItem(createCartWithId(UUID.randomUUID()), createProductWithId(productId), 1);

            assertNotEquals(item1, item2);
        }

        @Test
        @DisplayName("same cart + different product → not equal")
        void differentProduct_shouldNotBeEqual() {
            UUID cartId = UUID.randomUUID();

            CartItem item1 = new CartItem(createCartWithId(cartId), createProductWithId(UUID.randomUUID()), 1);
            CartItem item2 = new CartItem(createCartWithId(cartId), createProductWithId(UUID.randomUUID()), 1);

            assertNotEquals(item1, item2);
        }

        @Test
        @DisplayName("same instance → equal (reflexive)")
        void sameInstance_shouldBeEqual() {
            CartItem item = new CartItem(createCartWithId(UUID.randomUUID()), createProductWithId(UUID.randomUUID()), 1);

            assertEquals(item, item);
        }

        @Test
        @DisplayName("null → not equal")
        void null_shouldNotBeEqual() {
            CartItem item = new CartItem(createCartWithId(UUID.randomUUID()), createProductWithId(UUID.randomUUID()), 1);

            assertNotEquals(null, item);
        }

        @Test
        @DisplayName("different type → not equal")
        void differentType_shouldNotBeEqual() {
            CartItem item = new CartItem(createCartWithId(UUID.randomUUID()), createProductWithId(UUID.randomUUID()), 1);

            assertNotEquals("a string", item);
        }

        @Test
        @DisplayName("different quantity but same cart+product → still equal (quantity is not part of identity)")
        void differentQuantity_shouldStillBeEqual() {
            UUID cartId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            CartItem item1 = new CartItem(createCartWithId(cartId), createProductWithId(productId), 1);
            CartItem item2 = new CartItem(createCartWithId(cartId), createProductWithId(productId), 99);

            assertEquals(item1, item2);
        }
    }

    // ==================== hashCode() ====================

    @Nested
    @DisplayName("hashCode()")
    class HashCode {

        @Test
        @DisplayName("same cart + same product → same hashCode")
        void sameCartAndProduct_shouldHaveSameHashCode() {
            UUID cartId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            CartItem item1 = new CartItem(createCartWithId(cartId), createProductWithId(productId), 1);
            CartItem item2 = new CartItem(createCartWithId(cartId), createProductWithId(productId), 5);

            assertEquals(item1.hashCode(), item2.hashCode());
        }

        @Test
        @DisplayName("different cart or product → different hashCode")
        void differentCartOrProduct_shouldHaveDifferentHashCode() {
            CartItem item1 = new CartItem(createCartWithId(UUID.randomUUID()), createProductWithId(UUID.randomUUID()), 1);
            CartItem item2 = new CartItem(createCartWithId(UUID.randomUUID()), createProductWithId(UUID.randomUUID()), 1);

            assertNotEquals(item1.hashCode(), item2.hashCode());
        }

        @Test
        @DisplayName("consistent: multiple calls return same value")
        void shouldBeConsistent() {
            CartItem item1 = new CartItem(createCartWithId(UUID.randomUUID()), createProductWithId(UUID.randomUUID()), 1);

            int hash1 = item1.hashCode();
            int hash2 = item1.hashCode();

            assertEquals(hash1, hash2);
        }
    }
}
