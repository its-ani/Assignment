package com.ecommerce.oms.dto;

import com.ecommerce.oms.domain.ReturnStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequestResponse {
    private UUID id;
    private UUID orderId;
    private UUID orderItemId;
    private String productName;
    private Integer quantity;
    private String reason;
    private ReturnStatus status;
    private BigDecimal refundAmount;
    private String rejectionReason;
    private Instant createdAt;
}
