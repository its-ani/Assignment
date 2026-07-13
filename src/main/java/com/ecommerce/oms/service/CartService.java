package com.ecommerce.oms.service;

import com.ecommerce.oms.dto.AddCartItemRequest;
import com.ecommerce.oms.dto.CartResponse;

import java.util.UUID;

public interface CartService {
    CartResponse getCart(UUID customerId);
    CartResponse addItem(UUID customerId, AddCartItemRequest request);
    CartResponse updateItemQuantity(UUID customerId, UUID productId, int quantity);
    CartResponse removeItem(UUID customerId, UUID productId);
    CartResponse clearCart(UUID customerId);
}
