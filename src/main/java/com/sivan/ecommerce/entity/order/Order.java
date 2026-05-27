package com.sivan.ecommerce.entity.order;

import com.sivan.ecommerce.entity.BaseEntity;
import com.sivan.ecommerce.entity.customer.Customer;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "order")
public class Order extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Column(name = "shipping_address", length = 512, nullable = false)
    private String shippingAddress;

    /*
     *   Note:
     *
     *   "orphanRemoval = true" means if you do "order.getOrderItems().remove(item)"
     *   Hibernate will automatically execute a DELETE SQL statement for that item!
     *
     *   "orphanRemoval = true" means that if a child entity is disconnected from its parent
     *   (removed from a collection or set to null), the framework automatically deletes it
     *   from the database. It ensures privately owned child objects cannot exist without
     *   their parent.
     * */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OrderItem> orderItems = new HashSet<>();

    public Order() {
    }

    public Order(Customer customer, Status status, long totalPrice, String shippingAddress) {
        this.customer = customer;
        this.status = status;
        this.totalPrice = totalPrice;
        this.shippingAddress = shippingAddress;
    }

    public void addOrderItem(OrderItem orderItem) {
        orderItems.add(orderItem);

        orderItem.setOrder(this);
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(long totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public Set<OrderItem> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(Set<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    @Override
    public String toString() {
        return "Order{" +
                "status=" + status +
                ", totalPrice=" + totalPrice +
                ", shippingAddress='" + shippingAddress + '\'' +
                '}' + super.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Order order)) return false;
        return Objects.equals(this.getId(), order.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.getId());
    }
}
