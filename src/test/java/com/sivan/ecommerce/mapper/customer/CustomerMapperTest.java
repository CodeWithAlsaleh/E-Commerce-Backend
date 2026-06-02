package com.sivan.ecommerce.mapper.customer;

import com.sivan.ecommerce.dto.customer.CustomerRequestDTO;
import com.sivan.ecommerce.dto.customer.CustomerResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.customer.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CustomerMapper}.
 * Tests both mapping directions: DTO → Entity and Entity → DTO.
 * No Spring context is loaded — pure unit tests.
 */
@DisplayName("CustomerMapper — mapping methods")
class CustomerMapperTest {

    // ======================== Constants ========================

    private static final String VALID_FIRST_NAME = "John";
    private static final String VALID_LAST_NAME = "Doe";
    private static final String VALID_EMAIL = "john.doe@example.com";
    private static final String VALID_LOCATION = "New York, USA";
    private static final String VALID_PASSWORD = "SecurePass123!";

    // ==================== mapCustomerRequestToCustomer() ====================

    @Nested
    @DisplayName("mapCustomerRequestToCustomer()")
    class MapRequestToEntity {

        @Test
        @DisplayName("Should map all fields correctly from DTO to entity")
        void shouldMapAllFieldsCorrectly() {
            // Arrange
            CustomerRequestDTO request = new CustomerRequestDTO(
                    VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                    VALID_LOCATION, VALID_PASSWORD
            );

            // Act
            Customer customer = CustomerMapper.mapCustomerRequestToCustomer(request);

            // Assert
            assertNotNull(customer);
            assertEquals(VALID_FIRST_NAME, customer.getFirstName());
            assertEquals(VALID_LAST_NAME, customer.getLastName());
            assertEquals(VALID_EMAIL, customer.getEmail());
            assertEquals(VALID_LOCATION, customer.getLocation());
            assertEquals(VALID_PASSWORD, customer.getPassword());
        }

        @Test
        @DisplayName("Should handle null location (optional field)")
        void shouldHandleNullLocation() {
            // Arrange
            CustomerRequestDTO request = new CustomerRequestDTO(
                    VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                    null, VALID_PASSWORD
            );

            // Act
            Customer customer = CustomerMapper.mapCustomerRequestToCustomer(request);

            // Assert
            assertNotNull(customer);
            assertNull(customer.getLocation());
            // Other fields should still be mapped
            assertEquals(VALID_FIRST_NAME, customer.getFirstName());
            assertEquals(VALID_LAST_NAME, customer.getLastName());
            assertEquals(VALID_EMAIL, customer.getEmail());
            assertEquals(VALID_PASSWORD, customer.getPassword());
        }

        @Test
        @DisplayName("Should preserve trimmed values from compact constructor")
        void shouldPreserveTrimmedValues() {
            // Arrange — the DTO compact constructor trims fields before mapping
            CustomerRequestDTO request = new CustomerRequestDTO(
                    "  John  ", "  Doe  ", "  JOHN.DOE@EXAMPLE.COM  ",
                    "  New York  ", VALID_PASSWORD
            );

            // Act
            Customer customer = CustomerMapper.mapCustomerRequestToCustomer(request);

            // Assert — values should already be trimmed/lowercased by the DTO constructor
            assertEquals("John", customer.getFirstName());
            assertEquals("Doe", customer.getLastName());
            assertEquals("john.doe@example.com", customer.getEmail());
            assertEquals("New York", customer.getLocation());
        }

        @Test
        @DisplayName("Should handle special characters in names")
        void shouldHandleSpecialCharacters() {
            // Arrange
            CustomerRequestDTO request = new CustomerRequestDTO(
                    "José", "O'Brien-García", "jose@example.com",
                    "São Paulo, Brasil", VALID_PASSWORD
            );

            // Act
            Customer customer = CustomerMapper.mapCustomerRequestToCustomer(request);

            // Assert
            assertEquals("José", customer.getFirstName());
            assertEquals("O'Brien-García", customer.getLastName());
            assertEquals("São Paulo, Brasil", customer.getLocation());
        }

        @Test
        @DisplayName("Should handle Unicode characters in all fields")
        void shouldHandleUnicodeCharacters() {
            // Arrange
            CustomerRequestDTO request = new CustomerRequestDTO(
                    "太郎", "田中", "taro@example.com",
                    "東京都, 日本", VALID_PASSWORD
            );

            // Act
            Customer customer = CustomerMapper.mapCustomerRequestToCustomer(request);

            // Assert
            assertEquals("太郎", customer.getFirstName());
            assertEquals("田中", customer.getLastName());
            assertEquals("東京都, 日本", customer.getLocation());
        }

        @Test
        @DisplayName("Should not set ID on mapped entity (ID is assigned by Hibernate)")
        void shouldNotSetIdOnMappedEntity() {
            // Arrange
            CustomerRequestDTO request = new CustomerRequestDTO(
                    VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                    VALID_LOCATION, VALID_PASSWORD
            );

            // Act
            Customer customer = CustomerMapper.mapCustomerRequestToCustomer(request);

            // Assert
            assertNull(customer.getId());
        }

        @Test
        @DisplayName("Should not set roles on mapped entity (roles are assigned by the service)")
        void shouldNotSetRolesOnMappedEntity() {
            // Arrange
            CustomerRequestDTO request = new CustomerRequestDTO(
                    VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                    VALID_LOCATION, VALID_PASSWORD
            );

            // Act
            Customer customer = CustomerMapper.mapCustomerRequestToCustomer(request);

            // Assert
            assertNotNull(customer.getRoles());
            assertTrue(customer.getRoles().isEmpty());
        }
    }

    // ==================== mapCustomerToCustomerResponse() ====================

    @Nested
    @DisplayName("mapCustomerToCustomerResponse()")
    class MapEntityToResponse {

        @Test
        @DisplayName("Should map all fields correctly from entity to response DTO")
        void shouldMapAllFieldsCorrectly() {
            // Arrange
            UUID expectedId = UUID.randomUUID();
            Customer customer = new Customer(
                    VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                    VALID_LOCATION, VALID_PASSWORD
            );
            EntityTestUtil.setId(customer, expectedId);

            // Act
            CustomerResponseDTO response = CustomerMapper.mapCustomerToCustomerResponse(customer);

            // Assert
            assertNotNull(response);
            assertEquals(expectedId, response.id());
            assertEquals(VALID_FIRST_NAME, response.firstName());
            assertEquals(VALID_LAST_NAME, response.lastName());
            assertEquals(VALID_EMAIL, response.email());
            assertEquals(VALID_LOCATION, response.location());
        }

        @Test
        @DisplayName("Should map null location correctly")
        void shouldMapNullLocation() {
            // Arrange
            UUID expectedId = UUID.randomUUID();
            Customer customer = new Customer(
                    VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                    null, VALID_PASSWORD
            );
            EntityTestUtil.setId(customer, expectedId);

            // Act
            CustomerResponseDTO response = CustomerMapper.mapCustomerToCustomerResponse(customer);

            // Assert
            assertNull(response.location());
        }

        @Test
        @DisplayName("Should not expose password in the response DTO")
        void shouldNotExposePasswordInResponse() {
            // Arrange
            UUID expectedId = UUID.randomUUID();
            Customer customer = new Customer(
                    VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                    VALID_LOCATION, VALID_PASSWORD
            );
            EntityTestUtil.setId(customer, expectedId);

            // Act
            CustomerResponseDTO response = CustomerMapper.mapCustomerToCustomerResponse(customer);

            // Assert — CustomerResponseDTO does not have a password field
            // This is verified at compile time, but this test documents the design intent
            assertNotNull(response);
            assertEquals(expectedId, response.id());
            assertEquals(VALID_FIRST_NAME, response.firstName());
            assertEquals(VALID_LAST_NAME, response.lastName());
            assertEquals(VALID_EMAIL, response.email());
            assertEquals(VALID_LOCATION, response.location());
        }

        @Test
        @DisplayName("Should map entity with null ID (pre-persist state)")
        void shouldMapEntityWithNullId() {
            // Arrange
            Customer customer = new Customer(
                    VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                    VALID_LOCATION, VALID_PASSWORD
            );
            // ID is null — entity not yet persisted

            // Act
            CustomerResponseDTO response = CustomerMapper.mapCustomerToCustomerResponse(customer);

            // Assert
            assertNotNull(response);
            assertNull(response.id());
        }
    }
}
