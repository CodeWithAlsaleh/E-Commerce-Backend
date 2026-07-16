package com.sivan.ecommerce.entity.token;

import com.sivan.ecommerce.entity.BaseEntity;
import com.sivan.ecommerce.entity.customer.Customer;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "refresh_token")
public class RefreshToken extends BaseEntity {

    @Column(name = "token", unique = true, nullable = false)
    private String token;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    /*
     *  Each refresh token belongs to exactly one customer.
     *  When a customer logs in again, their old refresh token is deleted
     *  and a new one is created, so only one active token exists per customer.
     *
     *  We usually use @ManyToOne for Refresh Tokens because of Multiple Devices.
     *
     *  Q: If a customer only ever has one active refresh token at a time
     *     (because we delete the old ones on login), shouldn't it be a
     *     @OneToOne relationship ?
     *
     *  ANS: Technically, yes. If you strictly enforce that a user can only ever
     *       have a single active session (e.g., logging in on their phone logs
     *       them out of their web browser), then a @OneToOne relationship is
     *       perfectly accurate.
     *
     *  Right now cuz we deleteAllByCustomer() on a new /login, we can change it
     *  to @OneToOne, but for future support we will leave it @ManyToOne
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    public RefreshToken() {
    }

    public RefreshToken(String token, Instant expiryDate, Customer customer) {
        this.token = token;
        this.expiryDate = expiryDate;
        this.customer = customer;
    }

    public boolean isExpired() {
        return expiryDate.isBefore(Instant.now());
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Instant expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    @Override
    public String toString() {
        return "RefreshToken{" +
                "expiryDate=" + expiryDate +
                '}' + super.toString();
    }

    /*
     *  Using the token string as the natural business key (Strategy 1),
     *  since it is unique and immutable.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RefreshToken that)) return false;
        return Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(token);
    }
}
