package com.sivan.ecommerce.mapper.category;

import com.sivan.ecommerce.dto.category.CategoryRequestDTO;
import com.sivan.ecommerce.dto.category.CategoryResponseDTO;
import com.sivan.ecommerce.entity.category.Category;

public class CategoryMapper {

    private CategoryMapper() {
        // Prevent instantiation
    }

    public static Category mapCategoryRequestToCategory(CategoryRequestDTO categoryRequestDTO) {
        return new Category(
                categoryRequestDTO.title(),
                categoryRequestDTO.description()
        );
    }

    public static CategoryResponseDTO mapCategoryToCategoryResponse(Category category) {
        return new CategoryResponseDTO(
                category.getId(),
                category.getTitle(),
                category.getDescription(),
                category.isActive()
        );
    }
}
