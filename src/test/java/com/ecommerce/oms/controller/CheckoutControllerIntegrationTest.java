package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.*;
import com.ecommerce.oms.dto.CheckoutRequest;
import com.ecommerce.oms.dto.OrderResponse;
import com.ecommerce.oms.repository.*;
import com.ecommerce.oms.security.JwtService;
import com.ecommerce.oms.service.PaymentServiceImpl;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
class CheckoutControllerIntegrationTest {

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
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PaymentServiceImpl paymentService;

    private String customerToken;
    private String adminToken;
    private String warehouseToken;

    private User customerUser;
    private Category category;
    private Product product1;
    private Product product2;
    private Product inactiveProduct;
    private Warehouse warehouse1;
    private Warehouse warehouse2;

    @BeforeEach
    void setUp() {
        paymentService.setForceFailure(false);

        // Delete dependencies in correct order
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

        customerUser = User.builder()
                .name("Customer User")
                .email("customer@test.com")
                .passwordHash("pass")
                .role(UserRole.CUSTOMER)
                .build();
        userRepository.save(customerUser);
        customerToken = jwtService.generateToken(customerUser);

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

        category = Category.builder().name("Electronics").build();
        categoryRepository.save(category);

        product1 = Product.builder()
                .name("Laptop")
                .description("Developer laptop")
                .price(new BigDecimal("1000.00"))
                .category(category)
                .active(true)
                .build();
        productRepository.save(product1);

        product2 = Product.builder()
                .name("Smartphone")
                .description("4G smartphone")
                .price(new BigDecimal("500.00"))
                .category(category)
                .active(true)
                .build();
        productRepository.save(product2);

        inactiveProduct = Product.builder()
                .name("Old Router")
                .description("Legacy router")
                .price(new BigDecimal("50.00"))
                .category(category)
                .active(false)
                .build();
        productRepository.save(inactiveProduct);

        warehouse1 = Warehouse.builder().name("NYC Warehouse").location("New York").build();
        warehouseRepository.save(warehouse1);

        warehouse2 = Warehouse.builder().name("LA Warehouse").location("Los Angeles").build();
        warehouseRepository.save(warehouse2);
    }

    @Test
    void testHappyPathCheckout() throws Exception {
        // Setup inventory
        InventoryItem inv1 = InventoryItem.builder()
                .product(product1)
                .warehouse(warehouse1)
                .quantityOnHand(10)
                .quantityReserved(0)
                .build();
        inventoryItemRepository.save(inv1);

        // Add item to cart
        Cart cart = Cart.builder().customer(customerUser).status(CartStatus.ACTIVE).build();
        cartRepository.save(cart);
        CartItem item = CartItem.builder().cart(cart).product(product1).quantity(2).build();
        cartItemRepository.save(item);

        // Perform checkout
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("PLACED")))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId", is(product1.getId().toString())))
                .andExpect(jsonPath("$.items[0].quantity", is(2)))
                .andExpect(jsonPath("$.items[0].unitPrice", is(1000.00)))
                .andExpect(jsonPath("$.items[0].lineTotal", is(2000.00)))
                .andExpect(jsonPath("$.subtotal", is(2000.00)))
                .andExpect(jsonPath("$.taxAmount", is(200.00))) // 10%
                .andExpect(jsonPath("$.discountAmount", is(0.00)))
                .andExpect(jsonPath("$.totalAmount", is(2200.00)))
                .andExpect(jsonPath("$.paymentStatus", is("SUCCESS")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));

        // Assert cart checked out
        Cart updatedCart = cartRepository.findById(cart.getId()).orElseThrow();
        assertEquals(CartStatus.CHECKED_OUT, updatedCart.getStatus());

        // Assert inventory updated
        InventoryItem updatedInv = inventoryItemRepository.findByProductIdAndWarehouseId(product1.getId(), warehouse1.getId()).orElseThrow();
        assertEquals(2, updatedInv.getQuantityReserved());
    }

    @Test
    void testEmptyCartCheckoutReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testInactiveProductCheckoutReturnsBadRequest() throws Exception {
        Cart cart = Cart.builder().customer(customerUser).status(CartStatus.ACTIVE).build();
        cartRepository.save(cart);
        CartItem item = CartItem.builder().cart(cart).product(inactiveProduct).quantity(1).build();
        cartItemRepository.save(item);

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testNonCustomerRolesForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + warehouseToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPaymentFailureReleasesReservationsAndRollsBack() throws Exception {
        // Setup inventory
        InventoryItem inv = InventoryItem.builder()
                .product(product1)
                .warehouse(warehouse1)
                .quantityOnHand(10)
                .quantityReserved(0)
                .build();
        inventoryItemRepository.save(inv);

        // Setup active cart
        Cart cart = Cart.builder().customer(customerUser).status(CartStatus.ACTIVE).build();
        cartRepository.save(cart);
        CartItem item = CartItem.builder().cart(cart).product(product1).quantity(3).build();
        cartItemRepository.save(item);

        // Force payment failure
        paymentService.setForceFailure(true);

        // Perform checkout
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isPaymentRequired());

        // Assert cart remains ACTIVE due to transaction rollback
        Cart updatedCart = cartRepository.findById(cart.getId()).orElseThrow();
        assertEquals(CartStatus.ACTIVE, updatedCart.getStatus());

        // Assert order and payment records are rolled back (not saved)
        List<Order> orders = orderRepository.findAll();
        assertTrue(orders.isEmpty());

        // Assert inventory reservations are fully released (quantityReserved returns to 0)
        InventoryItem updatedInv = inventoryItemRepository.findByProductIdAndWarehouseId(product1.getId(), warehouse1.getId()).orElseThrow();
        assertEquals(0, updatedInv.getQuantityReserved());
    }

    @Test
    void testWarehouseSplitSelection() throws Exception {
        // Setup inventory across two warehouses: NYC has 4 available, LA has 3 available
        InventoryItem nycInv = InventoryItem.builder()
                .product(product1)
                .warehouse(warehouse1)
                .quantityOnHand(4)
                .quantityReserved(0)
                .build();
        inventoryItemRepository.save(nycInv);

        InventoryItem laInv = InventoryItem.builder()
                .product(product1)
                .warehouse(warehouse2)
                .quantityOnHand(3)
                .quantityReserved(0)
                .build();
        inventoryItemRepository.save(laInv);

        // Request 5 units (exceeds individual warehouse stock, but sufficient aggregate stock)
        Cart cart = Cart.builder().customer(customerUser).status(CartStatus.ACTIVE).build();
        cartRepository.save(cart);
        CartItem item = CartItem.builder().cart(cart).product(product1).quantity(5).build();
        cartItemRepository.save(item);

        // Perform checkout
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(2))); // NYC (4) + LA (1)

        // NYC (highest stock, available=4) should be fully reserved
        InventoryItem updatedNyc = inventoryItemRepository.findByProductIdAndWarehouseId(product1.getId(), warehouse1.getId()).orElseThrow();
        assertEquals(4, updatedNyc.getQuantityReserved());

        // LA should have 1 reserved
        InventoryItem updatedLa = inventoryItemRepository.findByProductIdAndWarehouseId(product1.getId(), warehouse2.getId()).orElseThrow();
        assertEquals(1, updatedLa.getQuantityReserved());
    }

    @Test
    void testConcurrencySingleWarehouse() throws Exception {
        // Known low stock: 5 units in warehouse 1
        InventoryItem inv = InventoryItem.builder()
                .product(product1)
                .warehouse(warehouse1)
                .quantityOnHand(5)
                .quantityReserved(0)
                .build();
        inventoryItemRepository.save(inv);

        // Create 10 customer accounts, each with 1 laptop in cart
        int threadsCount = 10;
        List<String> tokens = new ArrayList<>();
        List<User> customers = new ArrayList<>();
        for (int i = 0; i < threadsCount; i++) {
            User customer = User.builder()
                    .name("Concurrent Customer " + i)
                    .email("concurrent" + i + "@test.com")
                    .passwordHash("pass")
                    .role(UserRole.CUSTOMER)
                    .build();
            userRepository.save(customer);
            customers.add(customer);
            tokens.add(jwtService.generateToken(customer));

            Cart cart = Cart.builder().customer(customer).status(CartStatus.ACTIVE).build();
            cartRepository.save(cart);
            CartItem item = CartItem.builder().cart(cart).product(product1).quantity(1).build();
            cartItemRepository.save(item);
        }

        // Fire 10 concurrent requests
        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadsCount);
        List<MvcResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadsCount; i++) {
            final String token = tokens.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(post("/api/v1/orders/checkout")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                            .andReturn();
                    results.add(result);
                } catch (Exception e) {
                    log.error("Concurrent checkout thread exception", e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // GO!
        endLatch.await();
        executor.shutdown();

        // Count successful vs failed checkouts
        int successCount = 0;
        int failCount = 0;
        for (MvcResult result : results) {
            int status = result.getResponse().getStatus();
            if (status == 201) {
                successCount++;
            } else if (status == 422 || status == 503) {
                failCount++;
            } else {
                log.warn("Unexpected HTTP status in concurrent checkout: {}", status);
            }
        }

        log.info("Concurrent test results: Success: {}, Failed: {}", successCount, failCount);

        // Assert exactly 5 succeed, and exactly 5 fail
        assertEquals(5, successCount, "Exactly 5 orders should succeed");
        assertEquals(5, failCount, "Exactly 5 orders should fail");

        // Assert final quantityReserved is exactly 5
        InventoryItem finalInv = inventoryItemRepository.findByProductIdAndWarehouseId(product1.getId(), warehouse1.getId()).orElseThrow();
        assertEquals(5, finalInv.getQuantityReserved(), "Quantity reserved should equal exactly 5");
        assertEquals(5, finalInv.getQuantityOnHand(), "Quantity on hand must be untouched (5)");
    }

    @Test
    void testConcurrencyTwoWarehouses() throws Exception {
        // Stock split: NYC has 3, LA has 2 (total 5)
        InventoryItem nycInv = InventoryItem.builder()
                .product(product1)
                .warehouse(warehouse1)
                .quantityOnHand(3)
                .quantityReserved(0)
                .build();
        inventoryItemRepository.save(nycInv);

        InventoryItem laInv = InventoryItem.builder()
                .product(product1)
                .warehouse(warehouse2)
                .quantityOnHand(2)
                .quantityReserved(0)
                .build();
        inventoryItemRepository.save(laInv);

        // Create 10 customer accounts, each with 1 laptop in cart
        int threadsCount = 10;
        List<String> tokens = new ArrayList<>();
        List<User> customers = new ArrayList<>();
        for (int i = 0; i < threadsCount; i++) {
            User customer = User.builder()
                    .name("Concurrent Customer Two Wh " + i)
                    .email("concurrent_wh" + i + "@test.com")
                    .passwordHash("pass")
                    .role(UserRole.CUSTOMER)
                    .build();
            userRepository.save(customer);
            customers.add(customer);
            tokens.add(jwtService.generateToken(customer));

            Cart cart = Cart.builder().customer(customer).status(CartStatus.ACTIVE).build();
            cartRepository.save(cart);
            CartItem item = CartItem.builder().cart(cart).product(product1).quantity(1).build();
            cartItemRepository.save(item);
        }

        // Fire 10 concurrent requests
        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadsCount);
        List<MvcResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadsCount; i++) {
            final String token = tokens.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(post("/api/v1/orders/checkout")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                            .andReturn();
                    results.add(result);
                } catch (Exception e) {
                    log.error("Concurrent checkout thread exception", e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // GO!
        endLatch.await();
        executor.shutdown();

        // Count successful vs failed checkouts
        int successCount = 0;
        int failCount = 0;
        for (MvcResult result : results) {
            int status = result.getResponse().getStatus();
            if (status == 201) {
                successCount++;
            } else if (status == 422 || status == 503) {
                failCount++;
            }
        }

        log.info("Concurrent split test results: Success: {}, Failed: {}", successCount, failCount);

        // Assert exactly 5 succeed, and exactly 5 fail
        assertEquals(5, successCount, "Exactly 5 orders should succeed");
        assertEquals(5, failCount, "Exactly 5 orders should fail");

        // Assert NYC and LA have both been fully reserved
        InventoryItem finalNyc = inventoryItemRepository.findByProductIdAndWarehouseId(product1.getId(), warehouse1.getId()).orElseThrow();
        assertEquals(3, finalNyc.getQuantityReserved(), "NYC should be fully reserved (3)");

        InventoryItem finalLa = inventoryItemRepository.findByProductIdAndWarehouseId(product1.getId(), warehouse2.getId()).orElseThrow();
        assertEquals(2, finalLa.getQuantityReserved(), "LA should be fully reserved (2)");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
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
    }
}
