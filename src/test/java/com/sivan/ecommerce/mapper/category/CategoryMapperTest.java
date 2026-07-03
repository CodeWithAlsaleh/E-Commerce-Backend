package com.sivan.ecommerce.mapper.category;

import com.sivan.ecommerce.dto.category.CategoryRequestDTO;
import com.sivan.ecommerce.dto.category.CategoryResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.category.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CategoryMapper}.
 * Tests both mapping directions: DTO → Entity and Entity → DTO.
 * No Spring context is loaded — pure unit tests.
 */
@DisplayName("CategoryMapper — mapping methods")
class CategoryMapperTest {

    // ======================== Constants ========================

    private static final String VALID_TITLE = "Electronics";
    private static final String VALID_DESCRIPTION = "All kinds of electronic devices and gadgets.";

    // ==================== mapCategoryRequestToCategory() ====================

    @Nested
    @DisplayName("mapCategoryRequestToCategory()")
    class MapRequestToEntity {

        @Test
        @DisplayName("Should map all fields correctly from DTO to entity")
        void shouldMapAllFieldsCorrectly() {
            // Arrange
            CategoryRequestDTO request = new CategoryRequestDTO(VALID_TITLE, VALID_DESCRIPTION);

            // Act
            Category category = CategoryMapper.mapCategoryRequestToCategory(request);

            // Assert
            assertNotNull(category);
            assertEquals(VALID_TITLE.toLowerCase(), category.getTitle());
            assertEquals(VALID_DESCRIPTION, category.getDescription());
        }

        @Test
        @DisplayName("Should handle null description (optional field)")
        void shouldHandleNullDescription() {
            // Arrange
            CategoryRequestDTO request = new CategoryRequestDTO(VALID_TITLE, null);

            // Act
            Category category = CategoryMapper.mapCategoryRequestToCategory(request);

            // Assert
            assertNotNull(category);
            assertEquals(VALID_TITLE.toLowerCase(), category.getTitle());
            assertNull(category.getDescription());
        }

        @Test
        @DisplayName("Should handle special characters in title and description")
        void shouldHandleSpecialCharacters() {
            // Arrange
            CategoryRequestDTO request = new CategoryRequestDTO("Phones & Tablets!", "100% genuine products @ best prices.");

            // Act
            Category category = CategoryMapper.mapCategoryRequestToCategory(request);

            // Assert
            assertNotNull(category);
            assertEquals("Phones & Tablets!".toLowerCase(), category.getTitle());
            assertEquals("100% genuine products @ best prices.", category.getDescription());
        }

        @Test
        @DisplayName("Should handle Unicode characters in text fields")
        void shouldHandleUnicodeCharacters() {
            // Arrange
            CategoryRequestDTO request = new CategoryRequestDTO("エレクトロニクス", "各種電子機器");

            // Act
            Category category = CategoryMapper.mapCategoryRequestToCategory(request);

            // Assert
            assertNotNull(category);
            assertEquals("エレクトロニクス", category.getTitle());
            assertEquals("各種電子機器", category.getDescription());
        }

        @Test
        @DisplayName("Should not set ID on mapped entity (ID is assigned by Hibernate)")
        void shouldNotSetIdOnMappedEntity() {
            // Arrange
            CategoryRequestDTO request = new CategoryRequestDTO(VALID_TITLE, VALID_DESCRIPTION);

            // Act
            Category category = CategoryMapper.mapCategoryRequestToCategory(request);

            // Assert
            assertNotNull(category);
            assertNull(category.getId());
        }
    }

    // ==================== mapCategoryToCategoryResponse() ====================

    @Nested
    @DisplayName("mapCategoryToCategoryResponse()")
    class MapEntityToResponse {

        @Test
        @DisplayName("Should map all fields correctly from entity to response DTO")
        void shouldMapAllFieldsCorrectly() {
            // Arrange
            UUID expectedId = UUID.randomUUID();
            Category category = new Category(VALID_TITLE, VALID_DESCRIPTION);
            EntityTestUtil.setId(category, expectedId);

            // Act
            CategoryResponseDTO response = CategoryMapper.mapCategoryToCategoryResponse(category);

            // Assert
            assertNotNull(response);
            assertEquals(expectedId, response.id());
            assertEquals(VALID_TITLE, response.title());
            assertEquals(VALID_DESCRIPTION, response.description());
            assertEquals(category.isActive(), response.isActive());
        }

        @Test
        @DisplayName("Should map entity with null ID (pre-persist state)")
        void shouldMapEntityWithNullId() {
            // Arrange
            Category category = new Category(VALID_TITLE, VALID_DESCRIPTION);
            // ID is null — entity not yet persisted

            // Act
            CategoryResponseDTO response = CategoryMapper.mapCategoryToCategoryResponse(category);

            // Assert
            assertNotNull(response);
            assertNull(response.id());
            assertEquals(VALID_TITLE, response.title());
        }

        @Test
        @DisplayName("Should map null description correctly")
        void shouldMapNullDescription() {
            // Arrange
            UUID expectedId = UUID.randomUUID();
            Category category = new Category(VALID_TITLE, null);
            EntityTestUtil.setId(category, expectedId);

            // Act
            CategoryResponseDTO response = CategoryMapper.mapCategoryToCategoryResponse(category);

            // Assert
            assertNotNull(response);
            assertNull(response.description());
            assertEquals(VALID_TITLE, response.title());
        }

        @Test
        @DisplayName("Should map isActive correctly when false")
        void shouldMapIsActiveCorrectlyWhenFalse() {
            // Arrange
            UUID expectedId = UUID.randomUUID();
            Category category = new Category(VALID_TITLE, VALID_DESCRIPTION);
            category.setActive(false);
            EntityTestUtil.setId(category, expectedId);

            // Act
            CategoryResponseDTO response = CategoryMapper.mapCategoryToCategoryResponse(category);

            // Assert
            assertNotNull(response);
            assertFalse(response.isActive());
        }
    }
}
