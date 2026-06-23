package com.sivan.ecommerce.dto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemUpdateDTO(@NotNull @Min(1) Integer quantity) {
}
