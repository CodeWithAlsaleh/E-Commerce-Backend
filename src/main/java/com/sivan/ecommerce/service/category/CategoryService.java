package com.sivan.ecommerce.service.category;

import com.sivan.ecommerce.dto.category.CategoryRequestDTO;
import com.sivan.ecommerce.dto.category.CategoryResponseDTO;

import java.util.List;

public interface CategoryService {

    CategoryResponseDTO createCategory(CategoryRequestDTO categoryRequestDTO);

    List<CategoryResponseDTO> getCategories();
}
