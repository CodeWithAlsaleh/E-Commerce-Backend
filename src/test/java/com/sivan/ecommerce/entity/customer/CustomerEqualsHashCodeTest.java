package com.sivan.ecommerce.entity.customer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 *  Strategy 1: Natural Business Key — Customer uses 'email'
 */
@DisplayName("Customer equals() & hashCode()")
class CustomerEqualsHashCodeTest {

    private Customer createCustomer(String email) {
        return new Customer("First", "Last", email, "Location", "password");
    }

    // ==================== equals() ====================

    @Nested
    @DisplayName("equals()")
    class Equals {

        @Test
        @DisplayName("same email → equal")
        void sameEmail_shouldBeEqual() {
            Customer c1 = createCustomer("test@mail.com");
            Customer c2 = createCustomer("test@mail.com");

            assertEquals(c1, c2);
        }

        @Test
        @DisplayName("different email → not equal")
        void differentEmail_shouldNotBeEqual() {
            Customer c1 = createCustomer("a@mail.com");
            Customer c2 = createCustomer("b@mail.com");

            assertNotEquals(c1, c2);
        }

        @Test
        @DisplayName("same instance → equal (reflexive)")
        void sameInstance_shouldBeEqual() {
            Customer c1 = createCustomer("test@mail.com");

            assertEquals(c1, c1);
        }

        @Test
        @DisplayName("symmetric: c1.equals(c2) == c2.equals(c1)")
        void shouldBeSymmetric() {
            Customer c1 = createCustomer("test@mail.com");
            Customer c2 = createCustomer("test@mail.com");

            assertEquals(c1, c2);
            assertEquals(c2, c1);
        }

        @Test
        @DisplayName("null → not equal")
        void null_shouldNotBeEqual() {
            Customer c1 = createCustomer("test@mail.com");

            assertNotEquals(null, c1);
        }

        @Test
        @DisplayName("different type → not equal")
        void differentType_shouldNotBeEqual() {
            Customer c1 = createCustomer("test@mail.com");

            assertNotEquals("a string", c1);
        }

        @Test
        @DisplayName("different fields but same email → still equal")
        void differentFieldsSameEmail_shouldBeEqual() {
            Customer c1 = new Customer("Alice", "A", "same@mail.com", "NY", "pass1");
            Customer c2 = new Customer("Bob", "B", "same@mail.com", "LA", "pass2");

            assertEquals(c1, c2);
        }
    }

    // ==================== hashCode() ====================

    @Nested
    @DisplayName("hashCode()")
    class HashCode {

        @Test
        @DisplayName("same email → same hashCode")
        void sameEmail_shouldHaveSameHashCode() {
            Customer c1 = createCustomer("test@mail.com");
            Customer c2 = createCustomer("test@mail.com");

            assertEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        @DisplayName("different email → different hashCode (not guaranteed, but expected)")
        void differentEmail_shouldHaveDifferentHashCode() {
            Customer c1 = createCustomer("a@mail.com");
            Customer c2 = createCustomer("b@mail.com");

            // This is not strictly required by the hashCode contract,
            // but for distinct emails it should be different in practice
            assertNotEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        @DisplayName("consistent: multiple calls return same value")
        void shouldBeConsistent() {
            Customer c1 = createCustomer("test@mail.com");

            int hash1 = c1.hashCode();
            int hash2 = c1.hashCode();

            assertEquals(hash1, hash2);
        }
    }
}
