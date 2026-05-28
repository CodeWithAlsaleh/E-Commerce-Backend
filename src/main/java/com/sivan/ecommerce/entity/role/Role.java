package com.sivan.ecommerce.entity.role;

import com.sivan.ecommerce.entity.BaseEntity;
import com.sivan.ecommerce.entity.customer.Customer;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "role")
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false)
    private RoleName roleName;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "roles")
    private Set<Customer> customers = new HashSet<>();

    public Role() {
    }

    public Role(RoleName roleName) {
        this.roleName = roleName;
    }

    public void addCustomer(Customer customer) {
        customers.add(customer);
    }

    public RoleName getRoleName() {
        return roleName;
    }

    public void setRoleName(RoleName roleName) {
        this.roleName = roleName;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Set<Customer> getCustomers() {
        return customers;
    }

    public void setCustomers(Set<Customer> customers) {
        this.customers = customers;
    }

    @Override
    public String toString() {
        return "Role{" +
                "roleName=" + roleName +
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
        if (!(o instanceof Role role)) return false;
        return roleName == role.roleName;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(roleName);
    }
}
