package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Category;
import com.ecommerce.oms.dto.CategoryRequest;
import com.ecommerce.oms.dto.CategoryResponse;
import com.ecommerce.oms.exception.CategoryHasDependentsException;
import com.ecommerce.oms.exception.DuplicateResourceException;
import com.ecommerce.oms.exception.ResourceNotFoundException;
import com.ecommerce.oms.repository.CategoryRepository;
import com.ecommerce.oms.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void testCreateCategorySuccessfully() {
        CategoryRequest request = CategoryRequest.builder()
                .name("Books")
                .build();

        Category savedCategory = Category.builder()
                .id(UUID.randomUUID())
                .name("Books")
                .build();

        when(categoryRepository.existsByNameIgnoreCase("Books")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

        CategoryResponse response = categoryService.createCategory(request);

        assertNotNull(response);
        assertEquals("Books", response.getName());
        assertEquals(savedCategory.getId(), response.getId());
    }

    @Test
    void testCreateCategoryDuplicateNameThrowsException() {
        CategoryRequest request = CategoryRequest.builder()
                .name("Books")
                .build();

        when(categoryRepository.existsByNameIgnoreCase("Books")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> categoryService.createCategory(request));
    }

    @Test
    void testUpdateCategoryCyclePreventionSelf() {
        UUID categoryId = UUID.randomUUID();
        CategoryRequest request = CategoryRequest.builder()
                .name("Books")
                .parentCategoryId(categoryId)
                .build();

        Category existing = Category.builder()
                .id(categoryId)
                .name("Books")
                .build();

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Books", categoryId)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                categoryService.updateCategory(categoryId, request)
        );
        assertTrue(exception.getMessage().contains("cannot be its own parent"));
    }

    @Test
    void testUpdateCategoryCyclePreventionAncestors() {
        UUID categoryAId = UUID.randomUUID();
        UUID categoryBId = UUID.randomUUID();
        UUID categoryCId = UUID.randomUUID();

        Category categoryC = Category.builder().id(categoryCId).name("C").build();
        Category categoryB = Category.builder().id(categoryBId).name("B").parentCategory(categoryC).build();
        Category categoryA = Category.builder().id(categoryAId).name("A").parentCategory(categoryB).build();

        CategoryRequest request = CategoryRequest.builder()
                .name("C")
                .parentCategoryId(categoryAId)
                .build();

        when(categoryRepository.findById(categoryCId)).thenReturn(Optional.of(categoryC));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("C", categoryCId)).thenReturn(false);

        when(categoryRepository.findById(categoryAId)).thenReturn(Optional.of(categoryA));
        when(categoryRepository.findById(categoryBId)).thenReturn(Optional.of(categoryB));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                categoryService.updateCategory(categoryCId, request)
        );
        assertTrue(exception.getMessage().contains("Circular reference detected"));
    }

    @Test
    void testDeleteCategoryBlockedBySubcategories() {
        UUID id = UUID.randomUUID();
        Category category = Category.builder().id(id).name("Books").build();

        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(categoryRepository.hasSubcategories(id)).thenReturn(true);

        assertThrows(CategoryHasDependentsException.class, () -> categoryService.deleteCategory(id));
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void testDeleteCategoryBlockedByProducts() {
        UUID id = UUID.randomUUID();
        Category category = Category.builder().id(id).name("Books").build();

        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(categoryRepository.hasSubcategories(id)).thenReturn(false);
        when(productRepository.existsByCategoryId(id)).thenReturn(true);

        assertThrows(CategoryHasDependentsException.class, () -> categoryService.deleteCategory(id));
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void testDeleteCategorySuccessfully() {
        UUID id = UUID.randomUUID();
        Category category = Category.builder().id(id).name("Books").build();

        when(categoryRepository.findById(id)).thenReturn(Optional.of(category));
        when(categoryRepository.hasSubcategories(id)).thenReturn(false);
        when(productRepository.existsByCategoryId(id)).thenReturn(false);

        categoryService.deleteCategory(id);

        verify(categoryRepository).delete(category);
    }
}
