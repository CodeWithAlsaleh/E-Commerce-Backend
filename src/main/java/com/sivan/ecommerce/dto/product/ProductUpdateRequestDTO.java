package com.sivan.ecommerce.dto.product;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

public record ProductUpdateRequestDTO(@Nullable @Size(min = 3, max = 255) String title,
                                      @Nullable @Size(min = 40, max = 5000) String description,
                                      @Nullable @Min(0) Integer quantity,
                                      @Nullable @Min(0) Long price,
                                      @Nullable @Size(min = 3, max = 3) String currencyCode,
                                      @Nullable @URL @Size(max = 512)
                                      @Pattern(regexp = "^(https?://)[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+$",
                                              message = "URL contains invalid characters or spaces") String imageUrl) {

    public ProductUpdateRequestDTO {
        title = title != null ? title().trim() : null;
        description = description != null ? description.trim() : null;
        currencyCode = currencyCode != null ? currencyCode.trim().toUpperCase() : null;
        imageUrl = imageUrl != null ? imageUrl.trim() : null;
    }
}
