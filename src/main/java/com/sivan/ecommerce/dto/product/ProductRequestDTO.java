package com.sivan.ecommerce.dto.product;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record ProductRequestDTO(@NotBlank @Size(min = 3, max = 255) String title,
                                @Nullable @Size(min = 40, max = 5000) String description,
                                @NotNull @Min(0) Integer quantity,
                                @NotNull @Min(0) Long price,
                                @NotBlank @Size(min = 3, max = 3) String currencyCode,
                                @NotBlank @URL @Size(max = 512) String imageUrl) {
}
