package com.food.foodapp.category.service;

import com.food.foodapp.category.dto.CategoryResponse;
import com.food.foodapp.category.mapper.CategoryMapper;
import com.food.foodapp.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(CategoryMapper::toResponse)
                .toList();
    }
}
