package com.sivan.ecommerce.repository.cart;

import com.sivan.ecommerce.entity.cart.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
}
