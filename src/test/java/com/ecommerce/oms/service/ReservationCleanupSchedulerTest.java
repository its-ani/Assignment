package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.*;
import com.ecommerce.oms.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test for {@link ReservationCleanupScheduler}.
 * Verifies that orphaned reservations (quantityReserved not backed by active orders)
 * are detected and corrected.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationCleanupSchedulerTest {

    @Autowired
    private ReservationCleanupScheduler cleanupScheduler;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Product product;
    private Warehouse warehouse;
    private InventoryItem inventoryItem;
    private User customer;

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

        customer = User.builder()
                .name("Test Customer")
                .email("cleanup-test@test.com")
                .passwordHash("pass")
                .role(UserRole.CUSTOMER)
                .build();
        userRepository.save(customer);

        Category category = Category.builder().name("TestCat").build();
        categoryRepository.save(category);

        product = Product.builder()
                .name("Cleanup Test Product")
                .description("For testing reservation cleanup")
                .price(new BigDecimal("10.00"))
                .category(category)
                .active(true)
                .build();
        productRepository.save(product);

        warehouse = Warehouse.builder().name("Test WH").location("Test Location").build();
        warehouseRepository.save(warehouse);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void testOrphanedReservationIsCleanedUp() {
        // Create inventory with quantityReserved=5 but NO backing orders
        inventoryItem = InventoryItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(100)
                .quantityReserved(5) // Orphaned — no orders exist
                .build();
        inventoryItemRepository.save(inventoryItem);

        // Run the cleanup
        cleanupScheduler.cleanupOrphanedReservations();

        // Verify: quantityReserved should be corrected to 0
        InventoryItem updated = inventoryItemRepository.findByProductIdAndWarehouseId(
                product.getId(), warehouse.getId()).orElseThrow();
        assertEquals(0, updated.getQuantityReserved(),
                "Orphaned reservation should be released to 0 when no active orders exist");
        assertEquals(100, updated.getQuantityOnHand(),
                "quantityOnHand should remain unchanged");
    }

    @Test
    void testPartialOrphanedReservationIsCorrected() {
        // Create inventory with quantityReserved=5
        inventoryItem = InventoryItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(100)
                .quantityReserved(5) // 3 backed by order, 2 orphaned
                .build();
        inventoryItemRepository.save(inventoryItem);

        // Create one active order with 3 items
        Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.PLACED)
                .taxAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("30.00"))
                .build();
        orderRepository.save(order);

        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .product(product)
                .warehouse(warehouse)
                .quantity(3)
                .unitPrice(new BigDecimal("10.00"))
                .build();
        orderItemRepository.save(orderItem);

        // Run the cleanup
        cleanupScheduler.cleanupOrphanedReservations();

        // Verify: quantityReserved should be corrected from 5 to 3
        InventoryItem updated = inventoryItemRepository.findByProductIdAndWarehouseId(
                product.getId(), warehouse.getId()).orElseThrow();
        assertEquals(3, updated.getQuantityReserved(),
                "quantityReserved should be corrected to match active order total (3)");
    }

    @Test
    void testNoOrphanWhenReservationMatchesActiveOrders() {
        // Create inventory with quantityReserved=3, fully backed by an active order
        inventoryItem = InventoryItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(100)
                .quantityReserved(3)
                .build();
        inventoryItemRepository.save(inventoryItem);

        // Create an active order backing the reservation
        Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.CONFIRMED)
                .taxAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("30.00"))
                .build();
        orderRepository.save(order);

        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .product(product)
                .warehouse(warehouse)
                .quantity(3)
                .unitPrice(new BigDecimal("10.00"))
                .build();
        orderItemRepository.save(orderItem);

        // Run the cleanup
        cleanupScheduler.cleanupOrphanedReservations();

        // Verify: no change — reservation is legitimate
        InventoryItem updated = inventoryItemRepository.findByProductIdAndWarehouseId(
                product.getId(), warehouse.getId()).orElseThrow();
        assertEquals(3, updated.getQuantityReserved(),
                "quantityReserved should remain unchanged when fully backed by active orders");
    }

    @Test
    void testShippedOrdersDoNotCountAsActiveReservations() {
        // Create inventory with quantityReserved=3
        inventoryItem = InventoryItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(100)
                .quantityReserved(3) // Should be orphaned because the order is SHIPPED (already fulfilled)
                .build();
        inventoryItemRepository.save(inventoryItem);

        // Create a SHIPPED order — should NOT count as an active reservation
        Order order = Order.builder()
                .customer(customer)
                .status(OrderStatus.SHIPPED)
                .taxAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("30.00"))
                .build();
        orderRepository.save(order);

        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .product(product)
                .warehouse(warehouse)
                .quantity(3)
                .unitPrice(new BigDecimal("10.00"))
                .build();
        orderItemRepository.save(orderItem);

        // Run the cleanup
        cleanupScheduler.cleanupOrphanedReservations();

        // Verify: SHIPPED orders don't count, so quantityReserved should be cleaned to 0
        InventoryItem updated = inventoryItemRepository.findByProductIdAndWarehouseId(
                product.getId(), warehouse.getId()).orElseThrow();
        assertEquals(0, updated.getQuantityReserved(),
                "SHIPPED orders should not count as active reservations; quantityReserved should be 0");
    }
}
