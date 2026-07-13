package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.*;
import com.ecommerce.oms.dto.*;
import com.ecommerce.oms.exception.*;
import com.ecommerce.oms.repository.*;
import com.ecommerce.oms.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final InventoryReservationService inventoryReservationService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;
    private final DiscountValidationService discountValidationService;
    private final TaxCalculationService taxCalculationService;

    private static class ReservedItemTracker {
        private final UUID productId;
        private final UUID warehouseId;
        private final int quantity;
        private final Product product;

        public ReservedItemTracker(UUID productId, UUID warehouseId, int quantity, Product product) {
            this.productId = productId;
            this.warehouseId = warehouseId;
            this.quantity = quantity;
            this.product = product;
        }
    }

    @Override
    @Transactional
    public OrderResponse checkout(UUID customerId, CheckoutRequest request) {
        log.info("Starting checkout for customer: {}", customerId);

        // 1. Fetch customer's active cart using pessimistic write lock
        Cart cart = cartRepository.findActiveCartWithWriteLock(customerId)
                .orElseThrow(() -> new IllegalArgumentException("No active cart found"));

        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        // Validate that every product is active
        for (CartItem item : cart.getItems()) {
            if (!item.getProduct().isActive()) {
                throw new IllegalArgumentException("Product " + item.getProduct().getName() + " is inactive and cannot be checked out.");
            }
        }

        // 2. Transition cart to CHECKED_OUT to prevent double-submission
        cart.setStatus(CartStatus.CHECKED_OUT);
        cartRepository.saveAndFlush(cart);

        List<ReservedItemTracker> reservedTrackers = new ArrayList<>();

        try {
            // 3. Reserve stock per cart item (running in REQUIRES_NEW transaction)
            for (CartItem item : cart.getItems()) {
                Product product = item.getProduct();
                int qtyNeeded = item.getQuantity();

                List<ReservationDetail> splits = inventoryReservationService.reserveProductStock(product.getId(), qtyNeeded);
                for (ReservationDetail split : splits) {
                    reservedTrackers.add(new ReservedItemTracker(product.getId(), split.getWarehouseId(), split.getQuantity(), product));
                }
            }

            // 4. Compute order totals
            BigDecimal subtotal = BigDecimal.ZERO;
            for (CartItem item : cart.getItems()) {
                BigDecimal itemPrice = item.getProduct().getPrice();
                subtotal = subtotal.add(itemPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

            BigDecimal discountAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            String appliedDiscountCode = null;
            if (request.getDiscountCode() != null && !request.getDiscountCode().trim().isEmpty()) {
                Discount discount = discountValidationService.validate(request.getDiscountCode(), subtotal, customerId);
                appliedDiscountCode = discount.getCode();
                if (discount.getType() == DiscountType.PERCENTAGE) {
                    discountAmount = subtotal.multiply(discount.getValue())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                } else if (discount.getType() == DiscountType.FLAT) {
                    discountAmount = discount.getValue().setScale(2, RoundingMode.HALF_UP);
                }
                if (discountAmount.compareTo(subtotal) > 0) {
                    discountAmount = subtotal;
                }
            }

            BigDecimal postDiscountSubtotal = subtotal.subtract(discountAmount);
            BigDecimal taxAmount = taxCalculationService.calculateTax(postDiscountSubtotal);
            BigDecimal totalAmount = postDiscountSubtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

            // 5. Create and save Order (status=PLACED)
            User customer = cart.getCustomer();
            Order order = Order.builder()
                    .customer(customer)
                    .status(OrderStatus.PLACED)
                    .taxAmount(taxAmount)
                    .discountAmount(discountAmount)
                    .totalAmount(totalAmount)
                    .discountCode(appliedDiscountCode)
                    .build();
            order = orderRepository.save(order);

            // Create and save Payment
            Payment payment = Payment.builder()
                    .order(order)
                    .status(PaymentStatus.PENDING)
                    .amount(totalAmount)
                    .method("CARD")
                    .build();
            payment = paymentRepository.save(payment);

            // 6. Call PaymentService.charge(order) stub
            try {
                paymentService.charge(order);
            } catch (PaymentFailedException pfe) {
                log.error("Payment failed during checkout. Triggering compensation logic.", pfe);
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                throw pfe;
            }

            // On payment success
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransactionRef(UUID.randomUUID().toString());
            paymentRepository.save(payment);

            // Save order items
            List<OrderItem> orderItems = new ArrayList<>();
            for (ReservedItemTracker tracker : reservedTrackers) {
                OrderItem orderItem = OrderItem.builder()
                        .order(order)
                        .product(tracker.product)
                        .warehouse(warehouseRepository.getReferenceById(tracker.warehouseId))
                        .quantity(tracker.quantity)
                        .unitPrice(tracker.product.getPrice())
                        .build();
                orderItemRepository.save(orderItem);
                orderItems.add(orderItem);
            }

            // Publish OrderPlacedEvent to trigger downstream processing (audit logs, notifications)
            eventPublisher.publishEvent(OrderPlacedEvent.builder()
                    .orderId(order.getId())
                    .customerId(customerId)
                    .totalAmount(order.getTotalAmount())
                    .timestamp(Instant.now())
                    .build());

            log.info("Checkout successful for customer: {}. Created order: {}", customerId, order.getId());

            return mapToResponse(order, orderItems, payment);

        } catch (Exception ex) {
            log.error("Checkout failed. Releasing successfully reserved inventory items.", ex);
            // Compensate for successfully committed reservations
            for (ReservedItemTracker tracker : reservedTrackers) {
                try {
                    inventoryReservationService.releaseReservation(tracker.productId, tracker.warehouseId, tracker.quantity);
                } catch (Exception releaseEx) {
                    log.error("Failed to release reservation of product {} at warehouse {}: {}", 
                            tracker.productId, tracker.warehouseId, releaseEx.getMessage());
                }
            }
            throw ex;
        }
    }

    private OrderResponse mapToResponse(Order order, List<OrderItem> orderItems, Payment payment) {
        List<OrderItemResponse> itemResponses = orderItems.stream()
                .map(item -> OrderItemResponse.builder()
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
                .setScale(2, RoundingMode.HALF_UP);

        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .items(itemResponses)
                .subtotal(subtotal)
                .taxAmount(order.getTaxAmount())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .paymentStatus(payment.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
