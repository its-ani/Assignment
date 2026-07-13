package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Order;
import com.ecommerce.oms.domain.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefundCalculationServiceTest {

    private RefundCalculationService service;

    @BeforeEach
    void setUp() {
        service = new RefundCalculationService();
    }

    @Test
    void testProportionalRefundWithDiscount() {
        // subtotal = 50.00
        // discount = 5.00
        // post discount = 45.00
        // tax = 3.60
        // total = 48.60
        // Item A: $10.00 each, qty = 2 (subtotal = 20.00)
        // Item B: $30.00 each, qty = 1 (subtotal = 30.00)
        // Return 1 unit of A. Refund = 48.60 * (10.00 * 1) / 50.00 = 9.72

        Order order = Order.builder()
                .discountAmount(new BigDecimal("5.00"))
                .taxAmount(new BigDecimal("3.60"))
                .totalAmount(new BigDecimal("48.60"))
                .build();

        OrderItem itemA = OrderItem.builder()
                .unitPrice(new BigDecimal("10.00"))
                .quantity(2)
                .build();

        OrderItem itemB = OrderItem.builder()
                .unitPrice(new BigDecimal("30.00"))
                .quantity(1)
                .build();

        List<OrderItem> allItems = List.of(itemA, itemB);

        BigDecimal refund = service.calculateRefundAmount(order, itemA, allItems, 1);
        assertEquals(new BigDecimal("9.72"), refund);
    }

    @Test
    void testProportionalRefundWithoutDiscount() {
        // subtotal = 50.00
        // discount = 0.00
        // tax = 5.00
        // total = 55.00
        // Item A: $10.00, qty = 2
        // Item B: $30.00, qty = 1
        // Return 2 units of A. Refund = 55.00 * 20.00 / 50.00 = 22.00

        Order order = Order.builder()
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(new BigDecimal("5.00"))
                .totalAmount(new BigDecimal("55.00"))
                .build();

        OrderItem itemA = OrderItem.builder()
                .unitPrice(new BigDecimal("10.00"))
                .quantity(2)
                .build();

        OrderItem itemB = OrderItem.builder()
                .unitPrice(new BigDecimal("30.00"))
                .quantity(1)
                .build();

        List<OrderItem> allItems = List.of(itemA, itemB);

        BigDecimal refund = service.calculateRefundAmount(order, itemA, allItems, 2);
        assertEquals(new BigDecimal("22.00"), refund);
    }

    @Test
    void testProportionalRefundWith100PercentDiscount() {
        // total = 0.00
        Order order = Order.builder()
                .discountAmount(new BigDecimal("50.00"))
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .build();

        OrderItem itemA = OrderItem.builder()
                .unitPrice(new BigDecimal("25.00"))
                .quantity(2)
                .build();

        List<OrderItem> allItems = List.of(itemA);

        BigDecimal refund = service.calculateRefundAmount(order, itemA, allItems, 1);
        assertEquals(new BigDecimal("0.00"), refund);
    }

    @Test
    void testInvalidQuantityReturnsZero() {
        Order order = Order.builder().totalAmount(new BigDecimal("100.00")).build();
        OrderItem item = OrderItem.builder().unitPrice(new BigDecimal("10.00")).quantity(5).build();

        BigDecimal refund = service.calculateRefundAmount(order, item, List.of(item), 0);
        assertEquals(new BigDecimal("0.00"), refund);
    }
}
