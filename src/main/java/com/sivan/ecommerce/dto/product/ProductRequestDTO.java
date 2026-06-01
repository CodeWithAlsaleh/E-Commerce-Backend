package com.sivan.ecommerce.dto.product;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

public record ProductRequestDTO(@NotBlank @Size(min = 3, max = 255) String title,
                                @Nullable @Size(min = 40, max = 5000) String description,
                                @NotNull @Min(0) Integer quantity,
                                @NotNull @Min(0) Long price,
                                @NotBlank @Size(min = 3, max = 3) String currencyCode,
                                @NotBlank @URL @Size(max = 512)
                                @Pattern(regexp = "^(https?://)[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+$",
                                        message = "URL contains invalid characters or spaces") String imageUrl) {

    /*
     *   This is a Compact Constructor. It runs immediately when Jackson creates the record.
     *
     *   Its primary purpose is to allow developers to validate or normalize data before it
     *   is permanently assigned to the record's immutable fields.
     *
     *   You can intercept and trim the strings exactly when the DTO is created
     *   which happens before the "@Valid" annotations are checked!
     * */
    public ProductRequestDTO {
        // Sanitize only the specific fields that need it!
        title = title != null ? title.trim() : null;
        description = description != null ? description.trim() : null;
        currencyCode = currencyCode != null ? currencyCode.trim() : null;
        imageUrl = imageUrl != null ? imageUrl.trim() : null;
    }
}
