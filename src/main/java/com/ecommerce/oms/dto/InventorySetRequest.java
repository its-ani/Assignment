package com.ecommerce.oms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventorySetRequest {

    @NotNull(message = "Quantity on hand is required")
    @Min(value = 0, message = "Quantity on hand must be greater than or equal to 0")
    private Integer quantityOnHand;
}
