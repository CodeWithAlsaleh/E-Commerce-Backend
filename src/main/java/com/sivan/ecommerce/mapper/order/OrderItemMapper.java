package com.sivan.ecommerce.mapper.order;

import com.sivan.ecommerce.dto.order.OrderItemResponseDTO;
import com.sivan.ecommerce.entity.order.OrderItem;

import java.util.HashSet;
import java.util.Set;

public class OrderItemMapper {

    private OrderItemMapper() {
        // Prevent instantiation
    }

    public static OrderItemResponseDTO mapOrderItemToOrderItemResponse(OrderItem orderItem) {
        return new OrderItemResponseDTO(
                orderItem.getProduct().getId(),
                orderItem.getProduct().getTitle(),
                orderItem.getQuantity(),
                orderItem.getLockedPrice()
        );
    }

    public static Set<OrderItemResponseDTO> mapOrderItemsToOrderItemsResponse(Set<OrderItem> orderItems) {
        Set<OrderItemResponseDTO> st = new HashSet<>();

        for (var orderItem : orderItems)
            st.add(mapOrderItemToOrderItemResponse(orderItem));

        return st;
    }
}
