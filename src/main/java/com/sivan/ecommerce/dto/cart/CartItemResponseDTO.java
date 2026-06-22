package com.sivan.ecommerce.dto.cart;

import java.util.UUID;

public record CartItemResponseDTO(UUID productId,
                                  String productTitle,
                                  long price,
                                  boolean isAvailable,
                                  int quantity) {
}
