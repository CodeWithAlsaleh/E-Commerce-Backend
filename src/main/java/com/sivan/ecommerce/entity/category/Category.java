package com.sivan.ecommerce.entity.category;

import com.sivan.ecommerce.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "category")
public class Category extends BaseEntity {

    @Column(name = "title", unique = true, nullable = false)
    private String title;

    /*
     *   By default, JPA maps Java String to VARCHAR(255). If you try to save a 500-character
     *   product description right now, Hibernate will throw an error or truncate it.
     *
     *   So we need to use (columnDefinition = "TEXT")
     * */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public Category() {
    }

    public Category(String title, String description) {
        this.title = title;
        this.description = description;
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

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    @Override
    public String toString() {
        return "Category{" +
                "title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", isActive=" + isActive +
                '}' + super.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Category category)) return false;
        return Objects.equals(title, category.title);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(title);
    }
}
