package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.*;
import com.ecommerce.oms.dto.InventoryAdjustRequest;
import com.ecommerce.oms.dto.InventorySetRequest;
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
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InventoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String staffToken;
    private String customerToken;

    private Warehouse warehouse;
    private Product product;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        inventoryItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();

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

        warehouse = warehouseRepository.save(Warehouse.builder().name("Main WH").location("New York").build());
        Category cat = categoryRepository.save(Category.builder().name("Electronics").build());
        product = productRepository.save(Product.builder()
                .name("Smartphone")
                .price(new BigDecimal("500.00"))
                .category(cat)
                .active(true)
                .build());
    }

    @Test
    void testSetStockAdminSuccess() throws Exception {
        InventorySetRequest request = new InventorySetRequest(15);

        mockMvc.perform(put("/api/v1/inventory/" + product.getId() + "/warehouse/" + warehouse.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand", is(15)))
                .andExpect(jsonPath("$.quantityReserved", is(0)))
                .andExpect(jsonPath("$.quantityAvailable", is(15)));

        // Verify database entry
        InventoryItem item = inventoryItemRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElseThrow();
        assertEquals(15, item.getQuantityOnHand());

        // Verify AuditLog
        List<AuditLog> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty());
        AuditLog log = logs.stream()
                .filter(l -> l.getEntityId().equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("SET_STOCK", log.getAction());
        assertEquals("admin@test.com", log.getActor());
    }

    @Test
    void testSetStockStaffForbidden() throws Exception {
        InventorySetRequest request = new InventorySetRequest(15);

        mockMvc.perform(put("/api/v1/inventory/" + product.getId() + "/warehouse/" + warehouse.getId())
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdjustStockAdminSuccess() throws Exception {
        // Pre-create stock item
        inventoryItemRepository.save(InventoryItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(10)
                .quantityReserved(2)
                .build());

        InventoryAdjustRequest request = new InventoryAdjustRequest(5, "Received shipments");

        mockMvc.perform(patch("/api/v1/inventory/" + product.getId() + "/warehouse/" + warehouse.getId() + "/adjust")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand", is(15)))
                .andExpect(jsonPath("$.quantityReserved", is(2)))
                .andExpect(jsonPath("$.quantityAvailable", is(13)));

        // Verify AuditLog is created
        List<AuditLog> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty());
        assertEquals("ADJUST_STOCK", logs.get(0).getAction());
    }

    @Test
    void testAdjustStockGoingNegativeFails() throws Exception {
        inventoryItemRepository.save(InventoryItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(10)
                .quantityReserved(2)
                .build());

        InventoryAdjustRequest request = new InventoryAdjustRequest(-11, "Over-reduce stock");

        mockMvc.perform(patch("/api/v1/inventory/" + product.getId() + "/warehouse/" + warehouse.getId() + "/adjust")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void testAdjustStockLessThanReservedFails() throws Exception {
        inventoryItemRepository.save(InventoryItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(10)
                .quantityReserved(5)
                .build());

        InventoryAdjustRequest request = new InventoryAdjustRequest(-6, "Reduce below reserved");

        mockMvc.perform(patch("/api/v1/inventory/" + product.getId() + "/warehouse/" + warehouse.getId() + "/adjust")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void testGetInventoryProductStaffSuccess() throws Exception {
        inventoryItemRepository.save(InventoryItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(10)
                .quantityReserved(2)
                .build());

        mockMvc.perform(get("/api/v1/inventory/product/" + product.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].quantityOnHand", is(10)));
    }

    @Test
    void testGetInventoryProductCustomerForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/product/" + product.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetProductAvailabilityPublicEnrichment() throws Exception {
        inventoryItemRepository.save(InventoryItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(10)
                .quantityReserved(2)
                .build());

        // Call public get product details
        mockMvc.perform(get("/api/v1/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(product.getId().toString())))
                .andExpect(jsonPath("$.availability.productId", is(product.getId().toString())))
                .andExpect(jsonPath("$.availability.available", is(true)))
                .andExpect(jsonPath("$.availability.totalAvailableQuantity", is(8)))
                // Verify raw data is NOT leaked at root level of product response
                .andExpect(jsonPath("$.quantityOnHand").doesNotExist())
                .andExpect(jsonPath("$.quantityReserved").doesNotExist());
    }
}
