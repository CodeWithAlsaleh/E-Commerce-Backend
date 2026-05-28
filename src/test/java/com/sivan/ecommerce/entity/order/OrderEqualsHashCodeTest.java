package com.sivan.ecommerce.entity.order;

import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.customer.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/*
 *  Strategy 3: Primary Key (UUID) Identity — Order uses getId()
 */
@DisplayName("Order equals() & hashCode()")
class OrderEqualsHashCodeTest {

    private Order createOrderWithId(UUID id) {
        Customer customer = new Customer("F", "L", "e@m.com", null, "p");
        Order order = new Order(customer, Status.PENDING, 5000L, "123 Street");
        EntityTestUtil.setId(order, id);
        return order;
    }

    // ==================== equals() ====================

    @Nested
    @DisplayName("equals()")
    class Equals {

        @Test
        @DisplayName("same ID → equal")
        void sameId_shouldBeEqual() {
            UUID id = UUID.randomUUID();

            Order o1 = createOrderWithId(id);
            Order o2 = createOrderWithId(id);

            assertEquals(o1, o2);
        }

        @Test
        @DisplayName("different ID → not equal")
        void differentId_shouldNotBeEqual() {
            Order o1 = createOrderWithId(UUID.randomUUID());
            Order o2 = createOrderWithId(UUID.randomUUID());

            assertNotEquals(o1, o2);
        }

        @Test
        @DisplayName("same instance → equal (reflexive)")
        void sameInstance_shouldBeEqual() {
            Order o1 = createOrderWithId(UUID.randomUUID());

            assertEquals(o1, o1);
        }

        @Test
        @DisplayName("symmetric: o1.equals(o2) == o2.equals(o1)")
        void shouldBeSymmetric() {
            UUID id = UUID.randomUUID();

            Order o1 = createOrderWithId(id);
            Order o2 = createOrderWithId(id);

            assertEquals(o1, o2);
            assertEquals(o2, o1);
        }

        @Test
        @DisplayName("null → not equal")
        void null_shouldNotBeEqual() {
            Order o1 = createOrderWithId(UUID.randomUUID());

            assertNotEquals(null, o1);
        }

        @Test
        @DisplayName("different type → not equal")
        void differentType_shouldNotBeEqual() {
            Order o1 = createOrderWithId(UUID.randomUUID());

            assertNotEquals("a string", o1);
        }

        @Test
        @DisplayName("two orders from the same customer with different IDs → NOT equal")
        void sameCustomerDifferentId_shouldNotBeEqual() {
            // This was the bug we caught in the first review!
            Customer sharedCustomer = new Customer("F", "L", "same@mail.com", null, "p");

            Order o1 = new Order(sharedCustomer, Status.PENDING, 1000L, "Address A");
            EntityTestUtil.setId(o1, UUID.randomUUID());

            Order o2 = new Order(sharedCustomer, Status.SHIPPED, 2000L, "Address B");
            EntityTestUtil.setId(o2, UUID.randomUUID());

            assertNotEquals(o1, o2, "Orders from the same customer must NOT be equal if they have different IDs");
        }

        @Test
        @DisplayName("null ID → not equal to another with null ID")
        void nullId_shouldNotBeEqual() {
            Customer sharedCustomer = new Customer("F", "L", "same@mail.com", null, "p");

            Order o1 = new Order(sharedCustomer, Status.PENDING, 1000L, "Address A");
            Order o2 = new Order(sharedCustomer, Status.SHIPPED, 2000L, "Address B");
            // IDs are null (not yet persisted / no reflection set)

            assertNotEquals(o1, o2);
        }
    }

    // ==================== hashCode() ====================

    @Nested
    @DisplayName("hashCode()")
    class HashCode {

        @Test
        @DisplayName("same ID → same hashCode")
        void sameId_shouldHaveSameHashCode() {
            UUID id = UUID.randomUUID();

            Order o1 = createOrderWithId(id);
            Order o2 = createOrderWithId(id);

            assertEquals(o1.hashCode(), o2.hashCode());
        }

        @Test
        @DisplayName("different ID → different hashCode")
        void differentId_shouldHaveDifferentHashCode() {
            Order o1 = createOrderWithId(UUID.randomUUID());
            Order o2 = createOrderWithId(UUID.randomUUID());

            assertNotEquals(o1.hashCode(), o2.hashCode());
        }

        @Test
        @DisplayName("two orders from same customer → different hashCode")
        void sameCustomerDifferentId_shouldHaveDifferentHashCode() {
            Customer sharedCustomer = new Customer("F", "L", "same@mail.com", null, "p");

            Order o1 = new Order(sharedCustomer, Status.PENDING, 1000L, "Addr A");
            EntityTestUtil.setId(o1, UUID.randomUUID());

            Order o2 = new Order(sharedCustomer, Status.DELIVERED, 2000L, "Addr B");
            EntityTestUtil.setId(o2, UUID.randomUUID());

            assertNotEquals(o1.hashCode(), o2.hashCode());
        }

        @Test
        @DisplayName("consistent: multiple calls return same value")
        void shouldBeConsistent() {
            UUID id = UUID.randomUUID();

            Order o1 = createOrderWithId(id);

            int hash1 = o1.hashCode();
            int hash2 = o1.hashCode();

            assertEquals(hash1, hash2);
        }
    }
}
