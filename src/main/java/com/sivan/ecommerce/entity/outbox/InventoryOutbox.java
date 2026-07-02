package com.sivan.ecommerce.entity.outbox;

import com.sivan.ecommerce.entity.BaseEntity;
import com.sivan.ecommerce.entity.order.Order;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "inventory_outbox")
public class InventoryOutbox extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true, nullable = false)
    Order order;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    public InventoryOutbox() {
    }

    public InventoryOutbox(Order order) {
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "InventoryOutbox{" +
                "status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InventoryOutbox inventoryOutbox)) return false;
        return Objects.equals(order.getId(), inventoryOutbox.order.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(order.getId());
    }
}
