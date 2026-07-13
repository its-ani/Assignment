package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.*;
import com.ecommerce.oms.dto.ReturnDecisionRequest;
import com.ecommerce.oms.dto.ReturnRequestCreate;
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
import java.time.temporal.ChronoUnit;
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
class ReturnLifecycleIntegrationTest {

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
    private ReturnRequestRepository returnRequestRepository;

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

    private User customer1;
    private User customer2;
    private User staff;

    private Product product;
    private Warehouse warehouse;
    private InventoryItem inventoryItem;

    private void cleanup() {
        returnRequestRepository.deleteAll();
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

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        cleanup();
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

        // Setup Catalog & Inventory
        Category category = Category.builder().name("Electronics").build();
        categoryRepository.save(category);

        product = Product.builder()
                .name("Smartphone")
                .description("Flagship model")
                .price(new BigDecimal("500.00"))
                .category(category)
                .active(true)
                .build();
        productRepository.save(product);

        warehouse = Warehouse.builder().name("Main WH").location("Chicago").build();
        warehouseRepository.save(warehouse);

        inventoryItem = InventoryItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(10)
                .quantityReserved(0)
                .build();
        inventoryItemRepository.save(inventoryItem);
    }

    private Order createDeliveredOrder(User customer, int quantity, Instant deliveredAt) {
        Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.DELIVERED)
                .taxAmount(new BigDecimal("50.00"))
                .discountAmount(new BigDecimal("100.00"))
                .totalAmount(new BigDecimal("450.00")) // unit price 500 - 100 discount + 50 tax = 450 total
                .deliveredAt(deliveredAt)
                .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();
        order = orderRepository.save(order);

        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .warehouse(warehouse)
                .quantity(quantity)
                .unitPrice(new BigDecimal("500.00"))
                .build();
        orderItemRepository.save(item);

        Payment payment = Payment.builder()
                .order(order)
                .status(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("450.00"))
                .method("CARD")
                .build();
        paymentRepository.save(payment);

        return order;
    }

    @Test
    void testFullReturnAndRefundHappyPath() throws Exception {
        Order order = createDeliveredOrder(customer1, 2, Instant.now());
        OrderItem orderItem = orderItemRepository.findByOrderId(order.getId()).get(0);

        // Initial inventory count
        InventoryItem preInv = inventoryItemRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElseThrow();
        assertEquals(10, preInv.getQuantityOnHand());

        // 1. Customer requests return of 1 item
        ReturnRequestCreate request = ReturnRequestCreate.builder()
                .orderItemId(orderItem.getId())
                .quantity(1)
                .reason("Defective screen")
                .build();

        String responseStr = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("REQUESTED")))
                .andExpect(jsonPath("$.quantity", is(1)))
                .andExpect(jsonPath("$.reason", is("Defective screen")))
                .andReturn().getResponse().getContentAsString();

        UUID returnId = UUID.fromString(objectMapper.readTree(responseStr).get("id").asText());

        // 2. Customer lists own returns
        mockMvc.perform(get("/api/v1/returns")
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(returnId.toString())));

        // 3. Customer gets return detail
        mockMvc.perform(get("/api/v1/returns/" + returnId)
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REQUESTED")));

        // 4. Staff lists requests by status
        mockMvc.perform(get("/api/v1/returns/staff?status=REQUESTED")
                        .header("Authorization", "Bearer " + warehouseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        // 5. Staff approves the return request
        ReturnDecisionRequest decision = ReturnDecisionRequest.builder()
                .approved(true)
                .build();

        // Proportional Refund calculation:
        // Order Subtotal = 500 * 2 = 1000.00
        // Returned pre-discount value = 500 * 1 = 500.00
        // Order Total = 450.00
        // Refund = 450 * 500 / 1000 = 225.00
        mockMvc.perform(patch("/api/v1/returns/" + returnId + "/decision")
                        .header("Authorization", "Bearer " + warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REFUNDED")))
                .andExpect(jsonPath("$.refundAmount", is(225.00)));

        // 6. Verify inventory is restocked
        InventoryItem postInv = inventoryItemRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElseThrow();
        assertEquals(11, postInv.getQuantityOnHand()); // 10 -> 11

        // 7. Verify async audit log is created
        await().atMost(3, TimeUnit.SECONDS).until(() -> {
            List<AuditLog> logs = auditLogRepository.findAll();
            return logs.stream()
                    .anyMatch(log -> log.getEntityId().equals(returnId) && log.getAction().equals("RETURN_STATUS_CHANGED"));
        });
    }

    @Test
    void testReturnRequestRejectedForNonDeliveredOrder() throws Exception {
        Order order = createDeliveredOrder(customer1, 2, null); // missing deliveredAt means PLACED or not delivered
        order.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(order);
        OrderItem orderItem = orderItemRepository.findByOrderId(order.getId()).get(0);

        ReturnRequestCreate request = ReturnRequestCreate.builder()
                .orderItemId(orderItem.getId())
                .quantity(1)
                .reason("Wrong item shipped")
                .build();

        // Try to request return for non-delivered order -> 400 Bad Request / 409 Conflict (represented by 400/409 exception)
        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testReturnRequestRejectedOutsideReturnWindow() throws Exception {
        // Delivered 31 days ago (outside 30-day window)
        Instant deliveredAt = Instant.now().minus(31, ChronoUnit.DAYS);
        Order order = createDeliveredOrder(customer1, 2, deliveredAt);
        OrderItem orderItem = orderItemRepository.findByOrderId(order.getId()).get(0);

        ReturnRequestCreate request = ReturnRequestCreate.builder()
                .orderItemId(orderItem.getId())
                .quantity(1)
                .reason("Too late")
                .build();

        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testReturnRequestRejectedForOverQuantity() throws Exception {
        Order order = createDeliveredOrder(customer1, 2, Instant.now());
        OrderItem orderItem = orderItemRepository.findByOrderId(order.getId()).get(0);

        ReturnRequestCreate request = ReturnRequestCreate.builder()
                .orderItemId(orderItem.getId())
                .quantity(3) // ordered only 2
                .reason("Too many")
                .build();

        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testStaffRejectionPath() throws Exception {
        Order order = createDeliveredOrder(customer1, 2, Instant.now());
        OrderItem orderItem = orderItemRepository.findByOrderId(order.getId()).get(0);

        ReturnRequestCreate request = ReturnRequestCreate.builder()
                .orderItemId(orderItem.getId())
                .quantity(1)
                .reason("Not satisfied")
                .build();

        String responseStr = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID returnId = UUID.fromString(objectMapper.readTree(responseStr).get("id").asText());

        // Reject request
        ReturnDecisionRequest decision = ReturnDecisionRequest.builder()
                .approved(false)
                .rejectionReason("No return for opened software")
                .build();

        mockMvc.perform(patch("/api/v1/returns/" + returnId + "/decision")
                        .header("Authorization", "Bearer " + warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.rejectionReason", is("No return for opened software")))
                .andExpect(jsonPath("$.refundAmount", nullValue()));

        // Verify inventory is NOT restocked
        InventoryItem postInv = inventoryItemRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElseThrow();
        assertEquals(10, postInv.getQuantityOnHand());
    }

    @Test
    void testAccessControlRestrictions() throws Exception {
        Order order = createDeliveredOrder(customer1, 2, Instant.now());
        OrderItem orderItem = orderItemRepository.findByOrderId(order.getId()).get(0);

        ReturnRequestCreate request = ReturnRequestCreate.builder()
                .orderItemId(orderItem.getId())
                .quantity(1)
                .reason("Change of mind")
                .build();

        String responseStr = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID returnId = UUID.fromString(objectMapper.readTree(responseStr).get("id").asText());

        // Customer2 cannot view Customer1's return request detail -> 404
        mockMvc.perform(get("/api/v1/returns/" + returnId)
                        .header("Authorization", "Bearer " + customer2Token))
                .andExpect(status().isNotFound());

        // Customer1 cannot decide on own return request -> 403 Forbidden
        ReturnDecisionRequest decision = ReturnDecisionRequest.builder().approved(true).build();
        mockMvc.perform(patch("/api/v1/returns/" + returnId + "/decision")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isForbidden());
    }
}
