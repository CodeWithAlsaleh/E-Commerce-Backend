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
        Customer customer = new Customer("F", "L", "e@m.com", null, "p");
        Cart cart = new Cart();

        customer.setCart(cart);
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
            Cart cart = createCartWithId(UUID.randomUUID());
            Product product = createProductWithId(UUID.randomUUID());

            CartItem item1 = new CartItem(1);
            item1.setCart(cart);
            item1.setProduct(product);

            CartItem item2 = new CartItem(1);
            item2.setCart(cart);
            item2.setProduct(product);

            assertEquals(item1, item2);
        }

        @Test
        @DisplayName("different cart + same product → not equal")
        void differentCart_shouldNotBeEqual() {
            Cart cart1 = createCartWithId(UUID.randomUUID());
            Cart cart2 = createCartWithId(UUID.randomUUID());

            Product product = createProductWithId(UUID.randomUUID());

            CartItem item1 = new CartItem(1);
            item1.setCart(cart1);
            item1.setProduct(product);

            CartItem item2 = new CartItem(1);
            item2.setCart(cart2);
            item2.setProduct(product);

            assertNotEquals(item1, item2);
        }

        @Test
        @DisplayName("same cart + different product → not equal")
        void differentProduct_shouldNotBeEqual() {
            Cart cart = createCartWithId(UUID.randomUUID());

            Product product1 = createProductWithId(UUID.randomUUID());
            Product product2 = createProductWithId(UUID.randomUUID());

            CartItem item1 = new CartItem(1);
            item1.setCart(cart);
            item1.setProduct(product1);

            CartItem item2 = new CartItem(1);
            item2.setCart(cart);
            item2.setProduct(product2);

            assertNotEquals(item1, item2);
        }

        @Test
        @DisplayName("same instance → equal (reflexive)")
        void sameInstance_shouldBeEqual() {
            Cart cart = createCartWithId(UUID.randomUUID());
            Product product = createProductWithId(UUID.randomUUID());

            CartItem item = new CartItem(1);
            item.setCart(cart);
            item.setProduct(product);

            assertEquals(item, item);
        }

        @Test
        @DisplayName("null → not equal")
        void null_shouldNotBeEqual() {
            Cart cart = createCartWithId(UUID.randomUUID());
            Product product = createProductWithId(UUID.randomUUID());

            CartItem item = new CartItem(1);
            item.setCart(cart);
            item.setProduct(product);

            assertNotEquals(null, item);
        }

        @Test
        @DisplayName("different type → not equal")
        void differentType_shouldNotBeEqual() {
            Cart cart = createCartWithId(UUID.randomUUID());
            Product product = createProductWithId(UUID.randomUUID());

            CartItem item = new CartItem(1);
            item.setCart(cart);
            item.setProduct(product);

            assertNotEquals("a string", item);
        }

        @Test
        @DisplayName("different quantity but same cart+product → still equal (quantity is not part of identity)")
        void differentQuantity_shouldStillBeEqual() {
            Cart cart = createCartWithId(UUID.randomUUID());
            Product product = createProductWithId(UUID.randomUUID());

            CartItem item1 = new CartItem(1);
            item1.setCart(cart);
            item1.setProduct(product);

            CartItem item2 = new CartItem(99);
            item2.setCart(cart);
            item2.setProduct(product);

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
            Cart cart = createCartWithId(UUID.randomUUID());
            Product product = createProductWithId(UUID.randomUUID());

            CartItem item1 = new CartItem(1);
            item1.setCart(cart);
            item1.setProduct(product);

            CartItem item2 = new CartItem(99);
            item2.setCart(cart);
            item2.setProduct(product);

            assertEquals(item1.hashCode(), item2.hashCode());
        }

        @Test
        @DisplayName("different cart or product → different hashCode")
        void differentCartOrProduct_shouldHaveDifferentHashCode() {
            Cart cart1 = createCartWithId(UUID.randomUUID());
            Cart cart2 = createCartWithId(UUID.randomUUID());

            Product product1 = createProductWithId(UUID.randomUUID());
            Product product2 = createProductWithId(UUID.randomUUID());

            CartItem item1 = new CartItem(1);
            item1.setCart(cart1);
            item1.setProduct(product1);

            CartItem item2 = new CartItem(1);
            item2.setCart(cart2);
            item2.setProduct(product2);

            assertNotEquals(item1.hashCode(), item2.hashCode());
        }

        @Test
        @DisplayName("consistent: multiple calls return same value")
        void shouldBeConsistent() {
            Cart cart = createCartWithId(UUID.randomUUID());
            Product product = createProductWithId(UUID.randomUUID());

            CartItem item = new CartItem(1);
            item.setCart(cart);
            item.setProduct(product);

            int hash1 = item.hashCode();
            int hash2 = item.hashCode();

            assertEquals(hash1, hash2);
        }
    }
}
