package com.sivan.ecommerce.repository.order;

import com.sivan.ecommerce.entity.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
