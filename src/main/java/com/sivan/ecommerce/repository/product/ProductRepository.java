package com.sivan.ecommerce.repository.product;

import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import com.sivan.ecommerce.entity.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<ProductResponseDTO> findByIdAndIsActive(UUID productId, boolean isActive);

    @Query(
            value = """
                        SELECT
                            p.id AS id,
                            p.title AS title,
                            p.description AS description,
                            p.quantity AS quantity,
                            p.price AS price,
                            p.currency_code AS currencyCode,
                            p.image_url AS imageUrl
                        FROM `product` AS p
                        LEFT JOIN `product_category` AS pc ON p.id = pc.product_id
                        LEFT JOIN `category` AS c ON pc.category_id = c.id
                        WHERE p.is_active = true
                        AND (c.is_active = true OR c.id IS NULL)
                        AND (:minPrice IS NULL OR p.price >= :minPrice)
                        AND (:maxPrice IS NULL OR p.price <= :maxPrice)
                        AND (:category IS NULL OR c.title = :category)
                        AND (:title IS NULL OR MATCH(p.title) AGAINST (:title IN BOOLEAN MODE))
                    """,
            countQuery = """
                        SELECT COUNT(p.id)
                        FROM `product` AS p
                        LEFT JOIN `product_category` AS pc ON p.id = pc.product_id
                        LEFT JOIN `category` AS c ON pc.category_id = c.id
                        WHERE p.is_active = true
                        AND (c.is_active = true OR c.id IS NULL)
                        AND (:minPrice IS NULL OR p.price >= :minPrice)
                        AND (:maxPrice IS NULL OR p.price <= :maxPrice)
                        AND (:category IS NULL OR c.title = :category)
                        AND (:title IS NULL OR MATCH(p.title) AGAINST (:title IN BOOLEAN MODE))
                    """,
            nativeQuery = true
    )
    Page<ProductResponseDTO> findByFilters(@Param("title") String title,
                                           @Param("minPrice") Long minPrice,
                                           @Param("maxPrice") Long maxPrice,
                                           @Param("category") String category,
                                           Pageable pageable);
}
