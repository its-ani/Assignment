package com.ecommerce.oms.controller;

import com.ecommerce.oms.dto.CheckoutRequest;
import com.ecommerce.oms.dto.OrderResponse;
import com.ecommerce.oms.security.SecurityUtils;
import com.ecommerce.oms.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasRole('CUSTOMER')")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    private UUID getAuthenticatedCustomerId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Access denied: User is not authenticated"));
    }

    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(@Valid @RequestBody(required = false) CheckoutRequest request) {
        UUID customerId = getAuthenticatedCustomerId();
        CheckoutRequest req = request != null ? request : new CheckoutRequest();
        OrderResponse response = checkoutService.checkout(customerId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
