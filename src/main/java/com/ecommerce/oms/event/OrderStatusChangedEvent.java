package com.ecommerce.oms.event;

import com.ecommerce.oms.domain.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@ToString
public class OrderStatusChangedEvent {
    private final UUID orderId;
    private final OrderStatus oldStatus;
    private final OrderStatus newStatus;
    private final String actor;
    private final Instant timestamp;
}
