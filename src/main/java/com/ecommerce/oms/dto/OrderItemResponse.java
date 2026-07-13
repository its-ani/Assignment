package com.ecommerce.oms.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private UUID productId;
    private String productName;
    private UUID warehouseId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
