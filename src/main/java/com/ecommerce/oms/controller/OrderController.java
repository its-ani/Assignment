package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.OrderStatus;
import com.ecommerce.oms.dto.*;
import com.ecommerce.oms.security.SecurityUtils;
import com.ecommerce.oms.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    private UUID getAuthenticatedCustomerId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Access denied: User is not authenticated"));
    }

    private String getAuthenticatedUserEmail() {
        return SecurityUtils.getCurrentUserEmail()
                .orElse("UNKNOWN");
    }

    private boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Page<OrderSummaryResponse>> getMyOrders(
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {
        UUID customerId = getAuthenticatedCustomerId();
        Page<OrderSummaryResponse> orders = orderService.getCustomerOrders(customerId, status, pageable);
        // Strip customerId from response for customers
        orders.forEach(o -> o.setCustomerId(null));
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> getMyOrderDetail(@PathVariable UUID id) {
        UUID customerId = getAuthenticatedCustomerId();
        OrderResponse order = orderService.getCustomerOrderDetail(customerId, id);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> cancelMyOrder(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) OrderCancelRequest cancelRequest) {
        UUID customerId = getAuthenticatedCustomerId();
        String reason = cancelRequest != null ? cancelRequest.getReason() : null;
        OrderResponse order = orderService.cancelOrder(id, customerId, reason);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
    public ResponseEntity<Page<OrderSummaryResponse>> getAllOrdersForStaff(
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {
        Page<OrderSummaryResponse> orders = orderService.getOrdersByStatus(status, pageable);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/staff/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
    public ResponseEntity<OrderResponse> getOrderDetailForStaff(@PathVariable UUID id) {
        OrderResponse order = orderService.getOrderDetail(id);
        return ResponseEntity.ok(order);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable UUID id,
            @Valid @RequestBody OrderStatusUpdateRequest updateRequest) {
        String actor = getAuthenticatedUserEmail();
        boolean isAdmin = isCurrentUserAdmin();
        OrderResponse order = orderService.updateStatus(id, updateRequest.getNewStatus(), actor, isAdmin);
        return ResponseEntity.ok(order);
    }
}
