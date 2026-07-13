package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.Category;
import com.ecommerce.oms.domain.Product;
import com.ecommerce.oms.domain.User;
import com.ecommerce.oms.domain.UserRole;
import com.ecommerce.oms.dto.CategoryRequest;
import com.ecommerce.oms.repository.*;
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
class CategoryControllerIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.flush();

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
    }

    @Test
    void testCreateCategoryAdminSuccess() throws Exception {
        CategoryRequest request = CategoryRequest.builder()
                .name("Electronics")
                .build();

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Electronics")))
                .andExpect(jsonPath("$.parentCategoryId", nullValue()))
                .andExpect(jsonPath("$.hasChildren", is(false)));
    }

    @Test
    void testCreateCategoryCustomerForbidden() throws Exception {
        CategoryRequest request = CategoryRequest.builder()
                .name("Electronics")
                .build();

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetCategoriesIsPublic() throws Exception {
        Category cat = Category.builder().name("Books").build();
        categoryRepository.save(cat);

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Books")));
    }

    @Test
    void testUpdateCategoryCyclePrevention() throws Exception {
        Category catA = Category.builder().name("Cat A").build();
        categoryRepository.save(catA);

        Category catB = Category.builder().name("Cat B").parentCategory(catA).build();
        categoryRepository.save(catB);

        CategoryRequest request = CategoryRequest.builder()
                .name("Cat A")
                .parentCategoryId(catB.getId())
                .build();

        mockMvc.perform(put("/api/v1/categories/" + catA.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Circular reference detected")));
    }

    @Test
    void testDeleteCategoryBlockedWithDependents() throws Exception {
        Category parent = Category.builder().name("Parent").build();
        categoryRepository.save(parent);

        Category child = Category.builder().name("Child").parentCategory(parent).build();
        categoryRepository.save(child);

        mockMvc.perform(delete("/api/v1/categories/" + parent.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("has subcategories")));

        child.setParentCategory(null);
        categoryRepository.save(child);
        categoryRepository.delete(parent);

        Product product = Product.builder()
                .name("Sample Product")
                .price(BigDecimal.TEN)
                .category(child)
                .active(true)
                .build();
        productRepository.save(product);

        mockMvc.perform(delete("/api/v1/categories/" + child.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("associated products")));
    }
}
