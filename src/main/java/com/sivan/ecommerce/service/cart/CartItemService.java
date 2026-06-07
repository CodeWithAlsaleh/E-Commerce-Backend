package com.sivan.ecommerce.service.cart;

import com.sivan.ecommerce.dto.cart.CartItemRequestDTO;
import com.sivan.ecommerce.dto.cart.CartItemResponseDTO;

public interface CartItemService {

    CartItemResponseDTO createCartItem(CartItemRequestDTO cartItemRequestDTO);
}
