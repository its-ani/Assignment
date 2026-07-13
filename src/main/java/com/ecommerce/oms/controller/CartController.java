package com.ecommerce.oms.controller;

import com.ecommerce.oms.dto.AddCartItemRequest;
import com.ecommerce.oms.dto.CartResponse;
import com.ecommerce.oms.dto.UpdateCartItemRequest;
import com.ecommerce.oms.security.SecurityUtils;
import com.ecommerce.oms.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    private UUID getAuthenticatedCustomerId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Access denied: User is not authenticated"));
    }

    @GetMapping
    public ResponseEntity<CartResponse> getCart() {
        UUID customerId = getAuthenticatedCustomerId();
        CartResponse cart = cartService.getCart(customerId);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest request) {
        UUID customerId = getAuthenticatedCustomerId();
        CartResponse cart = cartService.addItem(customerId, request);
        return ResponseEntity.ok(cart);
    }

    @PatchMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        UUID customerId = getAuthenticatedCustomerId();
        CartResponse cart = cartService.updateItemQuantity(customerId, productId, request.getQuantity());
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(@PathVariable UUID productId) {
        UUID customerId = getAuthenticatedCustomerId();
        cartService.removeItem(customerId, productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        UUID customerId = getAuthenticatedCustomerId();
        cartService.clearCart(customerId);
        return ResponseEntity.noContent().build();
    }
}
