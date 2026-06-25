package com.sivan.ecommerce.dto.order;

import java.util.UUID;

public record OrderItemResponseDTO(UUID productId,
                                   String productTitle,
                                   int quantity,
                                   long lockedPrice) {
}
