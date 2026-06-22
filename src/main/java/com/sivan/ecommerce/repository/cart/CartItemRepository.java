package com.sivan.ecommerce.repository.cart;

import com.sivan.ecommerce.entity.cart.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    @Query("""
            SELECT ct
            FROM CartItem ct
            WHERE ct.product.id = :productId AND ct.cart.id = :cartId
            """)
    Optional<CartItem> findCartItem(@Param("productId") UUID productId,
                                    @Param("cartId") UUID cartId);

    @Query("""
             SELECT ct
             FROM CartItem ct
             JOIN FETCH ct.product p
             WHERE ct.cart.id = :cartId
            """)
    List<CartItem> findAllByCartIdWithProduct(@Param("cartId") UUID cartId);
}
