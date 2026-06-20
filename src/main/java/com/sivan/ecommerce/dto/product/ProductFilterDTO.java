package com.sivan.ecommerce.dto.product;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ProductFilterDTO(@Nullable @Size(min = 3, max = 255) String search,
                               @Nullable @Min(0) Long minPrice,
                               @Nullable @Min(0) Long maxPrice,
                               @Nullable @Size(min = 3, max = 255) String category) {

    public ProductFilterDTO {
        search = search != null ? search.trim() : null;
        category = category != null ? category.trim() : null;
    }
}
