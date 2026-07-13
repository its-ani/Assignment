package com.ecommerce.oms.event;

import com.ecommerce.oms.domain.ReturnStatus;
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
public class ReturnStatusChangedEvent {
    private final UUID returnRequestId;
    private final UUID orderId;
    private final ReturnStatus oldStatus;
    private final ReturnStatus newStatus;
    private final String actor;
    private final Instant timestamp;
    private final String metadata;
}
