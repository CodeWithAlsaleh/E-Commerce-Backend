package com.sivan.ecommerce.entity.product;

import com.sivan.ecommerce.entity.EntityTestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/*
 *  Strategy 3: Primary Key (UUID) Identity — Product uses getId()
 */
@DisplayName("Product equals() & hashCode()")
class ProductEqualsHashCodeTest {

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
        @DisplayName("same ID → equal")
        void sameId_shouldBeEqual() {
            UUID id = UUID.randomUUID();

            Product p1 = createProductWithId(id);
            Product p2 = createProductWithId(id);

            assertEquals(p1, p2);
        }

        @Test
        @DisplayName("different ID → not equal")
        void differentId_shouldNotBeEqual() {
            Product p1 = createProductWithId(UUID.randomUUID());
            Product p2 = createProductWithId(UUID.randomUUID());

            assertNotEquals(p1, p2);
        }

        @Test
        @DisplayName("same instance → equal (reflexive)")
        void sameInstance_shouldBeEqual() {
            Product p1 = createProductWithId(UUID.randomUUID());

            assertEquals(p1, p1);
        }

        @Test
        @DisplayName("symmetric: p1.equals(p2) == p2.equals(p1)")
        void shouldBeSymmetric() {
            UUID id = UUID.randomUUID();

            Product p1 = createProductWithId(id);
            Product p2 = createProductWithId(id);

            assertEquals(p1, p2);
            assertEquals(p2, p1);
        }

        @Test
        @DisplayName("null → not equal")
        void null_shouldNotBeEqual() {
            Product p1 = createProductWithId(UUID.randomUUID());

            assertNotEquals(null, p1);
        }

        @Test
        @DisplayName("different type → not equal")
        void differentType_shouldNotBeEqual() {
            Product p1 = createProductWithId(UUID.randomUUID());

            assertNotEquals("a string", p1);
        }

        @Test
        @DisplayName("null ID → not equal to another with null ID")
        void nullId_shouldNotBeEqual() {
            Product p1 = new Product("A", "Desc", 1, 100L, "USD", "http://a.png");
            Product p2 = new Product("B", "Desc", 2, 200L, "USD", "http://b.png");
            // IDs are null (not yet persisted / no reflection set)

            assertNotEquals(p1, p2);
        }

        @Test
        @DisplayName("null ID → not equal to one with ID")
        void nullIdVsSetId_shouldNotBeEqual() {
            Product p1 = new Product("A", "Desc", 1, 100L, "USD", "http://a.png");
            Product p2 = createProductWithId(UUID.randomUUID());

            assertNotEquals(p1, p2);
        }

        @Test
        @DisplayName("same ID but different fields → still equal (identity is ID-based)")
        void sameIdDifferentFields_shouldBeEqual() {
            UUID id = UUID.randomUUID();

            Product p1 = createProductWithId(id);
            p1.setTitle("Title A");
            p1.setPrice(100L);

            Product p2 = createProductWithId(id);
            p2.setTitle("Title B");
            p2.setPrice(999L);

            assertEquals(p1, p2);
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

            Product p1 = createProductWithId(id);
            Product p2 = createProductWithId(id);

            assertEquals(p1.hashCode(), p2.hashCode());
        }

        @Test
        @DisplayName("different ID → different hashCode")
        void differentId_shouldHaveDifferentHashCode() {
            Product p1 = createProductWithId(UUID.randomUUID());
            Product p2 = createProductWithId(UUID.randomUUID());

            assertNotEquals(p1.hashCode(), p2.hashCode());
        }

        @Test
        @DisplayName("consistent: multiple calls return same value")
        void shouldBeConsistent() {
            Product p1 = createProductWithId(UUID.randomUUID());

            int hash1 = p1.hashCode();
            int hash2 = p1.hashCode();

            assertEquals(hash1, hash2);
        }
    }
}
