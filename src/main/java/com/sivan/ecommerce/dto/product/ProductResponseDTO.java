package com.sivan.ecommerce.dto.product;

import java.util.UUID;

public record ProductResponseDTO(UUID id,
                                 String title,
                                 String description,
                                 int quantity,
                                 long price,
                                 String currencyCode,
                                 String imageUrl) {
}
