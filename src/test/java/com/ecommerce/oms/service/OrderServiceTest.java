package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.*;
import com.ecommerce.oms.dto.OrderResponse;
import com.ecommerce.oms.exception.InvalidOrderStatusTransitionException;
import com.ecommerce.oms.repository.OrderItemRepository;
import com.ecommerce.oms.repository.OrderRepository;
import com.ecommerce.oms.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InventoryReservationService inventoryReservationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order createOrder(OrderStatus status) {
        User customer = User.builder()
                .id(UUID.randomUUID())
                .email("cust@test.com")
                .role(UserRole.CUSTOMER)
                .build();

        return Order.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .status(status)
                .taxAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00"))
                .build();
    }

    @Test
    void testStaffValidTransitions() {
        Order order = createOrder(OrderStatus.PLACED);
        UUID orderId = order.getId();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(Collections.emptyList());
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        // PLACED -> CONFIRMED
        OrderResponse res1 = orderService.updateStatus(orderId, OrderStatus.CONFIRMED, "staff", false);
        assertEquals(OrderStatus.CONFIRMED, res1.getStatus());

        // CONFIRMED -> PACKED
        order.setStatus(OrderStatus.CONFIRMED);
        OrderResponse res2 = orderService.updateStatus(orderId, OrderStatus.PACKED, "staff", false);
        assertEquals(OrderStatus.PACKED, res2.getStatus());

        // PACKED -> SHIPPED
        order.setStatus(OrderStatus.PACKED);
        OrderResponse res3 = orderService.updateStatus(orderId, OrderStatus.SHIPPED, "staff", false);
        assertEquals(OrderStatus.SHIPPED, res3.getStatus());

        // SHIPPED -> DELIVERED
        order.setStatus(OrderStatus.SHIPPED);
        OrderResponse res4 = orderService.updateStatus(orderId, OrderStatus.DELIVERED, "staff", false);
        assertEquals(OrderStatus.DELIVERED, res4.getStatus());
    }

    @Test
    void testStaffInvalidTransitionThrowsConflict() {
        Order order = createOrder(OrderStatus.PLACED);
        UUID orderId = order.getId();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // PLACED -> SHIPPED is invalid transition
        assertThrows(InvalidOrderStatusTransitionException.class, () -> {
            orderService.updateStatus(orderId, OrderStatus.SHIPPED, "staff", false);
        });
    }

    @Test
    void testStaffCannotPerformAdminCancellation() {
        Order order = createOrder(OrderStatus.PACKED);
        UUID orderId = order.getId();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // Staff is not allowed to transition PACKED -> CANCELLED (only ADMIN can force cancel)
        assertThrows(AccessDeniedException.class, () -> {
            orderService.updateStatus(orderId, OrderStatus.CANCELLED, "staff", false);
        });
    }

    @Test
    void testAdminBypassesTransitionsForForceCancel() {
        Order order = createOrder(OrderStatus.PACKED);
        UUID orderId = order.getId();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Admin transitions PACKED -> CANCELLED (force-cancel)
        OrderResponse res = orderService.updateStatus(orderId, OrderStatus.CANCELLED, "admin", true);
        assertEquals(OrderStatus.CANCELLED, res.getStatus());
    }

    @Test
    void testCustomerSelfCancelPlacedOrConfirmed() {
        Order order = createOrder(OrderStatus.PLACED);
        UUID orderId = order.getId();
        UUID customerId = order.getCustomer().getId();

        Product product = Product.builder().id(UUID.randomUUID()).name("P1").build();
        Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).name("W1").build();
        OrderItem item = OrderItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantity(3)
                .unitPrice(BigDecimal.TEN)
                .build();

        when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));

        OrderResponse res = orderService.cancelOrder(orderId, customerId, "changed my mind");
        assertEquals(OrderStatus.CANCELLED, res.getStatus());

        // Verify inventory reservation was released
        verify(inventoryReservationService).releaseReservation(product.getId(), warehouse.getId(), 3);
    }

    @Test
    void testCustomerSelfCancelPackedThrowsConflict() {
        Order order = createOrder(OrderStatus.PACKED);
        UUID orderId = order.getId();
        UUID customerId = order.getCustomer().getId();

        when(orderRepository.findByIdAndCustomerId(orderId, customerId)).thenReturn(Optional.of(order));

        // Customer cannot cancel after PACKED
        assertThrows(InvalidOrderStatusTransitionException.class, () -> {
            orderService.cancelOrder(orderId, customerId, "cancel please");
        });
    }
}
