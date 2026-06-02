package com.sivan.ecommerce.service.customer;

import com.sivan.ecommerce.dto.customer.CustomerRequestDTO;
import com.sivan.ecommerce.dto.customer.CustomerResponseDTO;
import com.sivan.ecommerce.entity.EntityTestUtil;
import com.sivan.ecommerce.entity.customer.Customer;
import com.sivan.ecommerce.entity.role.Role;
import com.sivan.ecommerce.entity.role.RoleName;
import com.sivan.ecommerce.exception.CustomerAlreadyExistsException;
import com.sivan.ecommerce.repository.customer.CustomerRepository;
import com.sivan.ecommerce.repository.role.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CustomerServiceImpl}.
 * Only the service layer is tested — repositories and password encoder are mocked.
 * No Spring context is loaded, keeping tests fast and isolated.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerServiceImpl")
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CustomerServiceImpl customerService;

    // ======================== Helper Constants ========================

    private static final String VALID_FIRST_NAME = "John";
    private static final String VALID_LAST_NAME = "Doe";
    private static final String VALID_EMAIL = "john.doe@example.com";
    private static final String VALID_LOCATION = "New York, USA";
    private static final String VALID_PASSWORD = "SecurePass123!";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedPasswordHash";

    // ==================== createCustomer() ====================
    @Nested
    @DisplayName("createCustomer()")
    class CreateCustomer {

        // ======================== Helper Methods ========================

        /**
         * Builds a valid {@link CustomerRequestDTO} with all fields populated.
         */
        private CustomerRequestDTO validRequest() {
            return new CustomerRequestDTO(
                    VALID_FIRST_NAME,
                    VALID_LAST_NAME,
                    VALID_EMAIL,
                    VALID_LOCATION,
                    VALID_PASSWORD
            );
        }

        /**
         * Builds a valid {@link CustomerRequestDTO} with null location.
         */
        private CustomerRequestDTO requestWithNullLocation() {
            return new CustomerRequestDTO(
                    VALID_FIRST_NAME,
                    VALID_LAST_NAME,
                    VALID_EMAIL,
                    null,
                    VALID_PASSWORD
            );
        }

        /**
         * Creates a saved Customer entity with an ID assigned via reflection,
         * mimicking Hibernate's @UuidGenerator behavior on persist.
         */
        private Customer buildSavedCustomer(UUID id, String email) {
            Customer customer = new Customer(
                    VALID_FIRST_NAME, VALID_LAST_NAME, email,
                    VALID_LOCATION, ENCODED_PASSWORD
            );
            EntityTestUtil.setId(customer, id);
            return customer;
        }

        /**
         * Creates a Role entity with an ID assigned via reflection.
         */
        private Role buildRole(UUID id, RoleName roleName) {
            Role role = new Role(roleName);
            EntityTestUtil.setId(role, id);
            return role;
        }

        /**
         * Sets up the common mocking for a successful createCustomer() flow:
         * - email does not exist
         * - ROLE_USER is found
         * - password is encoded
         * - repository.save() returns the saved customer
         */
        private void stubCreateCustomerSuccess(UUID customerId) {
            when(customerRepository.existsByEmail(anyString())).thenReturn(false);

            Role userRole = buildRole(UUID.randomUUID(), RoleName.ROLE_USER);
            when(roleRepository.findByRoleName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));

            when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);

            Customer savedCustomer = buildSavedCustomer(customerId, VALID_EMAIL);
            when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);
        }

        // ==================== createCustomer() — SUCCESS CASES ====================

        @Nested
        @DisplayName("createCustomer() — Success cases")
        class CreateCustomerSuccess {

            @Test
            @DisplayName("Should return a valid CustomerResponseDTO when request is valid")
            void shouldReturnCustomerResponseDTO_whenRequestIsValid() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                stubCreateCustomerSuccess(expectedId);
                CustomerRequestDTO request = validRequest();

                // Act
                CustomerResponseDTO response = customerService.createCustomer(request);

                // Assert
                assertNotNull(response);
                assertEquals(expectedId, response.id());
                assertEquals(VALID_FIRST_NAME, response.firstName());
                assertEquals(VALID_LAST_NAME, response.lastName());
                assertEquals(VALID_EMAIL, response.email());
                assertEquals(VALID_LOCATION, response.location());
            }

            @Test
            @DisplayName("Should save the customer exactly once via the repository")
            void shouldCallRepositorySaveExactlyOnce() {
                // Arrange
                stubCreateCustomerSuccess(UUID.randomUUID());

                // Act
                customerService.createCustomer(validRequest());

                // Assert
                verify(customerRepository).save(any(Customer.class));
                verify(customerRepository).existsByEmail(anyString());
                verifyNoMoreInteractions(customerRepository);
            }

            @Test
            @DisplayName("Should encode the password before saving")
            void shouldEncodePasswordBeforeSaving() {
                // Arrange
                stubCreateCustomerSuccess(UUID.randomUUID());

                // Act
                customerService.createCustomer(validRequest());

                // Assert
                verify(passwordEncoder).encode(anyString());
            }

            @Test
            @DisplayName("Should assign ROLE_USER to the new customer")
            void shouldAssignRoleUserToNewCustomer() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                when(customerRepository.existsByEmail(anyString())).thenReturn(false);

                Role userRole = buildRole(UUID.randomUUID(), RoleName.ROLE_USER);
                when(roleRepository.findByRoleName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));

                when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);

                when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
                    Customer captured = inv.getArgument(0);

                    // Verify the role was assigned before save
                    assertFalse(captured.getRoles().isEmpty(), "Customer should have at least one role");
                    assertTrue(captured.getRoles().contains(userRole), "Customer should have ROLE_USER");

                    return buildSavedCustomer(expectedId, captured.getEmail());
                });

                // Act
                customerService.createCustomer(validRequest());

                // Assert
                verify(roleRepository).findByRoleName(RoleName.ROLE_USER);
            }

            @Test
            @DisplayName("Should assign a Cart to the new customer before saving")
            void shouldAssignCartToNewCustomer() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                when(customerRepository.existsByEmail(anyString())).thenReturn(false);

                Role userRole = buildRole(UUID.randomUUID(), RoleName.ROLE_USER);
                when(roleRepository.findByRoleName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));

                when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);

                when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
                    Customer captured = inv.getArgument(0);

                    // Verify the cart was assigned before save
                    assertNotNull(captured.getCart(), "Customer should have a Cart assigned");

                    return buildSavedCustomer(expectedId, captured.getEmail());
                });

                // Act
                customerService.createCustomer(validRequest());

                // Assert
                verify(customerRepository).save(any(Customer.class));
            }

            @Test
            @DisplayName("Should succeed when location is null (optional field)")
            void shouldSucceed_whenLocationIsNull() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                when(customerRepository.existsByEmail(anyString())).thenReturn(false);

                Role userRole = buildRole(UUID.randomUUID(), RoleName.ROLE_USER);
                when(roleRepository.findByRoleName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));

                when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);

                Customer savedCustomer = new Customer(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        null, ENCODED_PASSWORD
                );
                EntityTestUtil.setId(savedCustomer, expectedId);
                when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

                // Act
                CustomerResponseDTO response = customerService.createCustomer(requestWithNullLocation());

                // Assert
                assertNotNull(response);
                assertEquals(expectedId, response.id());
                assertNull(response.location());
            }

            @Test
            @DisplayName("Should check email existence before attempting to save")
            void shouldCheckEmailExistenceFirst() {
                // Arrange
                stubCreateCustomerSuccess(UUID.randomUUID());

                // Act
                customerService.createCustomer(validRequest());

                // Assert — verify the order: existsByEmail is called before save
                var inOrder = inOrder(customerRepository);
                inOrder.verify(customerRepository).existsByEmail(anyString());
                inOrder.verify(customerRepository).save(any(Customer.class));
            }
        }

        // ==================== createCustomer() — CONFLICT CASES (409) ====================

        @Nested
        @DisplayName("createCustomer() — Conflict cases (duplicate email)")
        class CreateCustomerConflict {

            @Test
            @DisplayName("Should throw CustomerAlreadyExistsException when email already exists")
            void shouldThrowException_whenEmailAlreadyExists() {
                // Arrange
                when(customerRepository.existsByEmail(anyString())).thenReturn(true);

                // Act & Assert
                CustomerAlreadyExistsException exception = assertThrows(
                        CustomerAlreadyExistsException.class,
                        () -> customerService.createCustomer(validRequest())
                );
                assertEquals("Email account already exists", exception.getMessage());
            }

            @Test
            @DisplayName("Should not call save or roleRepository when email already exists")
            void shouldNotCallSaveOrRoleRepo_whenEmailAlreadyExists() {
                // Arrange
                when(customerRepository.existsByEmail(anyString())).thenReturn(true);

                // Act
                assertThrows(CustomerAlreadyExistsException.class,
                        () -> customerService.createCustomer(validRequest()));

                // Assert
                verify(customerRepository, never()).save(any(Customer.class));
                verifyNoInteractions(roleRepository);
                verifyNoInteractions(passwordEncoder);
            }
        }

        // ==================== createCustomer() — SERVER FAILURE CASES ====================

        @Nested
        @DisplayName("createCustomer() — Server failure simulation")
        class CreateCustomerServerFailures {

            @Test
            @DisplayName("Should throw IllegalStateException when ROLE_USER is not found in database")
            void shouldThrowException_whenDefaultRoleNotFound() {
                // Arrange
                when(customerRepository.existsByEmail(anyString())).thenReturn(false);
                when(roleRepository.findByRoleName(RoleName.ROLE_USER)).thenReturn(Optional.empty());

                // Act & Assert
                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> customerService.createCustomer(validRequest())
                );
                assertEquals("Default Role not found in database", exception.getMessage());
            }

            @Test
            @DisplayName("Should not call save or passwordEncoder when ROLE_USER is not found")
            void shouldNotCallSaveOrEncoder_whenRoleNotFound() {
                // Arrange
                when(customerRepository.existsByEmail(anyString())).thenReturn(false);
                when(roleRepository.findByRoleName(RoleName.ROLE_USER)).thenReturn(Optional.empty());

                // Act
                assertThrows(IllegalStateException.class,
                        () -> customerService.createCustomer(validRequest()));

                // Assert
                verify(customerRepository, never()).save(any(Customer.class));
                verifyNoInteractions(passwordEncoder);
            }

            @Test
            @DisplayName("Should propagate RuntimeException when repository throws on save")
            void shouldPropagateRuntimeException_whenRepositoryThrows() {
                // Arrange
                when(customerRepository.existsByEmail(anyString())).thenReturn(false);

                Role userRole = buildRole(UUID.randomUUID(), RoleName.ROLE_USER);
                when(roleRepository.findByRoleName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));

                when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);

                when(customerRepository.save(any(Customer.class)))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> customerService.createCustomer(validRequest())
                );
                assertEquals("Database connection lost", exception.getMessage());
            }

            @Test
            @DisplayName("Should propagate RuntimeException when existsByEmail throws")
            void shouldPropagateException_whenExistsByEmailThrows() {
                // Arrange
                when(customerRepository.existsByEmail(anyString()))
                        .thenThrow(new RuntimeException("Database unavailable"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> customerService.createCustomer(validRequest())
                );
                assertEquals("Database unavailable", exception.getMessage());
            }
        }

        // ==================== createCustomer() — MAPPER INTERACTION ====================

        @Nested
        @DisplayName("createCustomer() — Mapper interaction verification")
        class CreateCustomerMapperVerification {

            @Test
            @DisplayName("Should pass the correctly mapped Customer entity to repository.save()")
            void shouldPassMappedCustomerToRepository() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                when(customerRepository.existsByEmail(anyString())).thenReturn(false);

                Role userRole = buildRole(UUID.randomUUID(), RoleName.ROLE_USER);
                when(roleRepository.findByRoleName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));

                when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);

                Customer savedCustomer = buildSavedCustomer(expectedId, VALID_EMAIL);
                when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
                    Customer captured = inv.getArgument(0);

                    // Verify the mapper correctly transformed the DTO into the entity
                    assertEquals(VALID_FIRST_NAME, captured.getFirstName());
                    assertEquals(VALID_LAST_NAME, captured.getLastName());
                    assertEquals(VALID_EMAIL, captured.getEmail());
                    assertEquals(VALID_LOCATION, captured.getLocation());

                    return savedCustomer;
                });

                // Act
                customerService.createCustomer(validRequest());

                // Assert — verify save was called
                verify(customerRepository).save(any(Customer.class));
            }
        }

        // ==================== createCustomer() — EDGE CASES ====================

        @Nested
        @DisplayName("createCustomer() — Edge cases")
        class CreateCustomerEdgeCases {

            @Test
            @DisplayName("Should handle special characters in names")
            void shouldHandleSpecialCharactersInNames() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                CustomerRequestDTO request = new CustomerRequestDTO(
                        "José", "O'Brien", "jose.obrien@example.com", VALID_LOCATION, VALID_PASSWORD
                );

                when(customerRepository.existsByEmail(anyString())).thenReturn(false);

                Role userRole = buildRole(UUID.randomUUID(), RoleName.ROLE_USER);
                when(roleRepository.findByRoleName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));

                when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);

                Customer savedCustomer = new Customer(
                        "José", "O'Brien", "jose.obrien@example.com",
                        VALID_LOCATION, ENCODED_PASSWORD
                );
                EntityTestUtil.setId(savedCustomer, expectedId);
                when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

                // Act
                CustomerResponseDTO response = customerService.createCustomer(request);

                // Assert
                assertNotNull(response);
                assertEquals("José", response.firstName());
                assertEquals("O'Brien", response.lastName());
            }

            @Test
            @DisplayName("Should handle Unicode characters in location")
            void shouldHandleUnicodeCharactersInLocation() {
                // Arrange
                UUID expectedId = UUID.randomUUID();
                String unicodeLocation = "東京都, 日本";
                CustomerRequestDTO request = new CustomerRequestDTO(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        unicodeLocation, VALID_PASSWORD
                );

                when(customerRepository.existsByEmail(anyString())).thenReturn(false);

                Role userRole = buildRole(UUID.randomUUID(), RoleName.ROLE_USER);
                when(roleRepository.findByRoleName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));

                when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);

                Customer savedCustomer = new Customer(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        unicodeLocation, ENCODED_PASSWORD
                );
                EntityTestUtil.setId(savedCustomer, expectedId);
                when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

                // Act
                CustomerResponseDTO response = customerService.createCustomer(request);

                // Assert
                assertNotNull(response);
                assertEquals(unicodeLocation, response.location());
            }
        }
    }

    // ==================== loadUserByUsername() ====================
    @Nested
    @DisplayName("loadUserByUsername() ")
    class LoadUserByUsername {
        // ==================== loadUserByUsername() — SUCCESS CASES ====================

        @Nested
        @DisplayName("loadUserByUsername() — Success cases")
        class LoadUserByUsernameSuccess {

            @Test
            @DisplayName("Should return UserDetails when email exists and customer is active")
            void shouldReturnUserDetails_whenEmailExistsAndActive() {
                // Arrange
                Customer customer = new Customer(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, ENCODED_PASSWORD
                );
                Role userRole = new Role(RoleName.ROLE_USER);
                customer.addRole(userRole);

                when(customerRepository.findByEmailWithRoles(VALID_EMAIL))
                        .thenReturn(Optional.of(customer));

                // Act
                UserDetails userDetails = customerService.loadUserByUsername(VALID_EMAIL);

                // Assert
                assertNotNull(userDetails);
                assertEquals(VALID_EMAIL, userDetails.getUsername());
                assertEquals(ENCODED_PASSWORD, userDetails.getPassword());
            }

            @Test
            @DisplayName("Should map single ROLE_USER to granted authorities correctly")
            void shouldMapSingleRoleToAuthorities() {
                // Arrange
                Customer customer = new Customer(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, ENCODED_PASSWORD
                );
                Role userRole = new Role(RoleName.ROLE_USER);
                customer.addRole(userRole);

                when(customerRepository.findByEmailWithRoles(VALID_EMAIL))
                        .thenReturn(Optional.of(customer));

                // Act
                UserDetails userDetails = customerService.loadUserByUsername(VALID_EMAIL);

                // Assert
                Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();
                assertEquals(1, authorities.size());
                assertTrue(authorities.stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
            }

            @Test
            @DisplayName("Should map multiple roles to granted authorities correctly")
            void shouldMapMultipleRolesToAuthorities() {
                // Arrange
                Customer customer = new Customer(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, ENCODED_PASSWORD
                );
                Role userRole = new Role(RoleName.ROLE_USER);
                Role adminRole = new Role(RoleName.ROLE_ADMIN);
                customer.addRole(userRole);
                customer.addRole(adminRole);

                when(customerRepository.findByEmailWithRoles(VALID_EMAIL))
                        .thenReturn(Optional.of(customer));

                // Act
                UserDetails userDetails = customerService.loadUserByUsername(VALID_EMAIL);

                // Assert
                Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();
                assertEquals(2, authorities.size());
                assertTrue(authorities.stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
                assertTrue(authorities.stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
            }

            @Test
            @DisplayName("Should lowercase the email before querying the repository")
            void shouldLowercaseEmailBeforeQuery() {
                // Arrange
                String upperCaseEmail = "JOHN.DOE@EXAMPLE.COM";
                Customer customer = new Customer(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, ENCODED_PASSWORD
                );
                Role userRole = new Role(RoleName.ROLE_USER);
                customer.addRole(userRole);

                when(customerRepository.findByEmailWithRoles("john.doe@example.com"))
                        .thenReturn(Optional.of(customer));

                // Act
                UserDetails userDetails = customerService.loadUserByUsername(upperCaseEmail);

                // Assert
                assertNotNull(userDetails);
                verify(customerRepository).findByEmailWithRoles("john.doe@example.com");
            }

            @Test
            @DisplayName("Should call findByEmailWithRoles exactly once")
            void shouldCallRepositoryExactlyOnce() {
                // Arrange
                Customer customer = new Customer(
                        VALID_FIRST_NAME, VALID_LAST_NAME, VALID_EMAIL,
                        VALID_LOCATION, ENCODED_PASSWORD
                );
                Role userRole = new Role(RoleName.ROLE_USER);
                customer.addRole(userRole);

                when(customerRepository.findByEmailWithRoles(VALID_EMAIL))
                        .thenReturn(Optional.of(customer));

                // Act
                customerService.loadUserByUsername(VALID_EMAIL);

                // Assert
                verify(customerRepository).findByEmailWithRoles(VALID_EMAIL);
                verifyNoMoreInteractions(customerRepository);
            }
        }

        // ==================== loadUserByUsername() — NOT FOUND (UsernameNotFoundException) ====================

        @Nested
        @DisplayName("loadUserByUsername() — Not found cases")
        class LoadUserByUsernameNotFound {

            @Test
            @DisplayName("Should throw UsernameNotFoundException when email is not found")
            void shouldThrowException_whenEmailNotFound() {
                // Arrange
                when(customerRepository.findByEmailWithRoles(anyString()))
                        .thenReturn(Optional.empty());

                // Act & Assert
                UsernameNotFoundException exception = assertThrows(
                        UsernameNotFoundException.class,
                        () -> customerService.loadUserByUsername("nonexistent@example.com")
                );
                assertEquals("Invalid username or password", exception.getMessage());
            }

            @Test
            @DisplayName("Should throw UsernameNotFoundException when repository returns empty (inactive user)")
            void shouldThrowException_whenUserIsInactive() {
                // Arrange — the JPQL query already filters by isActive=true,
                // so an inactive user would result in Optional.empty()
                when(customerRepository.findByEmailWithRoles(VALID_EMAIL))
                        .thenReturn(Optional.empty());

                // Act & Assert
                assertThrows(UsernameNotFoundException.class,
                        () -> customerService.loadUserByUsername(VALID_EMAIL));
            }
        }
        
        // ==================== loadUserByUsername() — SERVER FAILURE CASES ====================

        @Nested
        @DisplayName("loadUserByUsername() — Server failure simulation")
        class LoadUserByUsernameServerFailures {

            @Test
            @DisplayName("Should propagate RuntimeException when repository throws")
            void shouldPropagateException_whenRepositoryThrows() {
                // Arrange
                when(customerRepository.findByEmailWithRoles(anyString()))
                        .thenThrow(new RuntimeException("Database connection lost"));

                // Act & Assert
                RuntimeException exception = assertThrows(
                        RuntimeException.class,
                        () -> customerService.loadUserByUsername(VALID_EMAIL)
                );
                assertEquals("Database connection lost", exception.getMessage());
            }
        }
    }
}
