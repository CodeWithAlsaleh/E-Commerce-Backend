package com.sivan.ecommerce.service.product;

import com.sivan.ecommerce.dto.product.ProductFilterDTO;
import com.sivan.ecommerce.dto.product.ProductRequestDTO;
import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductService {

    ProductResponseDTO createProduct(ProductRequestDTO productRequestDTO);

    ProductResponseDTO getProduct(UUID productId);

    Page<ProductResponseDTO> getProducts(ProductFilterDTO productFilterDTO, Pageable pageable);
}
