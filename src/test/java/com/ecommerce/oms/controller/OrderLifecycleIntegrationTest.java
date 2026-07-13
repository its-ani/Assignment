package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.*;
import com.ecommerce.oms.dto.OrderCancelRequest;
import com.ecommerce.oms.dto.OrderStatusUpdateRequest;
import com.ecommerce.oms.repository.*;
import com.ecommerce.oms.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import java.util.concurrent.TimeUnit;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderLifecycleIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private JwtService jwtService;

    private String customer1Token;
    private String customer2Token;
    private String warehouseToken;
    private String adminToken;

    private User customer1;
    private User customer2;
    private User staff;
    private User admin;

    private Product product;
    private Warehouse warehouse;
    private InventoryItem inventoryItem;

    private void cleanup() {
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        inventoryItemRepository.deleteAll();
        warehouseRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        cleanup();

        // Save users
        customer1 = User.builder()
                .name("Customer 1")
                .email("cust1@test.com")
                .passwordHash("pass")
                .role(UserRole.CUSTOMER)
                .build();
        userRepository.save(customer1);
        customer1Token = jwtService.generateToken(customer1);

        customer2 = User.builder()
                .name("Customer 2")
                .email("cust2@test.com")
                .passwordHash("pass")
                .role(UserRole.CUSTOMER)
                .build();
        userRepository.save(customer2);
        customer2Token = jwtService.generateToken(customer2);

        staff = User.builder()
                .name("Staff")
                .email("staff@test.com")
                .passwordHash("pass")
                .role(UserRole.WAREHOUSE_STAFF)
                .build();
        userRepository.save(staff);
        warehouseToken = jwtService.generateToken(staff);

        admin = User.builder()
                .name("Admin")
                .email("admin@test.com")
                .passwordHash("pass")
                .role(UserRole.ADMIN)
                .build();
        userRepository.save(admin);
        adminToken = jwtService.generateToken(admin);

        // Setup Catalog & Inventory
        Category category = Category.builder().name("Books").build();
        categoryRepository.save(category);

        product = Product.builder()
                .name("Spring Boot Guide")
                .description("API reference book")
                .price(new BigDecimal("29.99"))
                .category(category)
                .active(true)
                .build();
        productRepository.save(product);

        warehouse = Warehouse.builder().name("East Coast WH").location("New York").build();
        warehouseRepository.save(warehouse);

        inventoryItem = InventoryItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(10)
                .quantityReserved(2) // Reserve 2 items
                .build();
        inventoryItemRepository.save(inventoryItem);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        cleanup();
    }

    private Order createOrder(User customer, OrderStatus status) {
        Order order = Order.builder()
                .customer(customer)
                .status(status)
                .taxAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("59.98"))
                .createdAt(Instant.now())
                .build();
        order = orderRepository.save(order);

        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .warehouse(warehouse)
                .quantity(2)
                .unitPrice(new BigDecimal("29.99"))
                .build();
        orderItemRepository.save(item);

        return order;
    }

    @Test
    void testStaffOrderLifecycleWorkflow() throws Exception {
        Order order = createOrder(customer1, OrderStatus.PLACED);
        UUID orderId = order.getId();

        // 1. Walk PLACED -> CONFIRMED
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));

        // 2. Walk CONFIRMED -> PACKED
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.PACKED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PACKED")));

        // 3. Walk PACKED -> SHIPPED
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.SHIPPED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SHIPPED")));

        // 4. Walk SHIPPED -> DELIVERED
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.DELIVERED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DELIVERED")));
    }

    @Test
    void testCustomerCannotUpdateStatus() throws Exception {
        Order order = createOrder(customer1, OrderStatus.PLACED);
        UUID orderId = order.getId();

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isForbidden());
    }

    @Test
    void testInvalidTransitionsReturnConflict() throws Exception {
        Order order = createOrder(customer1, OrderStatus.PLACED);
        UUID orderId = order.getId();

        // PLACED -> SHIPPED is invalid (skips CONFIRMED and PACKED)
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.SHIPPED))))
                .andExpect(status().isConflict());
    }

    @Test
    void testCrossCustomerAccessReturnsNotFound() throws Exception {
        Order order = createOrder(customer1, OrderStatus.PLACED);
        UUID orderId = order.getId();

        // customer2 tries to view customer1's order -> 404 Not Found
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + customer2Token))
                .andExpect(status().isNotFound());

        // customer2 tries to cancel customer1's order -> 404 Not Found
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customer2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCustomerSelfCancelReleasesInventory() throws Exception {
        Order order = createOrder(customer1, OrderStatus.PLACED);
        UUID orderId = order.getId();

        // Initial reserved is 2
        InventoryItem preInv = inventoryItemRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElseThrow();
        assertEquals(2, preInv.getQuantityReserved());

        // Customer cancels
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderCancelRequest("No longer needed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        // Verify reservation decreased
        InventoryItem postInv = inventoryItemRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElseThrow();
        assertEquals(0, postInv.getQuantityReserved());
    }

    @Test
    void testCustomerSelfCancelAfterPackedReturnsConflict() throws Exception {
        Order order = createOrder(customer1, OrderStatus.PACKED);
        UUID orderId = order.getId();

        // Customer cancels packed order -> 409 Conflict
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void testStaffCanFilterAndListOrders() throws Exception {
        createOrder(customer1, OrderStatus.PLACED);
        createOrder(customer2, OrderStatus.DELIVERED);

        // Staff listing PLACED orders
        mockMvc.perform(get("/api/v1/orders/staff?status=PLACED")
                        .header("Authorization", "Bearer " + warehouseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status", is("PLACED")));
    }

    @Test
    void testAsyncAuditLogIsCreated() throws Exception {
        Order order = createOrder(customer1, OrderStatus.PLACED);
        UUID orderId = order.getId();

        // Trigger status change to CONFIRMED
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isOk());

        // Wait up to 3 seconds for async event handler to save AuditLog entry
        await().atMost(3, TimeUnit.SECONDS).until(() -> {
            List<AuditLog> logs = auditLogRepository.findAll();
            return logs.stream()
                    .anyMatch(log -> log.getEntityId().equals(orderId) && log.getAction().equals("ORDER_STATUS_CHANGED"));
        });
    }
}
