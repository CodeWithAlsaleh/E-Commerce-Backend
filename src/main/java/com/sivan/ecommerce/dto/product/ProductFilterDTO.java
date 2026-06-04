package com.sivan.ecommerce.dto.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ProductFilterDTO(@Size(min = 3, max = 255) String title,
                               @Min(0) Long minPrice,
                               @Min(0) Long maxPrice,
                               @Size(max = 255) String category) {

    public ProductFilterDTO {
        title = title != null ? title.trim() : null;
        category = category != null ? category.trim() : null;
    }
}
