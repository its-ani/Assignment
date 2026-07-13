package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.Category;
import com.ecommerce.oms.domain.Product;
import com.ecommerce.oms.domain.User;
import com.ecommerce.oms.domain.UserRole;
import com.ecommerce.oms.dto.ProductRequest;
import com.ecommerce.oms.repository.CategoryRepository;
import com.ecommerce.oms.repository.ProductRepository;
import com.ecommerce.oms.repository.UserRepository;
import com.ecommerce.oms.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String customerToken;
    private Category electronics;
    private Category laptops;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        User admin = User.builder()
                .name("Admin User")
                .email("admin@test.com")
                .passwordHash("pass")
                .role(UserRole.ADMIN)
                .build();
        userRepository.save(admin);
        adminToken = jwtService.generateToken(admin);

        User customer = User.builder()
                .name("Customer User")
                .email("customer@test.com")
                .passwordHash("pass")
                .role(UserRole.CUSTOMER)
                .build();
        userRepository.save(customer);
        customerToken = jwtService.generateToken(customer);

        electronics = Category.builder().name("Electronics").build();
        categoryRepository.save(electronics);

        laptops = Category.builder().name("Laptops").parentCategory(electronics).build();
        categoryRepository.save(laptops);
    }

    @Test
    void testCreateProductAdminSuccess() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Gaming Laptop")
                .description("Powerful GPU")
                .categoryId(laptops.getId())
                .price(new BigDecimal("1200.00"))
                .active(true)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Gaming Laptop")))
                .andExpect(jsonPath("$.categoryName", is("Laptops")))
                .andExpect(jsonPath("$.price", is(1200.00)));
    }

    @Test
    void testCreateProductCategoryNotFound() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .name("Item")
                .categoryId(UUID.randomUUID())
                .price(BigDecimal.ONE)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Category not found")));
    }

    @Test
    void testSoftDeleteAndDirectGetBehavior() throws Exception {
        Product product = Product.builder()
                .name("iPhone")
                .price(new BigDecimal("999.00"))
                .category(electronics)
                .active(true)
                .build();
        productRepository.save(product);

        mockMvc.perform(delete("/api/v1/products/" + product.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/" + product.getId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/products/" + product.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/products/" + product.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    void testSearchAndFilters() throws Exception {
        Product p1 = Product.builder()
                .name("MacBook Pro")
                .description("Workstation laptop")
                .price(new BigDecimal("1999.99"))
                .category(laptops)
                .active(true)
                .build();
        productRepository.save(p1);

        Product p2 = Product.builder()
                .name("Sony TV")
                .description("Smart LED Television")
                .price(new BigDecimal("799.00"))
                .category(electronics)
                .active(true)
                .build();
        productRepository.save(p2);

        Product p3 = Product.builder()
                .name("Old Phone")
                .description("Broken")
                .price(new BigDecimal("49.99"))
                .category(electronics)
                .active(false)
                .build();
        productRepository.save(p3);

        mockMvc.perform(get("/api/v1/products?keyword=laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("MacBook Pro")));

        mockMvc.perform(get("/api/v1/products?minPrice=500.00&maxPrice=1500.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Sony TV")));

        mockMvc.perform(get("/api/v1/products?categoryId=" + electronics.getId() + "&includeDescendants=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        mockMvc.perform(get("/api/v1/products?categoryId=" + electronics.getId() + "&includeDescendants=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Sony TV")));

        mockMvc.perform(get("/api/v1/products?activeOnly=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        mockMvc.perform(get("/api/v1/products?activeOnly=false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));
    }
}
