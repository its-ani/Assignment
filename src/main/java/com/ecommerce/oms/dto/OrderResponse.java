package com.ecommerce.oms.dto;

import com.ecommerce.oms.domain.OrderStatus;
import com.ecommerce.oms.domain.PaymentStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private UUID id;
    private OrderStatus status;
    private List<OrderItemResponse> items;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private PaymentStatus paymentStatus;
    private Instant deliveredAt;
    private Instant createdAt;
}
