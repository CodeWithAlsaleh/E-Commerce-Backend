package com.sivan.ecommerce.entity;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

/*
 *  Utility class for entity unit tests.
 *
 *  Since BaseEntity.setId() is private and @UuidGenerator only assigns
 *  IDs within a Hibernate persistence context, we use reflection to
 *  set entity IDs in pure unit tests (no Spring context needed).
 */
public final class EntityTestUtil {

    private EntityTestUtil() {
    }

    public static void setId(BaseEntity entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
