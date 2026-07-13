package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.*;
import com.ecommerce.oms.dto.ReturnDecisionRequest;
import com.ecommerce.oms.dto.ReturnRequestCreate;
import com.ecommerce.oms.dto.ReturnRequestResponse;
import com.ecommerce.oms.event.ReturnStatusChangedEvent;
import com.ecommerce.oms.exception.ResourceNotFoundException;
import com.ecommerce.oms.repository.OrderItemRepository;
import com.ecommerce.oms.repository.OrderRepository;
import com.ecommerce.oms.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private final ReturnRequestRepository returnRequestRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final RefundCalculationService refundCalculationService;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.return.window-days:30}")
    private int returnWindowDays;

    @Override
    @Transactional
    public ReturnRequestResponse requestReturn(UUID customerId, ReturnRequestCreate request) {
        log.info("Customer {} requesting return for OrderItem: {}, quantity: {}", customerId, request.getOrderItemId(), request.getQuantity());

        OrderItem orderItem = orderItemRepository.findById(request.getOrderItemId())
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem not found with ID: " + request.getOrderItemId()));

        Order order = orderItem.getOrder();
        if (order == null || !order.getCustomer().getId().equals(customerId)) {
            throw new ResourceNotFoundException("OrderItem not found or not owned by customer.");
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Returns can only be requested for DELIVERED orders. Current status: " + order.getStatus());
        }

        if (order.getDeliveredAt() == null) {
            throw new IllegalStateException("Order delivered timestamp is missing. Cannot validate return window.");
        }

        Instant limit = order.getDeliveredAt().plus(Duration.ofDays(returnWindowDays));
        if (Instant.now().isAfter(limit)) {
            throw new IllegalArgumentException("Return window of " + returnWindowDays + " days has expired for this order.");
        }

        int alreadyReturned = returnRequestRepository.sumReturnedQuantityByOrderItemId(request.getOrderItemId());
        int remainingReturnable = orderItem.getQuantity() - alreadyReturned;

        if (request.getQuantity() > remainingReturnable) {
            throw new IllegalArgumentException(String.format(
                    "Requested quantity %d exceeds remaining returnable quantity %d (ordered: %d, already returned: %d).",
                    request.getQuantity(), remainingReturnable, orderItem.getQuantity(), alreadyReturned
            ));
        }

        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .orderItem(orderItem)
                .reason(request.getReason())
                .quantity(request.getQuantity())
                .status(ReturnStatus.REQUESTED)
                .createdAt(Instant.now())
                .build();

        ReturnRequest saved = returnRequestRepository.save(returnRequest);

        eventPublisher.publishEvent(ReturnStatusChangedEvent.builder()
                .returnRequestId(saved.getId())
                .orderId(order.getId())
                .oldStatus(null)
                .newStatus(ReturnStatus.REQUESTED)
                .actor("CUSTOMER_" + customerId)
                .timestamp(Instant.now())
                .metadata(String.format("{\"reason\":\"%s\",\"quantity\":%d}", saved.getReason(), saved.getQuantity()))
                .build());

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequestResponse> getCustomerReturns(UUID customerId, Pageable pageable) {
        return returnRequestRepository.findByCustomerId(customerId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnRequestResponse getReturnDetail(UUID customerId, UUID returnId) {
        ReturnRequest request = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest not found with ID: " + returnId));

        if (!request.getOrder().getCustomer().getId().equals(customerId)) {
            throw new ResourceNotFoundException("ReturnRequest not found with ID: " + returnId);
        }

        return mapToResponse(request);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnRequestResponse> getReturnsByStatus(ReturnStatus status, Pageable pageable) {
        if (status == null) {
            return returnRequestRepository.findAll(pageable).map(this::mapToResponse);
        }
        return returnRequestRepository.findByStatus(status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public ReturnRequestResponse decideReturn(UUID returnId, ReturnDecisionRequest request, String actor) {
        log.info("Staff/Admin decision on return: {}, approved: {}, actor: {}", returnId, request.getApproved(), actor);

        ReturnRequest returnRequest = returnRequestRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest not found with ID: " + returnId));

        ReturnStatus oldStatus = returnRequest.getStatus();
        if (oldStatus != ReturnStatus.REQUESTED) {
            throw new IllegalStateException("Can only decide on return requests in REQUESTED status. Current: " + oldStatus);
        }

        if (Boolean.FALSE.equals(request.getApproved())) {
            returnRequest.setStatus(ReturnStatus.REJECTED);
            returnRequest.setRejectionReason(request.getRejectionReason());
            ReturnRequest saved = returnRequestRepository.save(returnRequest);

            eventPublisher.publishEvent(ReturnStatusChangedEvent.builder()
                    .returnRequestId(saved.getId())
                    .orderId(saved.getOrder().getId())
                    .oldStatus(oldStatus)
                    .newStatus(ReturnStatus.REJECTED)
                    .actor(actor)
                    .timestamp(Instant.now())
                    .metadata(String.format("{\"rejectionReason\":\"%s\"}", saved.getRejectionReason() != null ? saved.getRejectionReason() : ""))
                    .build());

            return mapToResponse(saved);
        }

        // Approval flow:
        // 1. Calculate proportional refund
        Order order = returnRequest.getOrder();
        OrderItem orderItem = returnRequest.getOrderItem();
        List<OrderItem> allItems = orderItemRepository.findByOrderId(order.getId());
        BigDecimal refundAmount = refundCalculationService.calculateRefundAmount(
                order, orderItem, allItems, returnRequest.getQuantity()
        );

        returnRequest.setStatus(ReturnStatus.APPROVED);
        returnRequest.setRefundAmount(refundAmount);
        // Save APPROVED temporarily so we are in APPROVED state during refund/restock
        returnRequestRepository.saveAndFlush(returnRequest);

        // 2. Simulating payment refund gateway call
        paymentService.refund(order, refundAmount);

        // 3. Restocking inventory at the original warehouse
        if (orderItem.getWarehouse() == null) {
            throw new IllegalStateException("Original warehouse not defined on the OrderItem. Cannot restock.");
        }
        inventoryService.adjustStock(
                orderItem.getProduct().getId(),
                orderItem.getWarehouse().getId(),
                returnRequest.getQuantity(),
                "Restock return-driven for request: " + returnRequest.getId(),
                actor
        );

        // 4. Final state transition
        returnRequest.setStatus(ReturnStatus.REFUNDED);
        ReturnRequest saved = returnRequestRepository.save(returnRequest);

        eventPublisher.publishEvent(ReturnStatusChangedEvent.builder()
                .returnRequestId(saved.getId())
                .orderId(order.getId())
                .oldStatus(oldStatus)
                .newStatus(ReturnStatus.REFUNDED)
                .actor(actor)
                .timestamp(Instant.now())
                .metadata(String.format("{\"refundAmount\":%s}", refundAmount.toString()))
                .build());

        return mapToResponse(saved);
    }

    private ReturnRequestResponse mapToResponse(ReturnRequest request) {
        return ReturnRequestResponse.builder()
                .id(request.getId())
                .orderId(request.getOrder().getId())
                .orderItemId(request.getOrderItem().getId())
                .productName(request.getOrderItem().getProduct().getName())
                .quantity(request.getQuantity())
                .reason(request.getReason())
                .status(request.getStatus())
                .refundAmount(request.getRefundAmount())
                .rejectionReason(request.getRejectionReason())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
