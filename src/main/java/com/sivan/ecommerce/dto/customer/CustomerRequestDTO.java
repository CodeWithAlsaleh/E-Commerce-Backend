package com.sivan.ecommerce.dto.customer;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequestDTO(@NotBlank @Size(min = 1, max = 100) String firstName,
                                 @NotBlank @Size(min = 1, max = 100) String lastName,
                                 @NotBlank @Email @Size(max = 255) String email,
                                 @Nullable @Size(min = 2, max = 255) String location,
                                 @NotBlank @Size(min = 12, max = 255) String password) {

    /*
     *   This is a Compact Constructor. It runs immediately when Jackson creates the record.
     *
     *   Its primary purpose is to allow developers to validate or normalize data before it
     *   is permanently assigned to the record's immutable fields.
     *
     *   You can intercept and trim the strings exactly when the DTO is created
     *   which happens before the "@Valid" annotations are checked!
     * */
    public CustomerRequestDTO {
        // Sanitize only the specific fields that need it!
        firstName = firstName != null ? firstName.trim() : null;
        lastName = lastName != null ? lastName.trim() : null;
        email = email != null ? email.trim().toLowerCase() : null;
        location = location != null ? location.trim() : null;
    }
}
