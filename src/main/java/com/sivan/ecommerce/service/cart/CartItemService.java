package com.sivan.ecommerce.service.cart;

import com.sivan.ecommerce.dto.cart.CartItemRequestDTO;
import com.sivan.ecommerce.dto.cart.CartItemResponseDTO;

import java.util.List;

public interface CartItemService {

    CartItemResponseDTO createCartItem(CartItemRequestDTO cartItemRequestDTO);

    List<CartItemResponseDTO> getCartItems();
}
