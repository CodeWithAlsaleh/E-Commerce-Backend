package com.sivan.ecommerce.controller.category;

import tools.jackson.databind.ObjectMapper;
import com.sivan.ecommerce.config.SecurityConfig;
import com.sivan.ecommerce.dto.category.CategoryRequestDTO;
import com.sivan.ecommerce.dto.category.CategoryResponseDTO;
import com.sivan.ecommerce.exception.CategoryAlreadyExistsException;
import com.sivan.ecommerce.service.category.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryRestController.class)
@Import(SecurityConfig.class)
@DisplayName("CategoryRestController")
class CategoryRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    // ======================== Constants ========================

    private static final String CATEGORIES_URL = "/categories";

    private static final String VALID_TITLE = "electronics";
    private static final String VALID_DESCRIPTION =
            "All kinds of electronic devices like phones, laptops, and tablets."; // 66 chars

    // ==================== createCategory() ====================
    @Nested
    @DisplayName("createCategory()")
    class CreateCategory {

        // ======================== Helpers ========================

        private CategoryRequestDTO validRequest() {
            return new CategoryRequestDTO(VALID_TITLE, VALID_DESCRIPTION);
        }

        private CategoryResponseDTO validResponse(UUID id) {
            return new CategoryResponseDTO(
                    id, VALID_TITLE, VALID_DESCRIPTION, true
            );
        }

        // ==================== SUCCESS CASES (201) ====================

        @Nested
        @DisplayName("Success cases — 201 Created")
        class SuccessCases {

            @Test
            @DisplayName("Should return 201 and correct JSON when admin creates a valid category")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn201_whenAdminCreatesValidCategory() throws Exception {
                UUID expectedId = UUID.randomUUID();
                when(categoryService.createCategory(any(CategoryRequestDTO.class)))
                        .thenReturn(validResponse(expectedId));

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").value(expectedId.toString()))
                        .andExpect(jsonPath("$.title").value(VALID_TITLE))
                        .andExpect(jsonPath("$.description").value(VALID_DESCRIPTION))
                        .andExpect(jsonPath("$.isActive").value(true));

                verify(categoryService).createCategory(any(CategoryRequestDTO.class));
            }

            @Test
            @DisplayName("Should return 201 when admin creates a category with null description")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn201_whenDescriptionIsNull() throws Exception {
                UUID expectedId = UUID.randomUUID();
                CategoryRequestDTO request = new CategoryRequestDTO(VALID_TITLE, null);
                CategoryResponseDTO response = new CategoryResponseDTO(
                        expectedId, VALID_TITLE, null, true
                );
                when(categoryService.createCategory(any(CategoryRequestDTO.class))).thenReturn(response);

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").value(expectedId.toString()))
                        .andExpect(jsonPath("$.title").value(VALID_TITLE))
                        .andExpect(jsonPath("$.description").value(nullValue()));
            }

            @Test
            @DisplayName("Should call categoryService.createCategory exactly once")
            @WithMockUser(roles = "ADMIN")
            void shouldCallServiceExactlyOnce() throws Exception {
                when(categoryService.createCategory(any(CategoryRequestDTO.class)))
                        .thenReturn(validResponse(UUID.randomUUID()));

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated());

                verify(categoryService).createCategory(any(CategoryRequestDTO.class));
                verifyNoMoreInteractions(categoryService);
            }
        }

        // ==================== AUTHENTICATION FAILURES (401) ====================

        @Nested
        @DisplayName("Authentication failures — 401 Unauthorized")
        class AuthenticationFailures {

            @Test
            @DisplayName("Should return 401 when no credentials are provided (anonymous)")
            void shouldReturn401_whenNoCredentials() throws Exception {
                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isUnauthorized());

                verifyNoInteractions(categoryService);
            }

            @Test
            @DisplayName("Should return 401 when invalid credentials are provided")
            void shouldReturn401_whenInvalidCredentials() throws Exception {
                mockMvc.perform(post(CATEGORIES_URL)
                                .with(httpBasic("wrong@email.com", "WrongPassword1!"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isUnauthorized());

                verifyNoInteractions(categoryService);
            }
        }

        // ==================== AUTHORIZATION FAILURES (403) ====================

        @Nested
        @DisplayName("Authorization failures — 403 Forbidden")
        class AuthorizationFailures {

            @Test
            @DisplayName("Should return 403 when authenticated user has ROLE_USER (not ADMIN)")
            @WithMockUser(roles = "USER")
            void shouldReturn403_whenUserRoleIsNotAdmin() throws Exception {
                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(categoryService);
            }

            @Test
            @DisplayName("Should return 403 when user has no roles at all")
            @WithMockUser(roles = {})
            void shouldReturn403_whenUserHasNoRoles() throws Exception {
                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isForbidden());

                verifyNoInteractions(categoryService);
            }
        }

        // ==================== VALIDATION FAILURES (400) ====================

        @Nested
        @DisplayName("Validation failures — 400 Bad Request")
        class ValidationFailures {

            // ---------- title ----------

            @Test
            @DisplayName("Should return 400 when title is null")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenTitleIsNull() throws Exception {
                CategoryRequestDTO request = new CategoryRequestDTO(null, VALID_DESCRIPTION);

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(categoryService);
            }

            @Test
            @DisplayName("Should return 400 when title is blank")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenTitleIsBlank() throws Exception {
                CategoryRequestDTO request = new CategoryRequestDTO("   ", VALID_DESCRIPTION);

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(categoryService);
            }

            @Test
            @DisplayName("Should return 400 when title is too short (less than 3 characters)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenTitleIsTooShort() throws Exception {
                CategoryRequestDTO request = new CategoryRequestDTO("AB", VALID_DESCRIPTION);

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(categoryService);
            }

            @Test
            @DisplayName("Should return 400 when title exceeds 255 characters")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenTitleTooLong() throws Exception {
                CategoryRequestDTO request = new CategoryRequestDTO("A".repeat(256), VALID_DESCRIPTION);

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(categoryService);
            }

            // ---------- description ----------

            @Test
            @DisplayName("Should return 400 when description is provided but shorter than 40 characters")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenDescriptionTooShort() throws Exception {
                CategoryRequestDTO request = new CategoryRequestDTO(VALID_TITLE, "Too short desc");

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(categoryService);
            }

            @Test
            @DisplayName("Should return 400 when description exceeds 5000 characters")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenDescriptionTooLong() throws Exception {
                CategoryRequestDTO request = new CategoryRequestDTO(VALID_TITLE, "D".repeat(5001));

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(categoryService);
            }
        }

        // ==================== CONFLICT FAILURES (409) ====================

        @Nested
        @DisplayName("Conflict failures — 409 Conflict")
        class ConflictFailures {

            @Test
            @DisplayName("Should return 409 when category with the same title already exists")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn409_whenCategoryAlreadyExists() throws Exception {
                when(categoryService.createCategory(any(CategoryRequestDTO.class)))
                        .thenThrow(new CategoryAlreadyExistsException("Category already exists"));

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.status").value(409))
                        .andExpect(jsonPath("$.message").isNotEmpty());
            }
        }

        // ==================== SERVICE EXCEPTION HANDLING ====================

        @Nested
        @DisplayName("Service exception handling")
        class ServiceExceptionHandling {

            @Test
            @DisplayName("Should return 500 when service throws an unexpected RuntimeException")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn500_whenServiceThrowsRuntimeException() throws Exception {
                when(categoryService.createCategory(any(CategoryRequestDTO.class)))
                        .thenThrow(new RuntimeException("Database connection lost"));

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.status").value(500))
                        .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
            }

            @Test
            @DisplayName("Should return 500 when service throws an unexpected IllegalStateException")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn500_whenServiceThrowsIllegalStateException() throws Exception {
                when(categoryService.createCategory(any(CategoryRequestDTO.class)))
                        .thenThrow(new IllegalStateException("Database connection lost"));

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.status").value(500))
                        .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
            }
        }

        // ==================== MALFORMED INPUT ====================

        @Nested
        @DisplayName("Malformed input")
        class MalformedInput {

            @Test
            @DisplayName("Should return 400 when request body is malformed JSON")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenJsonIsMalformed() throws Exception {
                String malformedJson = "{ \"title\": \"Test\", \"description\": }";

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(malformedJson))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(categoryService);
            }

            @Test
            @DisplayName("Should return 400 when request body is missing entirely")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenRequestBodyIsMissing() throws Exception {
                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(categoryService);
            }

            @Test
            @DisplayName("Should return 400 when request body is an empty JSON object")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenRequestBodyIsEmptyObject() throws Exception {
                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty());

                verifyNoInteractions(categoryService);
            }

            @Test
            @DisplayName("Should return 400 when title is a wrong type (e.g., array instead of string)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn400_whenTitleIsWrongType() throws Exception {
                String badJson = """
                        {
                            "title": ["invalid"],
                            "description": "All kinds of electronic devices like phones, laptops, and tablets."
                        }
                        """;

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badJson))
                        .andExpect(status().isBadRequest());

                verifyNoInteractions(categoryService);
            }
        }

        // ==================== JSON RESPONSE STRUCTURE ====================

        @Nested
        @DisplayName("JSON response structure validation")
        class JsonResponseStructure {

            @Test
            @DisplayName("Should return all expected fields in the success response")
            @WithMockUser(roles = "ADMIN")
            void shouldReturnAllFieldsInResponse() throws Exception {
                UUID expectedId = UUID.randomUUID();
                when(categoryService.createCategory(any(CategoryRequestDTO.class)))
                        .thenReturn(validResponse(expectedId));

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").exists())
                        .andExpect(jsonPath("$.title").exists())
                        .andExpect(jsonPath("$.description").exists())
                        .andExpect(jsonPath("$.isActive").exists());
            }

            @Test
            @DisplayName("Error response should contain status, message, and timeStamp fields")
            @WithMockUser(roles = "ADMIN")
            void shouldReturnErrorResponseStructure() throws Exception {
                CategoryRequestDTO request = new CategoryRequestDTO(null, VALID_DESCRIPTION);

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400))
                        .andExpect(jsonPath("$.message").isNotEmpty())
                        .andExpect(jsonPath("$.timeStamp").isNumber());
            }
        }

        // ==================== EDGE CASES ====================

        @Nested
        @DisplayName("Edge cases")
        class EdgeCases {

            @Test
            @DisplayName("Should return 201 when title is exactly 3 characters (boundary minimum)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn201_whenTitleIsExactlyMinLength() throws Exception {
                UUID expectedId = UUID.randomUUID();
                CategoryRequestDTO request = new CategoryRequestDTO("Abc", VALID_DESCRIPTION);
                CategoryResponseDTO response = new CategoryResponseDTO(
                        expectedId, "abc", VALID_DESCRIPTION, true
                );
                when(categoryService.createCategory(any(CategoryRequestDTO.class))).thenReturn(response);

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.title").value("abc"));
            }

            @Test
            @DisplayName("Should return 201 when title is exactly 255 characters (boundary maximum)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn201_whenTitleIsExactlyMaxLength() throws Exception {
                String maxTitle = "T".repeat(255).toLowerCase();
                UUID expectedId = UUID.randomUUID();
                CategoryRequestDTO request = new CategoryRequestDTO(maxTitle, VALID_DESCRIPTION);
                CategoryResponseDTO response = new CategoryResponseDTO(
                        expectedId, maxTitle, VALID_DESCRIPTION, true
                );
                when(categoryService.createCategory(any(CategoryRequestDTO.class))).thenReturn(response);

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated());
            }

            @Test
            @DisplayName("Should return 201 when description is exactly 40 characters (boundary minimum)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn201_whenDescriptionIsExactlyMinLength() throws Exception {
                String minDescription = "D".repeat(40);
                UUID expectedId = UUID.randomUUID();
                CategoryRequestDTO request = new CategoryRequestDTO(VALID_TITLE, minDescription);
                CategoryResponseDTO response = new CategoryResponseDTO(
                        expectedId, VALID_TITLE, minDescription, true
                );
                when(categoryService.createCategory(any(CategoryRequestDTO.class))).thenReturn(response);

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.description").value(minDescription));
            }

            @Test
            @DisplayName("Should return 201 when description is exactly 5000 characters (boundary maximum)")
            @WithMockUser(roles = "ADMIN")
            void shouldReturn201_whenDescriptionIsExactlyMaxLength() throws Exception {
                String maxDescription = "D".repeat(5000);
                UUID expectedId = UUID.randomUUID();
                CategoryRequestDTO request = new CategoryRequestDTO(VALID_TITLE, maxDescription);
                CategoryResponseDTO response = new CategoryResponseDTO(
                        expectedId, VALID_TITLE, maxDescription, true
                );
                when(categoryService.createCategory(any(CategoryRequestDTO.class))).thenReturn(response);

                mockMvc.perform(post(CATEGORIES_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.description").value(maxDescription));
            }
        }
    }
}
