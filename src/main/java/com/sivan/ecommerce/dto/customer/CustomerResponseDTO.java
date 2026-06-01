package com.sivan.ecommerce.dto.customer;

import java.util.UUID;

public record CustomerResponseDTO(UUID id,
                                  String firstName,
                                  String lastName,
                                  String email,
                                  String location) {
}
