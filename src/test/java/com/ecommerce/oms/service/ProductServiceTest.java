package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Category;
import com.ecommerce.oms.domain.Product;
import com.ecommerce.oms.dto.ProductRequest;
import com.ecommerce.oms.dto.ProductResponse;
import com.ecommerce.oms.exception.ResourceNotFoundException;
import com.ecommerce.oms.repository.CategoryRepository;
import com.ecommerce.oms.repository.ProductRepository;
import com.ecommerce.oms.repository.InventoryItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @InjectMocks
    private ProductService productService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(String role) {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        if (role != null) {
            doReturn(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)))
                    .when(authentication).getAuthorities();
            when(securityContext.getAuthentication()).thenReturn(authentication);
        } else {
            when(securityContext.getAuthentication()).thenReturn(null);
        }
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testCreateProductSuccessfully() {
        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder().id(categoryId).name("Electronics").build();

        ProductRequest request = ProductRequest.builder()
                .name("Laptop")
                .description("Super laptop")
                .categoryId(categoryId)
                .price(new BigDecimal("999.99"))
                .active(true)
                .build();

        Product savedProduct = Product.builder()
                .id(UUID.randomUUID())
                .name("Laptop")
                .description("Super laptop")
                .category(category)
                .price(new BigDecimal("999.99"))
                .active(true)
                .createdAt(Instant.now())
                .build();

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals("Laptop", response.getName());
        assertEquals(categoryId, response.getCategoryId());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void testCreateProductCategoryNotFoundThrowsException() {
        UUID categoryId = UUID.randomUUID();
        ProductRequest request = ProductRequest.builder()
                .name("Laptop")
                .categoryId(categoryId)
                .price(new BigDecimal("999.99"))
                .build();

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.createProduct(request));
        verify(productRepository, never()).save(any());
    }

    @Test
    void testGetProductByIdActiveIsPublic() {
        UUID productId = UUID.randomUUID();
        Category category = Category.builder().id(UUID.randomUUID()).name("Electronics").build();
        Product product = Product.builder()
                .id(productId)
                .name("Laptop")
                .category(category)
                .price(new BigDecimal("999.99"))
                .active(true)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProductById(productId);

        assertNotNull(response);
        assertTrue(response.isActive());
        assertEquals("Laptop", response.getName());
    }

    @Test
    void testGetProductByIdInactiveBlockedForPublic() {
        UUID productId = UUID.randomUUID();
        Category category = Category.builder().id(UUID.randomUUID()).name("Electronics").build();
        Product product = Product.builder()
                .id(productId)
                .name("Laptop")
                .category(category)
                .price(new BigDecimal("999.99"))
                .active(false)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(productId));
    }

    @Test
    void testGetProductByIdInactiveAllowedForAdmin() {
        UUID productId = UUID.randomUUID();
        Category category = Category.builder().id(UUID.randomUUID()).name("Electronics").build();
        Product product = Product.builder()
                .id(productId)
                .name("Laptop")
                .category(category)
                .price(new BigDecimal("999.99"))
                .active(false)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        mockSecurityContext("ADMIN");

        ProductResponse response = productService.getProductById(productId);

        assertNotNull(response);
        assertFalse(response.isActive());
        assertEquals("Laptop", response.getName());
    }

    @Test
    void testSoftDeleteProduct() {
        UUID productId = UUID.randomUUID();
        Category category = Category.builder().id(UUID.randomUUID()).name("Electronics").build();
        Product product = Product.builder()
                .id(productId)
                .name("Laptop")
                .category(category)
                .price(new BigDecimal("999.99"))
                .active(true)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        productService.softDeleteProduct(productId);

        assertFalse(product.isActive());
        verify(productRepository).save(product);
    }
}
