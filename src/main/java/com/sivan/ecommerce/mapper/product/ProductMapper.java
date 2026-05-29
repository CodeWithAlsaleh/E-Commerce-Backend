package com.sivan.ecommerce.mapper.product;

import com.sivan.ecommerce.dto.product.ProductRequestDTO;
import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import com.sivan.ecommerce.entity.product.Product;

public class ProductMapper {

    private ProductMapper() {
        // Prevent instantiation
    }

    public static Product mapProductRequestToProduct(ProductRequestDTO productRequestDTO) {
        return new Product(
                productRequestDTO.title().trim(),
                productRequestDTO.description() != null ? productRequestDTO.description().trim() : null,
                productRequestDTO.quantity(),
                productRequestDTO.price(),
                productRequestDTO.currencyCode().trim().toUpperCase(),
                productRequestDTO.imageUrl().trim()
        );
    }

    public static ProductResponseDTO mapProductToProductResponse(Product product) {
        return new ProductResponseDTO(
                product.getId(),
                product.getTitle(),
                product.getDescription(),
                product.getQuantity(),
                product.getPrice(),
                product.getCurrencyCode(),
                product.getImageUrl()
        );
    }
}
