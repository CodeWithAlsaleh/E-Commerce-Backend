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
                productRequestDTO.title(),
                productRequestDTO.description(),
                productRequestDTO.quantity(),
                productRequestDTO.price(),
                productRequestDTO.currencyCode(),
                productRequestDTO.imageUrl()
        );
    }

    public static ProductResponseDTO mapProductToProductResponse(Product product) {
        return new ProductResponseDTO(
                product.getId() != null ? product.getId().toString() : null,
                product.getTitle(),
                product.getDescription(),
                product.getQuantity(),
                product.getPrice(),
                product.getCurrencyCode(),
                product.getImageUrl()
        );
    }
}
