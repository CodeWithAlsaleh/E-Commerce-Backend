package com.sivan.ecommerce.controller.cart;

import com.sivan.ecommerce.dto.cart.CartItemRequestDTO;
import com.sivan.ecommerce.dto.cart.CartItemResponseDTO;
import com.sivan.ecommerce.service.cart.CartItemService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cart")
public class CartRestController {

    private final CartItemService cartItemService;

    @Autowired
    public CartRestController(CartItemService cartItemService) {
        this.cartItemService = cartItemService;
    }

    @PostMapping("/items")
    public ResponseEntity<CartItemResponseDTO> createCartItem(@RequestBody @Valid CartItemRequestDTO cartItemRequestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartItemService.createCartItem(cartItemRequestDTO));
    }

    @GetMapping
    public ResponseEntity<List<CartItemResponseDTO>> getCartItems() {
        return ResponseEntity.ok(cartItemService.getCartItems());
    }
}
