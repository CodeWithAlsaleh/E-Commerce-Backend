package com.sivan.ecommerce.mapper.cart;

import com.sivan.ecommerce.dto.cart.CartItemRequestDTO;
import com.sivan.ecommerce.dto.cart.CartItemResponseDTO;
import com.sivan.ecommerce.entity.cart.CartItem;

public class CartItemMapper {

    private CartItemMapper() {
        // Prevent instantiation
    }

    public static CartItem mapCartItemRequestToCartItem(CartItemRequestDTO cartItemRequestDTO) {
        return new CartItem(cartItemRequestDTO.quantity());
    }

    public static CartItemResponseDTO mapCartItemToCartItemResponse(CartItem cartItem) {
        return new CartItemResponseDTO(
                cartItem.getProduct().getId(),
                cartItem.getProduct().getTitle(),
                cartItem.getQuantity()
        );
    }
}
