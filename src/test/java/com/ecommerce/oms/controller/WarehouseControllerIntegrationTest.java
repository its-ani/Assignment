package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.*;
import com.ecommerce.oms.dto.WarehouseRequest;
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
class WarehouseControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

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
    private String staffToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.flush();

        User admin = User.builder()
                .name("Admin")
                .email("admin@test.com")
                .passwordHash("pass")
                .role(UserRole.ADMIN)
                .build();
        userRepository.save(admin);
        adminToken = jwtService.generateToken(admin);

        User staff = User.builder()
                .name("Staff")
                .email("staff@test.com")
                .passwordHash("pass")
                .role(UserRole.WAREHOUSE_STAFF)
                .build();
        userRepository.save(staff);
        staffToken = jwtService.generateToken(staff);

        User customer = User.builder()
                .name("Customer")
                .email("customer@test.com")
                .passwordHash("pass")
                .role(UserRole.CUSTOMER)
                .build();
        userRepository.save(customer);
        customerToken = jwtService.generateToken(customer);
    }

    @Test
    void testCreateWarehouseAdminSuccess() throws Exception {
        WarehouseRequest request = new WarehouseRequest("Central WH", "Chicago");

        mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Central WH")))
                .andExpect(jsonPath("$.location", is("Chicago")));
    }

    @Test
    void testCreateWarehouseStaffForbidden() throws Exception {
        WarehouseRequest request = new WarehouseRequest("Central WH", "Chicago");

        mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCreateWarehouseCustomerForbidden() throws Exception {
        WarehouseRequest request = new WarehouseRequest("Central WH", "Chicago");

        mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetWarehouseStaffSuccess() throws Exception {
        Warehouse wh = warehouseRepository.save(Warehouse.builder().name("East WH").location("New York").build());

        mockMvc.perform(get("/api/v1/warehouses/" + wh.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("East WH")))
                .andExpect(jsonPath("$.location", is("New York")));
    }

    @Test
    void testGetWarehouseCustomerForbidden() throws Exception {
        Warehouse wh = warehouseRepository.save(Warehouse.builder().name("East WH").location("New York").build());

        mockMvc.perform(get("/api/v1/warehouses/" + wh.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeleteWarehouseBlockedWhenInventoryExists() throws Exception {
        Warehouse wh = warehouseRepository.save(Warehouse.builder().name("East WH").location("New York").build());
        Category cat = categoryRepository.save(Category.builder().name("Books").build());
        Product prod = productRepository.save(Product.builder()
                .name("Novel")
                .price(BigDecimal.TEN)
                .category(cat)
                .active(true)
                .build());

        inventoryItemRepository.save(InventoryItem.builder()
                .product(prod)
                .warehouse(wh)
                .quantityOnHand(10)
                .quantityReserved(0)
                .build());

        mockMvc.perform(delete("/api/v1/warehouses/" + wh.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }
}
