package com.sivan.ecommerce.controller.order;

import com.sivan.ecommerce.dto.order.OrderFilterDTO;
import com.sivan.ecommerce.dto.order.OrderRequestDTO;
import com.sivan.ecommerce.dto.order.OrderResponseDTO;
import com.sivan.ecommerce.dto.order.OrderSummaryResponseDTO;
import com.sivan.ecommerce.service.order.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@Validated // CRITICAL: This enables @NotBlank to work on the String parameter!
public class OrderRestController {

    private final OrderService orderService;

    public OrderRestController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponseDTO> placeOrder(@RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
                                                       @RequestBody @Valid OrderRequestDTO orderRequestDTO) {

        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(idempotencyKey, orderRequestDTO));
    }

    @GetMapping
    public ResponseEntity<Page<OrderSummaryResponseDTO>> getOrders(OrderFilterDTO orderFilterDTO, Pageable pageable) {
        return ResponseEntity.ok().body(orderService.getOrders(orderFilterDTO, pageable));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponseDTO> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok().body(orderService.getOrder(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponseDTO> cancelOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok().body(orderService.cancelOrder(orderId));
    }
}
