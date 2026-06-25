package com.sivan.ecommerce.mapper.order;

import com.sivan.ecommerce.dto.order.OrderResponseDTO;
import com.sivan.ecommerce.entity.order.Order;

public class OrderMapper {

    private OrderMapper() {
        // Prevent instantiation
    }

    public static OrderResponseDTO mapOrderToOrderResponse(Order order) {
        return new OrderResponseDTO(
                order.getId(),
                order.getStatus(),
                order.getTotalPrice(),
                order.getShippingAddress(),
                order.getCreatedAt(),
                OrderItemMapper.mapOrderItemsToOrderItemsResponse(order.getOrderItems())
        );
    }
}
