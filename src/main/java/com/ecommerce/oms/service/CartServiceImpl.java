package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Cart;
import com.ecommerce.oms.domain.CartItem;
import com.ecommerce.oms.domain.CartStatus;
import com.ecommerce.oms.domain.Product;
import com.ecommerce.oms.domain.User;
import com.ecommerce.oms.dto.AddCartItemRequest;
import com.ecommerce.oms.dto.CartItemResponse;
import com.ecommerce.oms.dto.CartResponse;
import com.ecommerce.oms.exception.ResourceNotFoundException;
import com.ecommerce.oms.repository.*;
import com.ecommerce.oms.security.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public CartServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            InventoryItemRepository inventoryItemRepository
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    private void verifyOwnership(UUID customerId) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Access denied: User is not authenticated"));
        if (!currentUserId.equals(customerId)) {
            throw new AccessDeniedException("Access denied: You do not own this cart");
        }
    }

    private Cart getOrCreateActiveCart(UUID customerId) {
        return cartRepository.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE)
                .orElseGet(() -> {
                    User customer = userRepository.findById(customerId)
                            .orElseThrow(() -> new ResourceNotFoundException("Customer not found with ID: " + customerId));
                    Cart newCart = Cart.builder()
                            .customer(customer)
                            .status(CartStatus.ACTIVE)
                            .items(new ArrayList<>())
                            .build();
                    return cartRepository.save(newCart);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(UUID customerId) {
        verifyOwnership(customerId);
        Cart cart = getOrCreateActiveCart(customerId);
        return mapToResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(UUID customerId, AddCartItemRequest request) {
        verifyOwnership(customerId);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + request.getProductId()));

        if (!product.isActive()) {
            throw new ResourceNotFoundException("Product not found with ID: " + request.getProductId());
        }

        Cart cart = getOrCreateActiveCart(customerId);

        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst();

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            existingItem.setQuantity(existingItem.getQuantity() + request.getQuantity());
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cart.getItems().add(newItem);
        }

        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public CartResponse updateItemQuantity(UUID customerId, UUID productId, int quantity) {
        verifyOwnership(customerId);
        Cart cart = getOrCreateActiveCart(customerId);

        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product not found in cart: " + productId));

        if (quantity == 0) {
            cart.getItems().remove(existingItem);
        } else {
            existingItem.setQuantity(quantity);
        }

        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public CartResponse removeItem(UUID customerId, UUID productId) {
        verifyOwnership(customerId);
        Cart cart = getOrCreateActiveCart(customerId);

        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product not found in cart: " + productId));

        cart.getItems().remove(existingItem);
        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public CartResponse clearCart(UUID customerId) {
        verifyOwnership(customerId);
        Cart cart = getOrCreateActiveCart(customerId);
        cart.getItems().clear();
        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    private CartResponse mapToResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::mapToItemResponse)
                .collect(Collectors.toList());

        BigDecimal subtotal = itemResponses.stream()
                .map(CartItemResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        int itemCount = itemResponses.stream()
                .mapToInt(CartItemResponse::getQuantity)
                .sum();

        return CartResponse.builder()
                .id(cart.getId())
                .status(cart.getStatus())
                .items(itemResponses)
                .subtotal(subtotal)
                .itemCount(itemCount)
                .build();
    }

    private CartItemResponse mapToItemResponse(CartItem item) {
        Product product = item.getProduct();
        BigDecimal unitPrice = product.getPrice();
        int qty = item.getQuantity();
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));

        List<com.ecommerce.oms.domain.InventoryItem> invItems = inventoryItemRepository.findByProductId(product.getId());
        int totalAvailable = invItems.stream()
                .mapToInt(inv -> inv.getQuantityOnHand() - inv.getQuantityReserved())
                .sum();

        String warning = null;
        if (totalAvailable <= 0) {
            warning = "Product is out of stock";
        } else if (qty > totalAvailable) {
            warning = "Only " + totalAvailable + " available in stock";
        }

        return CartItemResponse.builder()
                .id(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .unitPrice(unitPrice)
                .quantity(qty)
                .lineTotal(lineTotal)
                .availabilityWarning(warning)
                .build();
    }
}
