package com.sivan.ecommerce.dto.order;

import com.sivan.ecommerce.entity.order.Status;
import jakarta.annotation.Nullable;

public record OrderFilterDTO(@Nullable Status status) {
}
