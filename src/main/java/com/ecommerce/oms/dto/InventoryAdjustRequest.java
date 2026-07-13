package com.ecommerce.oms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustRequest {

    @NotNull(message = "Adjustment delta is required")
    private Integer delta;

    private String reason;
}
