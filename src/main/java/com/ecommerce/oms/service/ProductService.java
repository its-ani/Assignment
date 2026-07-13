package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Category;
import com.ecommerce.oms.domain.Product;
import com.ecommerce.oms.dto.ProductRequest;
import com.ecommerce.oms.dto.ProductResponse;
import com.ecommerce.oms.exception.ResourceNotFoundException;
import com.ecommerce.oms.repository.CategoryRepository;
import com.ecommerce.oms.repository.ProductRepository;
import com.ecommerce.oms.repository.ProductSpecification;
import com.ecommerce.oms.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            CategoryService categoryService
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .category(category)
                .price(request.getPrice())
                .active(request.isActive())
                .build();

        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategory(category);
        product.setPrice(request.getPrice());
        product.setActive(request.isActive());

        Product updated = productRepository.save(product);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        if (!product.isActive() && !isPrivilegedUser()) {
            throw new ResourceNotFoundException("Product not found with ID: " + id);
        }

        return mapToResponse(product);
    }

    @Transactional
    public void softDeleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
        product.setActive(false);
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(
            UUID categoryId,
            Boolean includeDescendants,
            String keyword,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean activeOnly,
            Pageable pageable
    ) {
        List<UUID> categoryIds = null;
        if (categoryId != null) {
            categoryIds = new ArrayList<>();
            categoryIds.add(categoryId);
            if (Boolean.TRUE.equals(includeDescendants)) {
                categoryIds.addAll(categoryService.getAllDescendantIds(categoryId));
            }
        }

        boolean enforceActiveOnly = true;
        if (isPrivilegedUser()) {
            enforceActiveOnly = activeOnly == null || activeOnly;
        }

        Specification<Product> spec = ProductSpecification.filterProducts(
                categoryIds,
                keyword,
                minPrice,
                maxPrice,
                enforceActiveOnly
        );

        return productRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    private boolean isPrivilegedUser() {
        return SecurityUtils.getAuthentication()
                .map(auth -> auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_WAREHOUSE_STAFF")))
                .orElse(false);
    }

    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .price(product.getPrice())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
