package com.ecommerce.oms.dto;

import com.ecommerce.oms.domain.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountRequest {

    @NotBlank(message = "Discount code is required")
    private String code;

    @NotNull(message = "Discount type is required")
    private DiscountType type;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "Discount value must be greater than zero")
    private BigDecimal value;

    @NotNull(message = "Valid from date is required")
    private Instant validFrom;

    @NotNull(message = "Valid to date is required")
    private Instant validTo;

    @NotNull(message = "Minimum order value is required")
    @DecimalMin(value = "0.00", message = "Minimum order value cannot be negative")
    private BigDecimal minOrderValue;

    @Builder.Default
    private boolean active = true;
}
