package com.sivan.ecommerce.repository.order;

import com.sivan.ecommerce.entity.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("""
            SELECT o
            FROM Order o
            JOIN FETCH o.orderItems
            WHERE o.idempotencyKey = :idempotencyKey
            """)
    Optional<Order> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
