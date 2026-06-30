package com.sivan.ecommerce.dto.order;

import com.sivan.ecommerce.entity.order.Status;

import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponseDTO(UUID id,
                                      Status status,
                                      long totalPrice,
                                      String shippingAddress,
                                      Instant createdAt,
                                      Instant updatedAt) {
}
