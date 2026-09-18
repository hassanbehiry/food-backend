package com.food.foodapp.category.service;

import com.food.foodapp.category.dto.CategoryCreateRequest;
import com.food.foodapp.category.dto.CategoryResponse;
import com.food.foodapp.category.entity.Category;
import com.food.foodapp.category.mapper.CategoryMapper;
import com.food.foodapp.category.repository.CategoryRepository;
import com.food.foodapp.common.exception.CategoryNotFoundException;
import com.food.foodapp.common.exception.DuplicateCategoryException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final String DEFAULT_ICON = "fa-utensils";
    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(CategoryMapper::toResponse)
                .toList();
    }

    /** Used by restaurant registration and owner settings to resolve a submitted category id. */
    @Transactional(readOnly = true)
    public Category requireById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + id));
    }

    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateCategoryException("Category '" + name + "' already exists");
        }

        String icon = request.getIcon() != null && !request.getIcon().isBlank()
                ? request.getIcon().trim()
                : DEFAULT_ICON;

        Category category = new Category();
        category.setName(name);
        category.setIcon(icon);
        category.setSlug(uniqueSlug(name));

        return CategoryMapper.toResponse(categoryRepository.save(category));
    }

    /**
     * Derives a URL-safe slug from {@code name} (lowercased, whitespace/punctuation collapsed to
     * single hyphens, Unicode letters/digits preserved so an Arabic name yields an Arabic slug
     * rather than an empty string) and disambiguates it against existing rows with a numeric
     * suffix, matching the deterministic {@code category-<id>} fallback V4 used for legacy rows.
     */
    private String uniqueSlug(String name) {
        String base = NON_SLUG_CHARS.matcher(name.toLowerCase()).replaceAll("-");
        base = EDGE_HYPHENS.matcher(base).replaceAll("");
        if (base.isBlank()) {
            base = "category";
        }

        String candidate = base;
        int suffix = 2;
        while (categoryRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
