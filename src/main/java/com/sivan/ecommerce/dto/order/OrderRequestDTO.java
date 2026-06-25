package com.sivan.ecommerce.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderRequestDTO(@NotBlank @Size(max = 512) String shippingAddress) {

    public OrderRequestDTO {
        shippingAddress = shippingAddress != null ? shippingAddress.trim() : null;
    }
}
