package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.*;
import com.ecommerce.oms.dto.OrderItemResponse;
import com.ecommerce.oms.dto.OrderResponse;
import com.ecommerce.oms.dto.OrderSummaryResponse;
import com.ecommerce.oms.event.OrderStatusChangedEvent;
import com.ecommerce.oms.exception.InvalidOrderStatusTransitionException;
import com.ecommerce.oms.exception.ResourceNotFoundException;
import com.ecommerce.oms.repository.OrderItemRepository;
import com.ecommerce.oms.repository.OrderRepository;
import com.ecommerce.oms.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryReservationService inventoryReservationService;
    private final ApplicationEventPublisher eventPublisher;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PLACED, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.PACKED, OrderStatus.CANCELLED),
            OrderStatus.PACKED, Set.of(OrderStatus.SHIPPED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of(),
            OrderStatus.RETURNED, Set.of()
    );

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getCustomerOrders(UUID customerId, OrderStatus status, Pageable pageable) {
        log.info("Fetching orders for customer: {} with status filter: {}", customerId, status);
        Page<Order> orders;
        if (status != null) {
            orders = orderRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        } else {
            orders = orderRepository.findByCustomerId(customerId, pageable);
        }
        return orders.map(this::mapToSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getCustomerOrderDetail(UUID customerId, UUID orderId) {
        log.info("Fetching order detail for customer: {}, order: {}", customerId, orderId);
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        return mapToResponse(order, items, payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        log.info("Fetching orders by status: {}", status);
        Page<Order> orders;
        if (status != null) {
            orders = orderRepository.findByStatus(status, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }
        return orders.map(this::mapToSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetail(UUID orderId) {
        log.info("Fetching order detail: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        return mapToResponse(order, items, payment);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(UUID orderId, OrderStatus newStatus, String actorName, boolean isAdmin) {
        log.info("Updating order: {} status to: {} by actor: {} (isAdmin={})", orderId, newStatus, actorName, isAdmin);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        if (oldStatus == newStatus) {
            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
            return mapToResponse(order, items, payment);
        }

        if (!isAdmin && newStatus == OrderStatus.CANCELLED) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Warehouse staff is not authorized to cancel orders.");
        }

        // Validate state transitions
        if (newStatus == OrderStatus.CANCELLED && isAdmin) {
            if (oldStatus == OrderStatus.DELIVERED || oldStatus == OrderStatus.RETURNED) {
                throw new InvalidOrderStatusTransitionException(
                        "Transition from " + oldStatus + " to " + newStatus + " is invalid.");
            }
        } else {
            Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(oldStatus, Set.of());
            if (!allowed.contains(newStatus)) {
                throw new InvalidOrderStatusTransitionException(
                        "Transition from " + oldStatus + " to " + newStatus + " is invalid.");
            }
        }

        // Enforce role-based restrictions
        if (!isAdmin) {
            boolean validStaffTransition = 
                    (oldStatus == OrderStatus.PLACED && newStatus == OrderStatus.CONFIRMED) ||
                    (oldStatus == OrderStatus.CONFIRMED && newStatus == OrderStatus.PACKED) ||
                    (oldStatus == OrderStatus.PACKED && newStatus == OrderStatus.SHIPPED) ||
                    (oldStatus == OrderStatus.SHIPPED && newStatus == OrderStatus.DELIVERED);
            if (!validStaffTransition) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Warehouse staff is not authorized to transition order from " + oldStatus + " to " + newStatus);
            }
        }

        // Handle inventory release if cancelling
        if (newStatus == OrderStatus.CANCELLED) {
            releaseInventoryReservations(orderId);
        }

        // Handle inventory fulfillment on shipment: decrement quantityOnHand and release quantityReserved
        if (newStatus == OrderStatus.SHIPPED) {
            fulfillInventoryOnShipment(orderId);
        }

        if (newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(Instant.now());
        }

        order.setStatus(newStatus);
        order = orderRepository.save(order);

        // Publish event
        eventPublisher.publishEvent(OrderStatusChangedEvent.builder()
                .orderId(order.getId())
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .actor(actorName)
                .timestamp(Instant.now())
                .build());

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        return mapToResponse(order, items, payment);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, UUID customerId, String reason) {
        log.info("Customer cancel request for order: {} by customer: {}, reason: {}", orderId, customerId, reason);
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        if (oldStatus == OrderStatus.CANCELLED) {
            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
            return mapToResponse(order, items, payment);
        }

        if (oldStatus != OrderStatus.PLACED && oldStatus != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStatusTransitionException(
                    "Order cannot be cancelled once it is in " + oldStatus + " status.");
        }

        releaseInventoryReservations(orderId);
        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);

        // Publish event
        eventPublisher.publishEvent(OrderStatusChangedEvent.builder()
                .orderId(order.getId())
                .oldStatus(oldStatus)
                .newStatus(OrderStatus.CANCELLED)
                .actor("CUSTOMER_" + customerId + (reason != null && !reason.isBlank() ? " (Reason: " + reason + ")" : ""))
                .timestamp(Instant.now())
                .build());

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        return mapToResponse(order, items, payment);
    }

    @Override
    @Transactional
    public OrderResponse adminCancelOrder(UUID orderId, String reason) {
        log.info("Admin cancel request for order: {}, reason: {}", orderId, reason);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        if (oldStatus == OrderStatus.CANCELLED) {
            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
            return mapToResponse(order, items, payment);
        }

        if (oldStatus == OrderStatus.DELIVERED || oldStatus == OrderStatus.RETURNED) {
            throw new InvalidOrderStatusTransitionException(
                    "Delivered or returned order cannot be cancelled.");
        }

        releaseInventoryReservations(orderId);
        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);

        // Publish event
        eventPublisher.publishEvent(OrderStatusChangedEvent.builder()
                .orderId(order.getId())
                .oldStatus(oldStatus)
                .newStatus(OrderStatus.CANCELLED)
                .actor("ADMIN" + (reason != null && !reason.isBlank() ? " (Reason: " + reason + ")" : ""))
                .timestamp(Instant.now())
                .build());

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        return mapToResponse(order, items, payment);
    }

    private void releaseInventoryReservations(UUID orderId) {
        log.info("Releasing reserved inventory items for cancelled order: {}", orderId);
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            if (item.getWarehouse() != null) {
                inventoryReservationService.releaseReservation(
                        item.getProduct().getId(),
                        item.getWarehouse().getId(),
                        item.getQuantity()
                );
            }
        }
    }

    private void fulfillInventoryOnShipment(UUID orderId) {
        log.info("Fulfilling inventory for shipped order: {}. Decrementing quantityOnHand and quantityReserved.", orderId);
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            if (item.getWarehouse() != null) {
                inventoryReservationService.fulfillReservation(
                        item.getProduct().getId(),
                        item.getWarehouse().getId(),
                        item.getQuantity()
                );
            }
        }
    }

    private OrderSummaryResponse mapToSummaryResponse(Order order) {
        int itemCount = orderItemRepository.findByOrderId(order.getId()).stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
        return OrderSummaryResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .itemCount(itemCount)
                .deliveredAt(order.getDeliveredAt())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderResponse mapToResponse(Order order, List<OrderItem> orderItems, Payment payment) {
        List<OrderItemResponse> itemResponses = orderItems.stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .warehouseId(item.getWarehouse() != null ? item.getWarehouse().getId() : null)
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .lineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .collect(Collectors.toList());

        BigDecimal subtotal = itemResponses.stream()
                .map(OrderItemResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .items(itemResponses)
                .subtotal(subtotal)
                .taxAmount(order.getTaxAmount())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .paymentStatus(payment != null ? payment.getStatus() : null)
                .deliveredAt(order.getDeliveredAt())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
