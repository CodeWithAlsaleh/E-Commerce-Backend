package com.sivan.ecommerce.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequestDTO(@NotBlank String refreshToken) {
}
