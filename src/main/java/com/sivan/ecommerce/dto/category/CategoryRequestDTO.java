package com.sivan.ecommerce.dto.category;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequestDTO(@NotBlank @Size(min = 3, max = 255) String title,
                                 @Nullable @Size(min = 40, max = 5000) String description) {

    public CategoryRequestDTO {
        title = title != null ? title.trim() : null;
        description = description != null ? description.trim() : null;
    }
}
