package com.sivan.ecommerce.controller.customer;

import com.sivan.ecommerce.dto.customer.CustomerRequestDTO;
import com.sivan.ecommerce.dto.customer.CustomerResponseDTO;
import com.sivan.ecommerce.service.customer.CustomerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class CustomerRestController {

    private final CustomerService customerService;

    @Autowired
    public CustomerRestController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponseDTO> createCustomer(@RequestBody @Valid CustomerRequestDTO customerRequestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.createCustomer(customerRequestDTO));
    }
}
