package com.sivan.ecommerce.entity.cart;

import com.sivan.ecommerce.entity.BaseEntity;
import com.sivan.ecommerce.entity.customer.Customer;
import jakarta.persistence.*;

@Entity
@Table(name = "cart")
public class Cart extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", unique = true, nullable = false)
    private Customer customer;

    public Cart() {
    }

    public Cart(Customer customer) {
        this.customer = customer;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    @Override
    public String toString() {
        return "Cart{}" + super.toString();
    }
}
