package com.sivan.ecommerce.service.category;

import com.sivan.ecommerce.dto.category.CategoryRequestDTO;
import com.sivan.ecommerce.dto.category.CategoryResponseDTO;
import com.sivan.ecommerce.entity.category.Category;
import com.sivan.ecommerce.exception.CategoryAlreadyExistsException;
import com.sivan.ecommerce.mapper.category.CategoryMapper;
import com.sivan.ecommerce.repository.category.CategoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public CategoryResponseDTO createCategory(CategoryRequestDTO categoryRequestDTO) {
        if (categoryRepository.existsByTitle(categoryRequestDTO.title()))
            throw new CategoryAlreadyExistsException("Category already exists");

        Category category = CategoryMapper.mapCategoryRequestToCategory(categoryRequestDTO);

        return CategoryMapper.mapCategoryToCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> getCategories() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "title"))
                .stream()
                .map(CategoryMapper::mapCategoryToCategoryResponse)
                .toList();
    }
}
