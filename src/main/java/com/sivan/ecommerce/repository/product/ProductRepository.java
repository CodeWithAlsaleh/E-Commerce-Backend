package com.sivan.ecommerce.repository.product;

import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import com.sivan.ecommerce.entity.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<ProductResponseDTO> findByIdAndIsActive(UUID productId, boolean isActive);
}
