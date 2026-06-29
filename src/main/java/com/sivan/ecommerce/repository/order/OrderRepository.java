package com.sivan.ecommerce.repository.order;

import com.sivan.ecommerce.dto.order.OrderSummaryResponseDTO;
import com.sivan.ecommerce.entity.order.Order;
import com.sivan.ecommerce.entity.order.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            SELECT o
            FROM Order o
            JOIN FETCH o.orderItems oi
            JOIN FETCH oi.product
            WHERE o.customer.id = :customerId AND o.id = :orderId
            """)
    Optional<Order> findByCustomerIdAndOrderId(@Param("customerId") UUID customerId,
                                               @Param("orderId") UUID orderId);

    @Query(value = """
            SELECT new com.sivan.ecommerce.dto.order.OrderSummaryResponseDTO(
                        o.id,
                        o.status,
                        o.totalPrice,
                        o.shippingAddress,
                        o.createdAt)
            FROM Order o
            WHERE o.customer.id = :customerId AND
            (:status IS NULL OR o.status = :status)
            """,
            countQuery = """
                    SELECT COUNT(o)
                    FROM Order o
                    WHERE o.customer.id = :customerId AND
                    (:status IS NULL OR o.status = :status)
                    """)
    Page<OrderSummaryResponseDTO> findByFilters(@Param("customerId") UUID customerId,
                                                @Param("status") Status status,
                                                Pageable pageable);
}
