package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Category;
import com.ecommerce.oms.dto.CategoryRequest;
import com.ecommerce.oms.dto.CategoryResponse;
import com.ecommerce.oms.exception.CategoryHasDependentsException;
import com.ecommerce.oms.exception.DuplicateResourceException;
import com.ecommerce.oms.exception.ResourceNotFoundException;
import com.ecommerce.oms.repository.CategoryRepository;
import com.ecommerce.oms.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Category with name '" + request.getName() + "' already exists");
        }

        Category parent = null;
        if (request.getParentCategoryId() != null) {
            parent = categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with ID: " + request.getParentCategoryId()));
        }

        Category category = Category.builder()
                .name(request.getName())
                .parentCategory(parent)
                .build();

        Category saved = categoryRepository.save(category);
        return mapToResponse(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new DuplicateResourceException("Category with name '" + request.getName() + "' already exists");
        }

        Category parent = null;
        if (request.getParentCategoryId() != null) {
            checkCircularReference(id, request.getParentCategoryId());
            parent = categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with ID: " + request.getParentCategoryId()));
        }

        category.setName(request.getName());
        category.setParentCategory(parent);

        Category updated = categoryRepository.save(category);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));
        return mapToResponse(category);
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> listCategories(Pageable pageable) {
        return categoryRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));

        if (categoryRepository.hasSubcategories(id)) {
            throw new CategoryHasDependentsException("Category cannot be deleted because it has subcategories.");
        }

        if (productRepository.existsByCategoryId(id)) {
            throw new CategoryHasDependentsException("Category cannot be deleted because it has associated products.");
        }

        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public Set<UUID> getAllDescendantIds(UUID categoryId) {
        Set<UUID> descendants = new HashSet<>();
        Category root = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + categoryId));
        
        collectDescendantIds(root, descendants);
        return descendants;
    }

    private void collectDescendantIds(Category category, Set<UUID> descendants) {
        List<Category> children = categoryRepository.findByParentCategoryId(category.getId());
        for (Category child : children) {
            if (descendants.add(child.getId())) {
                collectDescendantIds(child, descendants);
            }
        }
    }

    private void checkCircularReference(UUID categoryId, UUID proposedParentId) {
        if (proposedParentId == null) {
            return;
        }
        if (categoryId.equals(proposedParentId)) {
            throw new IllegalArgumentException("A category cannot be its own parent.");
        }
        UUID currentAncestorId = proposedParentId;
        while (currentAncestorId != null) {
            Category ancestor = categoryRepository.findById(currentAncestorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with ID: " + proposedParentId));
            if (ancestor.getParentCategory() != null) {
                UUID parentId = ancestor.getParentCategory().getId();
                if (parentId.equals(categoryId)) {
                    throw new IllegalArgumentException("Circular reference detected. Setting this parent category would create a cycle.");
                }
                currentAncestorId = parentId;
            } else {
                break;
            }
        }
    }

    private CategoryResponse mapToResponse(Category category) {
        UUID parentId = category.getParentCategory() != null ? category.getParentCategory().getId() : null;
        String parentName = category.getParentCategory() != null ? category.getParentCategory().getName() : null;
        boolean hasChildren = categoryRepository.hasSubcategories(category.getId());
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .parentCategoryId(parentId)
                .parentCategoryName(parentName)
                .hasChildren(hasChildren)
                .build();
    }
}
