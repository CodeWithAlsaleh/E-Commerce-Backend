package com.sivan.ecommerce.repository.customer;

import com.sivan.ecommerce.dto.customer.CustomerResponseDTO;
import com.sivan.ecommerce.entity.customer.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByEmail(String email);

    /*
     *  NOTE 1: Why use the Entity (Customer) instead of a DTO/Projection for Auth ?
     *  In the UserDetailsService flow, returning the full managed Entity is preferred because:
     *   1. Hibernate cannot "JOIN FETCH" collections (Set<Role>) into a DTO constructor efficiently.
     *   2. Spring Security's bridge needs the authoritative, managed state of the user to build
     *      the UserDetails object reliably without secondary queries or complex mapping logic.
     *
     *  NOTE 2: Why "LEFT JOIN FETCH" instead of "JOIN FETCH" ?
     *   1. "JOIN FETCH" defaults to an INNER JOIN. If a customer exists but has NO cart assigned,
     *      the INNER JOIN filters them out, making the login fail (UsernameNotFoundException).
     *   2. "LEFT JOIN FETCH" ensures the Customer is returned even if the Cart relationship
     *      is null/missing, providing a safety net for the authentication process.
     */
    @Query("""
            SELECT c
            FROM Customer c
            JOIN FETCH c.roles
            LEFT JOIN FETCH c.cart
            WHERE c.email = :email AND
            c.isActive = true
            """)
    Optional<Customer> findByEmailWithRoles(@Param("email") String email);

    Optional<CustomerResponseDTO> findByEmail(String email);
}
