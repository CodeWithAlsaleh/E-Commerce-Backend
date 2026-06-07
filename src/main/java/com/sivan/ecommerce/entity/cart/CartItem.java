package com.sivan.ecommerce.entity.cart;

import com.sivan.ecommerce.entity.BaseEntity;
import com.sivan.ecommerce.entity.product.Product;
import jakarta.persistence.*;

import java.util.Objects;

/*
 *   The Rule of Thumb: Only make a relationship "Bidirectional" if the "parent" actually
 *   needs to know about the "children" for business logic or UI display.
 *
 *   Cart to CartItem: Keep this bidirectional. When a customer opens their cart
 *   you absolutely need to fetch the Cart and display all its CartItems.
 *
 *   Product to CartItem: Remove this. (Delete Set<CartItem> cartItems from the Product class)
 *   and make the relationship "Unidirectional"
 *
 *   Why: A Product does not care whose cart it is in. It only cares about its own details.
 * */

@Entity
@Table(name = "cart_item")
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    public CartItem() {
    }

    public CartItem(int quantity) {
        this.quantity = quantity;
    }

    public Cart getCart() {
        return cart;
    }

    public void setCart(Cart cart) {
        this.cart = cart;
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

    @Override
    public String toString() {
        return "CartItem{" +
                "quantity=" + quantity +
                '}' + super.toString();
    }

    /*
     *   When Hibernate gives you a CartProxy or ProductProxy, the only piece of real data
     *   inside that hologram is the ID. Because of this, calling cartProxy.getId() does
     *   not trigger a database query.
     * */

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CartItem cartItem)) return false;
        return Objects.equals(cart.getId(), cartItem.cart.getId()) &&
                Objects.equals(product.getId(), cartItem.product.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(cart.getId(), product.getId());
    }
}
