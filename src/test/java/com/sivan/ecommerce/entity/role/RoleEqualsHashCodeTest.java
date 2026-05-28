package com.sivan.ecommerce.entity.role;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 *  Strategy 1: Natural Business Key — Role uses 'roleName' enum
 */
@DisplayName("Role equals() & hashCode()")
class RoleEqualsHashCodeTest {

    private Role createRole(RoleName roleName) {
        return new Role(roleName);
    }

    // ==================== equals() ====================

    @Nested
    @DisplayName("equals()")
    class Equals {

        @Test
        @DisplayName("same roleName → equal")
        void sameRoleName_shouldBeEqual() {
            Role r1 = createRole(RoleName.ROLE_USER);
            Role r2 = createRole(RoleName.ROLE_USER);

            assertEquals(r1, r2);
        }

        @Test
        @DisplayName("different roleName → not equal")
        void differentRoleName_shouldNotBeEqual() {
            Role r1 = createRole(RoleName.ROLE_USER);
            Role r2 = createRole(RoleName.ROLE_ADMIN);

            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("same instance → equal (reflexive)")
        void sameInstance_shouldBeEqual() {
            Role r1 = createRole(RoleName.ROLE_ADMIN);

            assertEquals(r1, r1);
        }

        @Test
        @DisplayName("symmetric: r1.equals(r2) == r2.equals(r1)")
        void shouldBeSymmetric() {
            Role r1 = createRole(RoleName.ROLE_USER);
            Role r2 = createRole(RoleName.ROLE_USER);

            assertEquals(r1, r2);
            assertEquals(r2, r1);
        }

        @Test
        @DisplayName("null → not equal")
        void null_shouldNotBeEqual() {
            Role r1 = createRole(RoleName.ROLE_USER);

            assertNotEquals(null, r1);
        }

        @Test
        @DisplayName("different type → not equal")
        void differentType_shouldNotBeEqual() {
            Role r1 = createRole(RoleName.ROLE_USER);

            assertNotEquals("a string", r1);
        }
    }

    // ==================== hashCode() ====================

    @Nested
    @DisplayName("hashCode()")
    class HashCode {

        @Test
        @DisplayName("same roleName → same hashCode")
        void sameRoleName_shouldHaveSameHashCode() {
            Role r1 = createRole(RoleName.ROLE_USER);
            Role r2 = createRole(RoleName.ROLE_USER);

            assertEquals(r1.hashCode(), r2.hashCode());
        }

        @Test
        @DisplayName("different roleName → different hashCode")
        void differentRoleName_shouldHaveDifferentHashCode() {
            Role r1 = createRole(RoleName.ROLE_USER);
            Role r2 = createRole(RoleName.ROLE_ADMIN);

            assertNotEquals(r1.hashCode(), r2.hashCode());
        }

        @Test
        @DisplayName("consistent: multiple calls return same value")
        void shouldBeConsistent() {
            Role r1 = createRole(RoleName.ROLE_ADMIN);

            int hash1 = r1.hashCode();
            int hash2 = r1.hashCode();

            assertEquals(hash1, hash2);
        }
    }
}
