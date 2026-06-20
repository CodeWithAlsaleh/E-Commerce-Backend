package com.sivan.ecommerce.entity.customer;

import com.sivan.ecommerce.entity.BaseEntity;
import com.sivan.ecommerce.entity.cart.Cart;
import com.sivan.ecommerce.entity.order.Order;
import com.sivan.ecommerce.entity.role.Role;
import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "customer")
public class Customer extends BaseEntity {

    @Column(name = "first_name", length = 100, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 100, nullable = false)
    private String lastName;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "location", length = 512)
    private String location;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @ManyToMany(
            fetch = FetchType.LAZY,
            cascade = {
                    CascadeType.MERGE,
                    CascadeType.DETACH,
                    CascadeType.PERSIST,
                    CascadeType.REFRESH
            }
    )
    @JoinTable(
            name = "customer_role",
            joinColumns = @JoinColumn(name = "customer_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    /*
     *   Specifying FetchType.LAZY for the non-owning side of the @OneToOne association will not affect
     *   the loading. The related entity will still be loaded as if the FetchType.EAGER is defined.
     *
     *   TODO: This can cuz (N + 1) problem, so try to check it in the future
     * */
    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL)
    private Cart cart;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "customer", cascade = {
            CascadeType.MERGE,
            CascadeType.DETACH,
            CascadeType.PERSIST,
            CascadeType.REFRESH
    })
    private List<Order> orders = new ArrayList<>();

    public Customer() {
    }

    public Customer(String firstName, String lastName, String email, String location, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.location = location;
        this.password = password;
    }

    public void addRole(Role role) {
        roles.add(role);

        role.addCustomer(this);
    }

    public void addOrder(Order order) {
        orders.add(order);

        order.setCustomer(this);
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public Cart getCart() {
        return cart;
    }

    public void setCart(Cart cart) {
        this.cart = cart;

        if (cart != null)
            cart.setCustomer(this);
    }

    public List<Order> getOrders() {
        return orders;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }

    @Override
    public String toString() {
        return "Customer{" +
                "firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", location='" + location + '\'' +
                ", isActive=" + isActive +
                '}' + super.toString();
    }

    /*
     *  Why use 'instanceof' and not 'getClass()'?
     *
     *  In JPA, Hibernate uses "Proxies" (subclasses created at runtime) to handle
     *  Lazy Loading. A proxy's class (e.g., User$HibernateProxy) will not
     *  equal User.class, causing 'getClass()' to return false even if the
     *  identities match.
     *
     *  Since the Proxy extends the Entity, 'instanceof' returns true, ensuring
     *  equals() works correctly even when the entity is lazily loaded.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Customer customer)) return false;
        return Objects.equals(email, customer.email);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(email);
    }
}
