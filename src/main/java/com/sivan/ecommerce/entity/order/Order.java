package com.sivan.ecommerce.entity.order;

import com.sivan.ecommerce.entity.BaseEntity;
import com.sivan.ecommerce.entity.customer.Customer;
import jakarta.persistence.*;
import org.hibernate.Hibernate;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "orders")
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

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

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
        /*
         *   NOTE: We need to set the product on orderItem before calling this method
         *
         *   QUESTION: Why we set the order before adding "orderItem" to the set ?
         *
         *   ANSWER: "Cuz hashCode & equals" for "OrderItem" entity depends on
         *           both "product & order" to be set in order to work fine.
         * */
        orderItem.setOrder(this);

        orderItems.add(orderItem);
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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
                ", idempotencyKey='" + idempotencyKey + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;

        /*
         *   Using Hibernate.getClass() instead of instanceof or standard .getClass() is the absolute
         *   gold standard for JPA entity equality when you do not have a unique business key.
         *
         *   It perfectly strips away the Hibernate Proxy layer to compare the actual underlying types.
         * */

        if (Hibernate.getClass(this) != Hibernate.getClass(o))
            return false;

        Order that = (Order) o;

        return this.getId() != null && this.getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        /*
         *   Before (constant hashCode, O(N) lookups instead of O(1)):
         *   return Hibernate.getClass(this).hashCode();
         * */

        // After (ID-based, O(1) lookups):
        return Objects.hashCode(getId());
    }
}
