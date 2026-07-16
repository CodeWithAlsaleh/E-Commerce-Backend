package com.sivan.ecommerce.service.auth;

import com.sivan.ecommerce.dto.auth.AuthRequestDTO;
import com.sivan.ecommerce.dto.auth.AuthResponseDTO;
import com.sivan.ecommerce.dto.auth.RefreshRequestDTO;

public interface AuthService {

    AuthResponseDTO login(AuthRequestDTO authRequestDTO);

    AuthResponseDTO refresh(RefreshRequestDTO refreshRequestDTO);
}
