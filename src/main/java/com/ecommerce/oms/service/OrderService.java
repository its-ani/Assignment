package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.OrderStatus;
import com.ecommerce.oms.dto.OrderResponse;
import com.ecommerce.oms.dto.OrderSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {
    Page<OrderSummaryResponse> getCustomerOrders(UUID customerId, OrderStatus status, Pageable pageable);
    OrderResponse getCustomerOrderDetail(UUID customerId, UUID orderId);
    Page<OrderSummaryResponse> getOrdersByStatus(OrderStatus status, Pageable pageable);
    OrderResponse getOrderDetail(UUID orderId);
    OrderResponse updateStatus(UUID orderId, OrderStatus newStatus, String actorName, boolean isAdmin);
    OrderResponse cancelOrder(UUID orderId, UUID customerId, String reason);
    OrderResponse adminCancelOrder(UUID orderId, String reason);
}
