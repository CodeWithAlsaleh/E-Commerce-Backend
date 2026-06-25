package com.sivan.ecommerce.entity.product;

import com.sivan.ecommerce.entity.BaseEntity;
import com.sivan.ecommerce.entity.category.Category;
import jakarta.persistence.*;
import org.hibernate.Hibernate;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    /*
     *   By default, JPA maps Java String to VARCHAR(255). If you try to save a 500-character
     *   product description right now, Hibernate will throw an error or truncate it.
     *
     *   So we need to use (columnDefinition = "TEXT")
     * */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "image_url", length = 512, nullable = false)
    private String imageUrl;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "products")
    private Set<Category> categories = new HashSet<>();

    public Product() {
    }

    public Product(String title, String description, int quantity, long price, String currencyCode, String imageUrl) {
        this.title = title;
        this.description = description;
        this.quantity = quantity;
        this.price = price;
        this.currencyCode = currencyCode;
        this.imageUrl = imageUrl;
    }

    public void addCategory(Category category) {
        categories.add(category);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public void setCategories(Set<Category> categories) {
        this.categories = categories;
    }

    @Override
    public String toString() {
        return "Product{" +
                "title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", currencyCode='" + currencyCode + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", version=" + version +
                ", isActive=" + isActive +
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

        Product that = (Product) o;

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
