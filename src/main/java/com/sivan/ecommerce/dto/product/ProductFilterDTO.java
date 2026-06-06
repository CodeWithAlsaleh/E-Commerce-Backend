package com.sivan.ecommerce.dto.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ProductFilterDTO(@Size(min = 3, max = 255) String search,
                               @Min(0) Long minPrice,
                               @Min(0) Long maxPrice,
                               @Size(min = 3, max = 255) String category) {

    public ProductFilterDTO {
        search = search != null ? search.trim() : null;
        category = category != null ? category.trim() : null;
    }
}
