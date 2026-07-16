package com.sivan.ecommerce.repository.token;

import com.sivan.ecommerce.entity.customer.Customer;
import com.sivan.ecommerce.entity.token.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    /*
     *  Deletes all refresh tokens for a given customer.
     *  Called during login to ensure only one active refresh token exists per customer.
     *
     *  Using a bulk DELETE query instead of deleteByCustomer(Customer) to avoid
     *  Hibernate loading all tokens into memory just to delete them.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.customer = :customer")
    void deleteAllByCustomer(@Param("customer") Customer customer);
}
