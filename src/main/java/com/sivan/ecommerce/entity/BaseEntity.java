package com.sivan.ecommerce.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/*
 *   Yes, when using an automatic UUID generation strategy, Hibernate assigns the
 *   ID in the application layer before the record is inserted into the database.
 *
 *   Hibernate usually assigns the UUID:
 *   => when the entity becomes managed (persist)
 *   => sometimes earlier depending on lifecycle
 *
 *   But NOT necessarily immediately after: "Entity entity = new Entity()"
 *
 *   This is a major difference compared to using an Integer with an IDENTITY strategy
 *   where the ID remains null until the database actually performs the insertion.
 * */

/*
 *   ===================== equals() & hashCode() Strategies =====================
 *
 *   We use 3 strategies across our entities. Each is chosen based on whether
 *   the entity has a natural business key, a composite FK identity, or neither.
 *
 *   -------------------------------------------------------------------
 *   Strategy 1: Natural Business Key
 *   -------------------------------------------------------------------
 *   Used in: Customer (email), Role (roleName), Category (title)
 *
 *   When the entity has a column that is UNIQUE and immutable (or rarely changes),
 *   use it as the identity. This is the gold standard recommended by Vlad Mihalcea.
 *
 *   - equals():  compare the business key field (e.g., email)
 *   - hashCode(): hash the same business key field
 *   - Pros: works even before persist, semantically meaningful
 *   - Use 'instanceof' (not getClass()) to handle Hibernate proxies
 *
 *   -------------------------------------------------------------------
 *   Strategy 2: Composite FK Identity
 *   -------------------------------------------------------------------
 *   Used in: CartItem (cart.id + product.id), OrderItem (order.id + product.id)
 *
 *   When the entity has no unique business key of its own, but the combination
 *   of its parent FKs forms a unique identity (enforced by a UNIQUE constraint
 *   in the DB), use those FK IDs.
 *
 *   - equals():  compare parent1.getId() AND parent2.getId()
 *   - hashCode(): Objects.hash(parent1.getId(), parent2.getId())
 *   - Note: calling .getId() on a Hibernate proxy does NOT trigger lazy loading,
 *     because the proxy already holds the ID internally
 *
 *   -------------------------------------------------------------------
 *   Strategy 3: Primary Key (UUID) Identity
 *   -------------------------------------------------------------------
 *   Used in: Product, Order, Cart
 *
 *   When the entity has no natural business key and no composite FK uniqueness,
 *   fall back to the entity's own ID. This is safe with @UuidGenerator because
 *   the UUID is assigned at persist-time (before the INSERT), so hashCode
 *   remains stable across the entity lifecycle.
 *
 *   - equals():  getId() != null && getId().equals(other.getId())
 *   - hashCode(): Objects.hashCode(getId())
 *   - Note: avoid returning a constant hashCode (e.g., Hibernate.getClass(this).hashCode())
 *     as it degrades HashSet/HashMap lookups from O(1) to O(n)
 *
 *   ===================== Why 'instanceof' over 'getClass()' =====================
 *
 *   Hibernate uses Proxy subclasses for lazy loading. A proxy's getClass()
 *   returns e.g., Customer$HibernateProxy, which != Customer.class.
 *   Using 'instanceof' ensures equals() works correctly with proxies.
 *   ============================================================================
 * */

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    private void setId(UUID id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "BaseEntity{" +
                "id=" + id +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
