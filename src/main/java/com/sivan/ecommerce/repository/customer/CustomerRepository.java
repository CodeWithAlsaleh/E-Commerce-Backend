package com.sivan.ecommerce.repository.customer;

import com.sivan.ecommerce.entity.customer.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByEmail(String email);
}
