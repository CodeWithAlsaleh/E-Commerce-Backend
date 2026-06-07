package com.sivan.ecommerce.dto.cart;

import java.util.UUID;

public record CartItemResponseDTO(UUID productId,
                                  String productTitle,
                                  int quantity) {
}
