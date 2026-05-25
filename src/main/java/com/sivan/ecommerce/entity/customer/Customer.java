package com.sivan.ecommerce.entity.customer;

import com.sivan.ecommerce.entity.BaseEntity;
import com.sivan.ecommerce.entity.role.Role;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "customer")
public class Customer extends BaseEntity {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "location")
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
    private Set<Role> roles;

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
        if (roles == null)
            roles = new HashSet<>();

        roles.add(role);

        role.addCustomer(this);
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
