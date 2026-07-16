package com.sivan.ecommerce.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequestDTO(@NotBlank @Email @Size(max = 255) String email,
                             @NotBlank @Size(max = 255) String password) {

    public AuthRequestDTO {
        email = email != null ? email.trim().toLowerCase() : null;
    }
}
