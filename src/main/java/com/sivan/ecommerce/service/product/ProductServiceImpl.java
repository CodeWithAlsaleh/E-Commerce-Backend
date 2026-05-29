package com.sivan.ecommerce.service.product;

import com.sivan.ecommerce.dto.product.ProductRequestDTO;
import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import com.sivan.ecommerce.entity.product.Product;
import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.mapper.product.ProductMapper;
import com.sivan.ecommerce.repository.product.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Autowired
    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO productRequestDTO) {
        Product product = ProductMapper.mapProductRequestToProduct(productRequestDTO);

        checkDescription(product.getDescription());

        return ProductMapper.mapProductToProductResponse(productRepository.save(product));
    }

    private void checkDescription(String description) {
        if (description != null && description.length() < 40)
            throw new InvalidDataException("Description: trimmed size must be at least 40 characters if provided.");
    }
}
