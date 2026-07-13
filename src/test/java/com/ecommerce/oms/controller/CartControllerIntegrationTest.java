package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.*;
import com.ecommerce.oms.dto.AddCartItemRequest;
import com.ecommerce.oms.dto.UpdateCartItemRequest;
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
class CartControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private JwtService jwtService;

    private String customerToken;
    private String adminToken;
    private String warehouseToken;

    private Category category;
    private Product product1;
    private Product product2;
    private Product inactiveProduct;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        cartRepository.deleteAll();
        inventoryItemRepository.deleteAll();
        warehouseRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        User customer = User.builder()
                .name("Customer User")
                .email("customer@test.com")
                .passwordHash("pass")
                .role(UserRole.CUSTOMER)
                .build();
        userRepository.save(customer);
        customerToken = jwtService.generateToken(customer);

        User admin = User.builder()
                .name("Admin User")
                .email("admin@test.com")
                .passwordHash("pass")
                .role(UserRole.ADMIN)
                .build();
        userRepository.save(admin);
        adminToken = jwtService.generateToken(admin);

        User staff = User.builder()
                .name("Staff User")
                .email("staff@test.com")
                .passwordHash("pass")
                .role(UserRole.WAREHOUSE_STAFF)
                .build();
        userRepository.save(staff);
        warehouseToken = jwtService.generateToken(staff);

        category = Category.builder().name("Books").build();
        categoryRepository.save(category);

        product1 = Product.builder()
                .name("Java Complete Reference")
                .description("Detailed Java manual")
                .price(new BigDecimal("50.00"))
                .category(category)
                .active(true)
                .build();
        productRepository.save(product1);

        product2 = Product.builder()
                .name("Effective Java")
                .description("Best practices book")
                .price(new BigDecimal("40.00"))
                .category(category)
                .active(true)
                .build();
        productRepository.save(product2);

        inactiveProduct = Product.builder()
                .name("Deprecated Library")
                .description("Legacy docs")
                .price(new BigDecimal("10.00"))
                .category(category)
                .active(false)
                .build();
        productRepository.save(inactiveProduct);

        warehouse = Warehouse.builder().name("Main Hub").location("New York").build();
        warehouseRepository.save(warehouse);

        InventoryItem inv1 = InventoryItem.builder()
                .product(product1)
                .warehouse(warehouse)
                .quantityOnHand(10)
                .quantityReserved(2)
                .build();
        inventoryItemRepository.save(inv1);

        InventoryItem inv2 = InventoryItem.builder()
                .product(product2)
                .warehouse(warehouse)
                .quantityOnHand(0)
                .quantityReserved(0)
                .build();
        inventoryItemRepository.save(inv2);
    }

    @Test
    void testFullCartLifecycle() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.subtotal", is(0.00)))
                .andExpect(jsonPath("$.itemCount", is(0)));

        AddCartItemRequest addReq = AddCartItemRequest.builder()
                .productId(product1.getId())
                .quantity(2)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId", is(product1.getId().toString())))
                .andExpect(jsonPath("$.items[0].quantity", is(2)))
                .andExpect(jsonPath("$.items[0].unitPrice", is(50.00)))
                .andExpect(jsonPath("$.items[0].lineTotal", is(100.00)))
                .andExpect(jsonPath("$.subtotal", is(100.00)))
                .andExpect(jsonPath("$.itemCount", is(2)));

        AddCartItemRequest addRepeat = AddCartItemRequest.builder()
                .productId(product1.getId())
                .quantity(1)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRepeat)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity", is(3)))
                .andExpect(jsonPath("$.items[0].lineTotal", is(150.00)))
                .andExpect(jsonPath("$.subtotal", is(150.00)))
                .andExpect(jsonPath("$.itemCount", is(3)));

        UpdateCartItemRequest updateReq = UpdateCartItemRequest.builder()
                .quantity(5)
                .build();

        mockMvc.perform(patch("/api/v1/cart/items/" + product1.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity", is(5)))
                .andExpect(jsonPath("$.items[0].lineTotal", is(250.00)))
                .andExpect(jsonPath("$.subtotal", is(250.00)))
                .andExpect(jsonPath("$.itemCount", is(5)));

        mockMvc.perform(delete("/api/v1/cart/items/" + product1.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.subtotal", is(0.00)))
                .andExpect(jsonPath("$.itemCount", is(0)));

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/cart")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void testNonCustomerRolesForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + warehouseToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAddInactiveProductReturnsNotFound() throws Exception {
        AddCartItemRequest req = AddCartItemRequest.builder()
                .productId(inactiveProduct.getId())
                .quantity(1)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void testSoftStockWarningTriggers() throws Exception {
        AddCartItemRequest req = AddCartItemRequest.builder()
                .productId(product2.getId())
                .quantity(2)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].availabilityWarning", is("Product is out of stock")));
    }

    @Test
    void testLivePriceComputation() throws Exception {
        AddCartItemRequest req = AddCartItemRequest.builder()
                .productId(product1.getId())
                .quantity(2)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice", is(50.00)))
                .andExpect(jsonPath("$.subtotal", is(100.00)));

        product1.setPrice(new BigDecimal("60.00"));
        productRepository.save(product1);

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice", is(60.00)))
                .andExpect(jsonPath("$.subtotal", is(120.00)));
    }
}
