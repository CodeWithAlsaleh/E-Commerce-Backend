package com.sivan.ecommerce.service.cart;

import com.sivan.ecommerce.dto.cart.CartItemRequestDTO;
import com.sivan.ecommerce.dto.cart.CartItemResponseDTO;
import com.sivan.ecommerce.dto.cart.CartItemUpdateDTO;

import java.util.List;
import java.util.UUID;

public interface CartItemService {

    CartItemResponseDTO createCartItem(CartItemRequestDTO cartItemRequestDTO);

    List<CartItemResponseDTO> getCartItems();

    CartItemResponseDTO updateCartItem(UUID productId, CartItemUpdateDTO cartItemUpdateDTO);

    void deleteCartItem(UUID productId);
}
