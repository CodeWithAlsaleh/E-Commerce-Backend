package com.sivan.ecommerce.service.order;

import com.sivan.ecommerce.dto.order.OrderRequestDTO;
import com.sivan.ecommerce.dto.order.OrderResponseDTO;

public interface OrderService {

    OrderResponseDTO placeOrder(String idempotencyKey, OrderRequestDTO orderRequestDTO);
}
