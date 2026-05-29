package com.sivan.ecommerce.service.product;

import com.sivan.ecommerce.dto.product.ProductRequestDTO;
import com.sivan.ecommerce.dto.product.ProductResponseDTO;

public interface ProductService {

    ProductResponseDTO createProduct(ProductRequestDTO productRequestDTO);
}
