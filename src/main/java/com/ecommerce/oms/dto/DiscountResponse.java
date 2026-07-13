package com.ecommerce.oms.dto;

import com.ecommerce.oms.domain.DiscountType;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountResponse {
    private UUID id;
    private String code;
    private DiscountType type;
    private BigDecimal value;
    private Instant validFrom;
    private Instant validTo;
    private BigDecimal minOrderValue;
    private boolean active;
}
