package com.sivan.ecommerce.dto.product;

public record ProductResponseDTO(String id,
                                 String title,
                                 String description,
                                 int quantity,
                                 long price,
                                 String currencyCode,
                                 String imageUrl) {
}
