package com.sivan.ecommerce.repository.cart;

import com.sivan.ecommerce.entity.cart.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
}
