package com.ecommerce.oms.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAvailabilitySummary {

    private UUID productId;
    private boolean available;
    private int totalAvailableQuantity;
}
