package com.sivan.ecommerce.entity.cart;

import com.sivan.ecommerce.entity.BaseEntity;
import com.sivan.ecommerce.entity.customer.Customer;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "cart")
public class Cart extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", unique = true, nullable = false)
    private Customer customer;

    /*
     *   Note:
     *
     *   "orphanRemoval = true" means if you do "cart.getCartItems().remove(item)"
     *   Hibernate will automatically execute a DELETE SQL statement for that item!
     *
     *   "orphanRemoval = true" means that if a child entity is disconnected from its parent
     *   (removed from a collection or set to null), the framework automatically deletes it
     *   from the database. It ensures privately owned child objects cannot exist without
     *   their parent.
     * */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CartItem> cartItems = new HashSet<>();

    public Cart() {
    }

    public Cart(Customer customer) {
        this.customer = customer;
    }

    public void addCartItem(CartItem cartItem) {
        cartItems.add(cartItem);

        cartItem.setCart(this);
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Set<CartItem> getCartItems() {
        return cartItems;
    }

    public void setCartItems(Set<CartItem> cartItems) {
        this.cartItems = cartItems;
    }

    @Override
    public String toString() {
        return "Cart{}" + super.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Cart cart)) return false;
        return Objects.equals(customer.getId(), cart.customer.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(customer.getId());
    }
}
