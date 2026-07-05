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

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.data.domain.Sort;

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

    // ======================== getCategories() ========================

    @Nested
    @DisplayName("getCategories()")
    class GetCategories {

        // ======================== Helper Methods ========================

        /**
         * Creates a {@link Category} with the given fields and a reflectively-set ID.
         */
        private Category getCategory(UUID id, String title, String description, boolean isActive) {
            Category category = new Category(title, description);
            category.setActive(isActive);
            EntityTestUtil.setId(category, id);
            return category;
        }

        // ==================== SUCCESS CASES ====================

        @Nested
        @DisplayName("Success cases")
        class SuccessCases {

            @Test
            @DisplayName("Should return a list of CategoryResponseDTOs when categories exist")
            void shouldReturnListOfCategoryResponseDTOs_whenCategoriesExist() {
                // Arrange
                UUID id1 = UUID.randomUUID();
                UUID id2 = UUID.randomUUID();
                UUID id3 = UUID.randomUUID();

                List<Category> categories = List.of(
                        getCategory(id1, "accessories", "Phone cases and chargers", true),
                        getCategory(id2, "electronics", "All electronic items", true),
                        getCategory(id3, "furniture", "Home and office furniture", true)
                );

                when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title")))
                        .thenReturn(categories);

                // Act
                List<CategoryResponseDTO> result = categoryService.getCategories();

                // Assert
                assertNotNull(result);
                assertEquals(3, result.size());

                assertEquals(id1, result.getFirst().id());
                assertEquals("accessories", result.getFirst().title());
                assertEquals("Phone cases and chargers", result.getFirst().description());
                assertTrue(result.getFirst().isActive());

                assertEquals(id2, result.get(1).id());
                assertEquals("electronics", result.get(1).title());
                assertEquals("All electronic items", result.get(1).description());
                assertTrue(result.get(1).isActive());

                assertEquals(id3, result.get(2).id());
                assertEquals("furniture", result.get(2).title());
                assertEquals("Home and office furniture", result.get(2).description());
                assertTrue(result.get(2).isActive());
            }

            @Test
            @DisplayName("Should return an empty list when no categories exist")
            void shouldReturnEmptyList_whenNoCategoriesExist() {
                // Arrange
                when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title")))
                        .thenReturn(Collections.emptyList());

                // Act
                List<CategoryResponseDTO> result = categoryService.getCategories();

                // Assert
                assertNotNull(result);
                assertTrue(result.isEmpty());
            }

            @Test
            @DisplayName("Should return a single-element list when only one category exists")
            void shouldReturnSingleElementList_whenOneCategoryExists() {
                // Arrange
                UUID id = UUID.randomUUID();
                Category category = getCategory(id, "electronics", "Gadgets and devices", true);

                when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title")))
                        .thenReturn(List.of(category));

                // Act
                List<CategoryResponseDTO> result = categoryService.getCategories();

                // Assert
                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals(id, result.getFirst().id());
                assertEquals("electronics", result.getFirst().title());
                assertEquals("Gadgets and devices", result.getFirst().description());
                assertTrue(result.getFirst().isActive());
            }

            @Test
            @DisplayName("Should call repository.findAll with ascending sort by title")
            void shouldCallFindAllWithAscendingSortByTitle() {
                // Arrange
                when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title")))
                        .thenReturn(Collections.emptyList());

                // Act
                categoryService.getCategories();

                // Assert
                verify(categoryRepository).findAll(Sort.by(Sort.Direction.ASC, "title"));
                verifyNoMoreInteractions(categoryRepository);
            }
        }

        // ==================== MAPPER INTERACTION VERIFICATION ====================

        @Nested
        @DisplayName("Mapper integration verification")
        class MapperVerification {

            @Test
            @DisplayName("Should correctly map each Category entity field to CategoryResponseDTO")
            void shouldMapEachEntityFieldToResponseDTO() {
                // Arrange
                UUID id = UUID.randomUUID();
                Category category = getCategory(id, "electronics", "Gadgets and devices", true);

                when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title")))
                        .thenReturn(List.of(category));

                // Act
                List<CategoryResponseDTO> result = categoryService.getCategories();

                // Assert — verify every field was mapped correctly
                CategoryResponseDTO dto = result.getFirst();
                assertEquals(id, dto.id());
                assertEquals(category.getTitle(), dto.title());
                assertEquals(category.getDescription(), dto.description());
                assertEquals(category.isActive(), dto.isActive());
            }

            @Test
            @DisplayName("Should preserve the order returned by repository in the response list")
            void shouldPreserveRepositoryOrder() {
                // Arrange
                UUID id1 = UUID.randomUUID();
                UUID id2 = UUID.randomUUID();

                List<Category> categories = List.of(
                        getCategory(id1, "accessories", "Accessories desc", true),
                        getCategory(id2, "electronics", "Electronics desc", true)
                );

                when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title")))
                        .thenReturn(categories);

                // Act
                List<CategoryResponseDTO> result = categoryService.getCategories();

                // Assert — order must match repository output
                assertEquals(id1, result.getFirst().id());
                assertEquals(id2, result.get(1).id());
            }
        }

        // ==================== REPOSITORY / SERVER FAILURE CASES ====================

        @Nested
        @DisplayName("Repository failure simulation")
        class RepositoryFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when repository throws on findAll")
            void shouldPropagateRuntimeException_whenRepositoryThrows() {
                // Arrange
                when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title")))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> categoryService.getCategories()
                );
                assertEquals("Database connection lost", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate IllegalStateException from repository")
            void shouldPropagateIllegalStateException_whenRepositoryFails() {
                // Arrange
                when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title")))
                        .thenThrow(new IllegalStateException("Unexpected persistence error"));

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> categoryService.getCategories()
                );
                assertEquals("Unexpected persistence error", exception.getMessage());
            }
        }

        // ==================== EDGE CASES ====================

        @Nested
        @DisplayName("Edge cases")
        class EdgeCases {

            @Test
            @DisplayName("Should handle categories with null descriptions")
            void shouldHandleCategoriesWithNullDescriptions() {
                // Arrange
                UUID id = UUID.randomUUID();
                Category category = getCategory(id, "electronics", null, true);

                when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title")))
                        .thenReturn(List.of(category));

                // Act
                List<CategoryResponseDTO> result = categoryService.getCategories();

                // Assert
                assertNotNull(result);
                assertEquals(1, result.size());
                assertNull(result.getFirst().description());
            }

            @Test
            @DisplayName("Should handle categories with special characters and Unicode in title and description")
            void shouldHandleSpecialCharactersAndUnicode() {
                // Arrange
                UUID id = UUID.randomUUID();
                String specialTitle = "laptops & pcs™";
                String specialDescription = "Features: résumé-ready, naïve AI, 日本語サポート & more! @#$%^&*()";
                Category category = getCategory(id, specialTitle, specialDescription, true);

                when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title")))
                        .thenReturn(List.of(category));

                // Act
                List<CategoryResponseDTO> result = categoryService.getCategories();

                // Assert
                assertNotNull(result);
                assertEquals(specialTitle, result.getFirst().title());
                assertEquals(specialDescription, result.getFirst().description());
            }

            @Test
            @DisplayName("Should include inactive categories in the result (no filtering by isActive)")
            void shouldIncludeInactiveCategories() {
                // Arrange
                UUID activeId = UUID.randomUUID();
                UUID inactiveId = UUID.randomUUID();

                List<Category> categories = List.of(
                        getCategory(activeId, "active category", "Active desc", true),
                        getCategory(inactiveId, "inactive category", "Inactive desc", false)
                );

                when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title")))
                        .thenReturn(categories);

                // Act
                List<CategoryResponseDTO> result = categoryService.getCategories();

                // Assert
                assertEquals(2, result.size());
                assertTrue(result.getFirst().isActive());
                assertFalse(result.get(1).isActive());
            }

            @Test
            @DisplayName("Should handle a large number of categories")
            void shouldHandleLargeNumberOfCategories() {
                // Arrange
                List<Category> categories = IntStream.rangeClosed(1, 100)
                        .mapToObj(i -> getCategory(UUID.randomUUID(), "category-" + i, "Description " + i, true))
                        .toList();

                when(categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title")))
                        .thenReturn(categories);

                // Act
                List<CategoryResponseDTO> result = categoryService.getCategories();

                // Assert
                assertNotNull(result);
                assertEquals(100, result.size());
            }
        }
    }
}
