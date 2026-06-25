package com.sivan.ecommerce.dto.order;

import com.sivan.ecommerce.entity.order.Status;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record OrderResponseDTO(UUID id,
                               Status status,
                               long totalPrice,
                               String shippingAddress,
                               LocalDateTime createdAt,
                               Set<OrderItemResponseDTO> orderItems) {
}
