package com.sivan.ecommerce.service.customer;

import com.sivan.ecommerce.dto.customer.CustomerRequestDTO;
import com.sivan.ecommerce.dto.customer.CustomerResponseDTO;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface CustomerService extends UserDetailsService {

    CustomerResponseDTO createCustomer(CustomerRequestDTO customerRequestDTO);

    CustomerResponseDTO getProfile();
}
