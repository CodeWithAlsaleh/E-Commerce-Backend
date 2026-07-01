package com.sivan.ecommerce.service.order;

import com.sivan.ecommerce.dto.order.OrderFilterDTO;
import com.sivan.ecommerce.dto.order.OrderRequestDTO;
import com.sivan.ecommerce.dto.order.OrderResponseDTO;
import com.sivan.ecommerce.dto.order.OrderSummaryResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    OrderResponseDTO placeOrder(String idempotencyKey, OrderRequestDTO orderRequestDTO);

    Page<OrderSummaryResponseDTO> getOrders(OrderFilterDTO orderFilterDTO, Pageable pageable);

    OrderResponseDTO getOrder(UUID orderId);

    OrderResponseDTO cancelOrder(UUID orderId);
}
