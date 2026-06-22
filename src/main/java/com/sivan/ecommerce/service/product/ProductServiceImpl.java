package com.sivan.ecommerce.service.product;

import com.sivan.ecommerce.dto.product.ProductFilterDTO;
import com.sivan.ecommerce.dto.product.ProductRequestDTO;
import com.sivan.ecommerce.dto.product.ProductResponseDTO;
import com.sivan.ecommerce.entity.product.Product;
import com.sivan.ecommerce.exception.InvalidDataException;
import com.sivan.ecommerce.exception.ProductNotFoundException;
import com.sivan.ecommerce.mapper.product.ProductMapper;
import com.sivan.ecommerce.repository.product.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    // TODO: Think of adding "newest" (createdAt) sorting field in the future
    private static final Set<String> ALLOWED_SORTS = Set.of("title", "price");

    @Autowired
    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO productRequestDTO) {
        Product product = ProductMapper.mapProductRequestToProduct(productRequestDTO);

        return ProductMapper.mapProductToProductResponse(productRepository.save(product));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDTO getProduct(UUID productId) {
        Optional<Product> product = productRepository.findById(productId);

        if (product.isEmpty() || !product.get().isActive())
            throw new ProductNotFoundException("Product not found");

        return ProductMapper.mapProductToProductResponse(product.get());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getProducts(ProductFilterDTO productFilterDTO, Pageable pageable) {
        if (productFilterDTO.minPrice() != null && productFilterDTO.maxPrice() != null && productFilterDTO.minPrice() > productFilterDTO.maxPrice())
            throw new InvalidDataException("Minimum price must be less than or equal to maximum price");

        for (Sort.Order order : pageable.getSort()) {
            // Throwing InvalidDataException tells Spring "The client sent bad data"
            if (!ALLOWED_SORTS.contains(order.getProperty()))
                throw new InvalidDataException("Sorting by '" + order.getProperty() + "' is not allowed");
        }

        return productRepository.findByFilters(
                productFilterDTO.search(),
                productFilterDTO.minPrice(),
                productFilterDTO.maxPrice(),
                productFilterDTO.category(),
                pageable);
    }
}
