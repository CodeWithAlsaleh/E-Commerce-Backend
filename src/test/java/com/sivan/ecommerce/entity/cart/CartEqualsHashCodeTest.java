package com.sivan.ecommerce.entity.cart;

import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.customer.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/*
 *  Strategy 3 (variant): UUID Identity via FK — Cart uses customer.getId()
 *
 *  This works because Cart has a 1:1 relationship with Customer,
 *  enforced by the UNIQUE constraint on customer_id in DB.sql.
 *  There can only ever be one Cart per Customer.
 */
@DisplayName("Cart equals() & hashCode()")
class CartEqualsHashCodeTest {

    private Cart createCartWithCustomerId(UUID customerId) {
        Customer customer = new Customer("F", "L", "e@m.com", null, "p");
        EntityTestUtil.setId(customer, customerId);

        Cart cart = new Cart();
        customer.setCart(cart);

        return cart;
    }

    // ==================== equals() ====================

    @Nested
    @DisplayName("equals()")
    class Equals {

        @Test
        @DisplayName("same customer ID → equal")
        void sameCustomerId_shouldBeEqual() {
            UUID customerId = UUID.randomUUID();

            Cart c1 = createCartWithCustomerId(customerId);
            Cart c2 = createCartWithCustomerId(customerId);

            assertEquals(c1, c2);
        }

        @Test
        @DisplayName("different customer ID → not equal")
        void differentCustomerId_shouldNotBeEqual() {
            Cart c1 = createCartWithCustomerId(UUID.randomUUID());
            Cart c2 = createCartWithCustomerId(UUID.randomUUID());

            assertNotEquals(c1, c2);
        }

        @Test
        @DisplayName("same instance → equal (reflexive)")
        void sameInstance_shouldBeEqual() {
            Cart c1 = createCartWithCustomerId(UUID.randomUUID());

            assertEquals(c1, c1);
        }

        @Test
        @DisplayName("symmetric: c1.equals(c2) == c2.equals(c1)")
        void shouldBeSymmetric() {
            UUID customerId = UUID.randomUUID();

            Cart c1 = createCartWithCustomerId(customerId);
            Cart c2 = createCartWithCustomerId(customerId);

            assertEquals(c1, c2);
            assertEquals(c2, c1);
        }

        @Test
        @DisplayName("null → not equal")
        void null_shouldNotBeEqual() {
            Cart c1 = createCartWithCustomerId(UUID.randomUUID());

            assertNotEquals(null, c1);
        }

        @Test
        @DisplayName("different type → not equal")
        void differentType_shouldNotBeEqual() {
            Cart c1 = createCartWithCustomerId(UUID.randomUUID());

            assertNotEquals("a string", c1);
        }
    }

    // ==================== hashCode() ====================

    @Nested
    @DisplayName("hashCode()")
    class HashCode {

        @Test
        @DisplayName("same customer ID → same hashCode")
        void sameCustomerId_shouldHaveSameHashCode() {
            UUID customerId = UUID.randomUUID();

            Cart c1 = createCartWithCustomerId(customerId);
            Cart c2 = createCartWithCustomerId(customerId);

            assertEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        @DisplayName("different customer ID → different hashCode")
        void differentCustomerId_shouldHaveDifferentHashCode() {
            Cart c1 = createCartWithCustomerId(UUID.randomUUID());
            Cart c2 = createCartWithCustomerId(UUID.randomUUID());

            assertNotEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        @DisplayName("consistent: multiple calls return same value")
        void shouldBeConsistent() {
            Cart c1 = createCartWithCustomerId(UUID.randomUUID());

            int hash1 = c1.hashCode();
            int hash2 = c1.hashCode();

            assertEquals(hash1, hash2);
        }
    }
}
