package com.sivan.ecommerce.entity.order;

import com.sivan.ecommerce.entity.BaseEntity;
import com.sivan.ecommerce.entity.product.Product;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "order_item")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "locked_price", nullable = false)
    private long lockedPrice;

    public OrderItem() {
    }

    public OrderItem(Order order, Product product, int quantity, long lockedPrice) {
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.lockedPrice = lockedPrice;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getLockedPrice() {
        return lockedPrice;
    }

    public void setLockedPrice(long lockedPrice) {
        this.lockedPrice = lockedPrice;
    }

    @Override
    public String toString() {
        return "OrderItem{" +
                "quantity=" + quantity +
                ", lockedPrice=" + lockedPrice +
                '}' + super.toString();
    }

    /*
     *   When Hibernate gives you a OrderProxy or ProductProxy, the only piece of real data
     *   inside that hologram is the ID. Because of this, calling orderProxy.getId() does
     *   not trigger a database query.
     * */

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OrderItem orderItem)) return false;
        return Objects.equals(order.getId(), orderItem.order.getId()) &&
                Objects.equals(product.getId(), orderItem.product.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(order.getId(), product.getId());
    }
}
