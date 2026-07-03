package com.sivan.ecommerce.service.category;

import com.sivan.ecommerce.dto.category.CategoryRequestDTO;
import com.sivan.ecommerce.dto.category.CategoryResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.category.Category;
import com.sivan.ecommerce.exception.CategoryAlreadyExistsException;
import com.sivan.ecommerce.repository.category.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CategoryServiceImpl}.
 * Only the service layer is tested here — the repository is mocked.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryServiceImpl — Unit Tests")
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    // ======================== Shared Helper Constants ========================

    private static final String VALID_TITLE = "Electronics";
    private static final String VALID_DESCRIPTION = "All electronic items including phones, laptops, and accessories.";

    // ======================== createCategory() ========================

    @Nested
    @DisplayName("createCategory()")
    class CreateCategory {

        // ======================== Helper Methods ========================

        private CategoryRequestDTO validRequest() {
            return new CategoryRequestDTO(VALID_TITLE, VALID_DESCRIPTION);
        }

        private CategoryRequestDTO requestWithNullDescription() {
            return new CategoryRequestDTO(VALID_TITLE, null);
        }

        private void stubRepositorySave(UUID id) {
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
                Category categoryToSave = inv.getArgument(0);
                EntityTestUtil.setId(categoryToSave, id);
                return categoryToSave;
            });
        }

        // ==================== SUCCESS CASES ====================

        @Nested
        @DisplayName("Success cases")
        class SuccessCases {

            @Test
            @DisplayName("Should return a valid CategoryResponseDTO when request is valid with description")
            void shouldReturnCategoryResponseDTO_whenRequestIsValid() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                when(categoryRepository.existsByTitle(anyString())).thenReturn(false);
                stubRepositorySave(expectedId);

                // Act
                CategoryResponseDTO response = categoryService.createCategory(validRequest());

                // Assert
                assertNotNull(response);
                assertEquals(expectedId, response.id());
                assertEquals(VALID_TITLE.toLowerCase(), response.title());
                assertEquals(VALID_DESCRIPTION, response.description());
                assertTrue(response.isActive());
            }

            @Test
            @DisplayName("Should call repository save exactly once")
            void shouldCallRepositorySaveExactlyOnce() {
                // Arrange
                when(categoryRepository.existsByTitle(anyString())).thenReturn(false);
                stubRepositorySave(UUID.randomUUID());

                // Act
                categoryService.createCategory(validRequest());

                // Assert
                verify(categoryRepository).save(any(Category.class));
            }

            @Test
            @DisplayName("Should succeed when description is null (optional field)")
            void shouldSucceed_whenDescriptionIsNull() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                Category savedCategory = new Category(VALID_TITLE, null);
                EntityTestUtil.setId(savedCategory, expectedId);

                when(categoryRepository.existsByTitle(anyString())).thenReturn(false);
                when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

                // Act
                CategoryResponseDTO response = categoryService.createCategory(requestWithNullDescription());

                // Assert
                assertNotNull(response);
                assertEquals(expectedId, response.id());
                assertNull(response.description());
                verify(categoryRepository).save(any(Category.class));
            }

            @Test
            @DisplayName("Should handle title whitespace trimming and lowercasing correctly (from DTO)")
            void shouldHandleTitleWhitespaceAndCase() {
                // Arrange
                CategoryRequestDTO request = new CategoryRequestDTO("  Electronics  ", VALID_DESCRIPTION);

                UUID expectedId = UUID.randomUUID();
                Category savedCategory = new Category("electronics", VALID_DESCRIPTION);
                EntityTestUtil.setId(savedCategory, expectedId);

                when(categoryRepository.existsByTitle("electronics")).thenReturn(false);
                when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

                // Act
                CategoryResponseDTO response = categoryService.createCategory(request);

                // Assert
                assertEquals("electronics", response.title());
            }
        }

        // ==================== CONFLICT CASES ====================

        @Nested
        @DisplayName("Conflict cases")
        class ConflictCases {

            @Test
            @DisplayName("Should throw CategoryAlreadyExistsException when title already exists")
            void shouldThrowCategoryAlreadyExistsException_whenTitleAlreadyExists() {
                // Arrange
                when(categoryRepository.existsByTitle(anyString())).thenReturn(true);

                // Act & Assert
                CategoryAlreadyExistsException exception = assertThrows(
                        CategoryAlreadyExistsException.class,
                        () -> categoryService.createCategory(validRequest())
                );

                assertEquals("Category already exists", exception.getMessage());
                verify(categoryRepository, never()).save(any(Category.class));
            }
        }

        // ==================== REPOSITORY / SERVER FAILURE CASES ====================

        @Nested
        @DisplayName("Repository failure simulation")
        class RepositoryFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when repository throws on save")
            void shouldPropagateRuntimeException_whenRepositoryThrows() {
                // Arrange
                when(categoryRepository.existsByTitle(anyString())).thenReturn(false);
                when(categoryRepository.save(any(Category.class)))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> categoryService.createCategory(validRequest())
                );
                assertEquals("Database connection lost", exception.getMessage());
                verify(categoryRepository).save(any(Category.class));
            }

            @Test
            @DisplayName("Should propagate IllegalStateException error from repository")
            void shouldPropagateException_whenRepositorySaveFailsUnexpectedly() {
                // Arrange
                when(categoryRepository.existsByTitle(anyString())).thenReturn(false);
                when(categoryRepository.save(any(Category.class)))
                        .thenThrow(new IllegalStateException("Unexpected persistence error"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> categoryService.createCategory(validRequest())
                );
                assertEquals("Unexpected persistence error", exception.getMessage());
            }
        }

        // ==================== EDGE CASES ====================

        @Nested
        @DisplayName("Edge cases")
        class EdgeCases {

            @Test
            @DisplayName("Should handle a very long valid description (5000 characters)")
            void shouldHandleVeryLongDescription() {
                // Arrange
                String longDescription = "X".repeat(5000);
                CategoryRequestDTO request = new CategoryRequestDTO(VALID_TITLE, longDescription);

                UUID expectedId = UUID.randomUUID();
                Category savedCategory = new Category(VALID_TITLE, longDescription);
                EntityTestUtil.setId(savedCategory, expectedId);

                when(categoryRepository.existsByTitle(anyString())).thenReturn(false);
                when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

                // Act
                CategoryResponseDTO response = categoryService.createCategory(request);

                // Assert
                assertNotNull(response);
                assertEquals(longDescription, response.description());
            }

            @Test
            @DisplayName("Should handle special characters in title and description")
            void shouldHandleSpecialCharacters() {
                // Arrange
                String specialTitle = "laptops & pcs™";
                String specialDescription = "Features: résumé-ready, naïve AI, 日本語サポート & more! @#$%^&*()";
                CategoryRequestDTO request = new CategoryRequestDTO(specialTitle, specialDescription);

                UUID expectedId = UUID.randomUUID();
                Category savedCategory = new Category(specialTitle, specialDescription);
                EntityTestUtil.setId(savedCategory, expectedId);

                when(categoryRepository.existsByTitle(anyString())).thenReturn(false);
                when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

                // Act
                CategoryResponseDTO response = categoryService.createCategory(request);

                // Assert
                assertNotNull(response);
                assertEquals(specialTitle, response.title());
                assertEquals(specialDescription, response.description());
            }
        }

        // ==================== MAPPER INTERACTION VERIFICATION ====================

        @Nested
        @DisplayName("Mapper integration verification")
        class MapperVerification {

            @Test
            @DisplayName("Should pass the correctly mapped Category entity to repository.save()")
            void shouldPassMappedCategoryToRepository() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                Category savedCategory = new Category(VALID_TITLE, VALID_DESCRIPTION);
                EntityTestUtil.setId(savedCategory, expectedId);

                when(categoryRepository.existsByTitle(anyString())).thenReturn(false);
                when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
                    Category captured = inv.getArgument(0);

                    // Verify mapper mapped correctly
                    assertEquals(VALID_TITLE.toLowerCase(), captured.getTitle());
                    assertEquals(VALID_DESCRIPTION, captured.getDescription());
                    assertTrue(captured.isActive()); // Default should be true

                    return savedCategory;
                });

                // Act
                categoryService.createCategory(validRequest());

                // Assert
                verify(categoryRepository).save(any(Category.class));
            }
        }
    }
}
