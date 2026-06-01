package com.sivan.ecommerce.mapper.customer;

import com.sivan.ecommerce.dto.customer.CustomerRequestDTO;
import com.sivan.ecommerce.dto.customer.CustomerResponseDTO;
import com.sivan.ecommerce.entity.customer.Customer;

public class CustomerMapper {

    private CustomerMapper() {
        // Prevent instantiation
    }

    public static Customer mapCustomerRequestToCustomer(CustomerRequestDTO customerRequestDTO) {
        return new Customer(
                customerRequestDTO.firstName(),
                customerRequestDTO.lastName(),
                customerRequestDTO.email(),
                customerRequestDTO.location(),
                customerRequestDTO.password()
        );
    }

    public static CustomerResponseDTO mapCustomerToCustomerResponse(Customer customer) {
        return new CustomerResponseDTO(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getLocation()
        );
    }
}
