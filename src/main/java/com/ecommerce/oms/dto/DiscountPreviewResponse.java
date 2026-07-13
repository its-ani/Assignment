package com.ecommerce.oms.dto;

import com.ecommerce.oms.domain.DiscountType;
import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountPreviewResponse {
    private String code;
    private DiscountType type;
    private BigDecimal value;
    private BigDecimal discountAmount;
    private boolean eligible;
}
