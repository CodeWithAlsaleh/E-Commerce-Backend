package com.sivan.ecommerce.controller.order;

import com.sivan.ecommerce.dto.order.OrderRequestDTO;
import com.sivan.ecommerce.dto.order.OrderResponseDTO;
import com.sivan.ecommerce.service.order.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
}
