package com.sivan.ecommerce.repository.product;

import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import com.sivan.ecommerce.entity.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query(
            value = """
                      SELECT
                          BIN_TO_UUID(p.id) AS id,
                          p.title AS title,
                          p.description AS description,
                          p.quantity AS quantity,
                          p.price AS price,
                          p.currency_code AS currencyCode,
                          p.image_url AS imageUrl
                      FROM product p
                      WHERE p.is_active = true
                      AND (:minPrice IS NULL OR p.price >= :minPrice)
                      AND (:maxPrice IS NULL OR p.price <= :maxPrice)
                      AND (:search IS NULL OR MATCH(p.title, p.description) AGAINST(:search IN BOOLEAN MODE))
                      AND (:category IS NULL OR EXISTS (
                                                  SELECT 1
                                                  FROM product_category pc
                                                  INNER JOIN category c ON c.id = pc.category_id
                                                  WHERE pc.product_id = p.id
                                                  AND c.is_active = true
                                                  AND c.title = :category))
                    """,
            countQuery = """
                      SELECT COUNT(*)
                      FROM product p
                      WHERE p.is_active = true
                      AND (:minPrice IS NULL OR p.price >= :minPrice)
                      AND (:maxPrice IS NULL OR p.price <= :maxPrice)
                      AND (:search IS NULL OR MATCH(p.title, p.description) AGAINST(:search IN BOOLEAN MODE))
                      AND (:category IS NULL OR EXISTS (
                                                  SELECT 1
                                                  FROM product_category pc
                                                  INNER JOIN category c ON c.id = pc.category_id
                                                  WHERE pc.product_id = p.id
                                                  AND c.is_active = true
                                                  AND c.title = :category))
                    """,
            nativeQuery = true)
    Page<ProductResponseDTO> findByFilters(@Param("search") String search,
                                           @Param("minPrice") Long minPrice,
                                           @Param("maxPrice") Long maxPrice,
                                           @Param("category") String category,
                                           Pageable pageable);

    @Modifying
    @Query("""
            UPDATE Product p
            SET p.quantity = p.quantity + :stock
            WHERE p.id = :productId
            """)
    void restoreStock(@Param("productId") UUID productId, @Param("stock") int stock);
}
