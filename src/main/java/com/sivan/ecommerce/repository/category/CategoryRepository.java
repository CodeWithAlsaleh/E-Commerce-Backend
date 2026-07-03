package com.sivan.ecommerce.repository.category;

import com.sivan.ecommerce.entity.category.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByTitle(String title);
}
