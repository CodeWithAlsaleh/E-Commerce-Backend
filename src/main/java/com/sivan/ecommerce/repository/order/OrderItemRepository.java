package com.sivan.ecommerce.repository.order;

import com.sivan.ecommerce.entity.order.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Query("""
            SELECT oi
            FROM OrderItem oi
            JOIN FETCH oi.product
            WHERE oi.order.id = :orderId
            """)
    List<OrderItem> findByOrderId(@Param("orderId") UUID orderId);
}
