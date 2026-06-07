package com.sivan.ecommerce.dto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CartItemRequestDTO(@NotNull UUID productId,
                                 @NotNull @Min(1) Integer quantity) {
}
