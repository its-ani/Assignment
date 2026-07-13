package com.ecommerce.oms.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@ToString
public class OrderPlacedEvent {
    private final UUID orderId;
    private final UUID customerId;
    private final BigDecimal totalAmount;
    private final Instant timestamp;
}
