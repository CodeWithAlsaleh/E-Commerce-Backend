package com.sivan.ecommerce.entity.category;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 *  Strategy 1: Natural Business Key — Category uses 'title'
 */
@DisplayName("Category equals() & hashCode()")
class CategoryEqualsHashCodeTest {

    private Category createCategory(String title) {
        return new Category(title, "some description");
    }

    // ==================== equals() ====================

    @Nested
    @DisplayName("equals()")
    class Equals {

        @Test
        @DisplayName("same title → equal")
        void sameTitle_shouldBeEqual() {
            Category c1 = createCategory("Electronics");
            Category c2 = createCategory("Electronics");

            assertEquals(c1, c2);
        }

        @Test
        @DisplayName("different title → not equal")
        void differentTitle_shouldNotBeEqual() {
            Category c1 = createCategory("Electronics");
            Category c2 = createCategory("Clothing");

            assertNotEquals(c1, c2);
        }

        @Test
        @DisplayName("same instance → equal (reflexive)")
        void sameInstance_shouldBeEqual() {
            Category c1 = createCategory("Electronics");

            assertEquals(c1, c1);
        }

        @Test
        @DisplayName("symmetric: c1.equals(c2) == c2.equals(c1)")
        void shouldBeSymmetric() {
            Category c1 = createCategory("Electronics");
            Category c2 = createCategory("Electronics");

            assertEquals(c1, c2);
            assertEquals(c2, c1);
        }

        @Test
        @DisplayName("null → not equal")
        void null_shouldNotBeEqual() {
            Category c1 = createCategory("Electronics");

            assertNotEquals(null, c1);
        }

        @Test
        @DisplayName("different type → not equal")
        void differentType_shouldNotBeEqual() {
            Category c1 = createCategory("Electronics");

            assertNotEquals("a string", c1);
        }

        @Test
        @DisplayName("different description but same title → still equal")
        void differentDescriptionSameTitle_shouldBeEqual() {
            Category c1 = new Category("Electronics", "Desc A");
            Category c2 = new Category("Electronics", "Desc B");

            assertEquals(c1, c2);
        }
    }

    // ==================== hashCode() ====================

    @Nested
    @DisplayName("hashCode()")
    class HashCode {

        @Test
        @DisplayName("same title → same hashCode")
        void sameTitle_shouldHaveSameHashCode() {
            Category c1 = createCategory("Electronics");
            Category c2 = createCategory("Electronics");

            assertEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        @DisplayName("different title → different hashCode")
        void differentTitle_shouldHaveDifferentHashCode() {
            Category c1 = createCategory("Electronics");
            Category c2 = createCategory("Clothing");

            assertNotEquals(c1.hashCode(), c2.hashCode());
        }

        @Test
        @DisplayName("consistent: multiple calls return same value")
        void shouldBeConsistent() {
            Category c1 = createCategory("Electronics");

            int hash1 = c1.hashCode();
            int hash2 = c1.hashCode();

            assertEquals(hash1, hash2);
        }
    }
}
