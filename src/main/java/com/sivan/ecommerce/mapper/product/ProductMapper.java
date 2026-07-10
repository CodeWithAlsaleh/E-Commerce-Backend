package com.sivan.ecommerce.mapper.product;

import com.sivan.ecommerce.dto.product.ProductRequestDTO;
import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import com.sivan.ecommerce.dto.product.ProductUpdateRequestDTO;
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

    public static void mapProductUpdateRequestToProduct(Product product, ProductUpdateRequestDTO productUpdateRequestDTO) {
        if (productUpdateRequestDTO.title() != null)
            product.setTitle(productUpdateRequestDTO.title());

        /*
         *   But what if the user wants to delete their description?
         *
         *   If they send {"description": null} in their JSON, Jackson parses it as null.
         *   Your DTO receives null. Your if statement sees null and skips it.
         *   The old description stays!
         * */
        if (productUpdateRequestDTO.description() != null)
            product.setDescription(productUpdateRequestDTO.description());

        if (productUpdateRequestDTO.quantity() != null)
            product.setQuantity(productUpdateRequestDTO.quantity());

        if (productUpdateRequestDTO.price() != null)
            product.setPrice(productUpdateRequestDTO.price());

        if (productUpdateRequestDTO.currencyCode() != null)
            product.setCurrencyCode(productUpdateRequestDTO.currencyCode());

        if (productUpdateRequestDTO.imageUrl() != null)
            product.setImageUrl(productUpdateRequestDTO.imageUrl());
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
