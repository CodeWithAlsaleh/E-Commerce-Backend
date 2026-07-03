package com.sivan.ecommerce.dto.category;

import java.util.UUID;

public record CategoryResponseDTO(UUID id,
                                  String title,
                                  String description,
                                  boolean isActive) {
}
