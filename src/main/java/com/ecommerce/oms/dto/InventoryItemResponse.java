package com.ecommerce.oms.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private UUID warehouseId;
    private String warehouseName;
    private int quantityOnHand;
    private int quantityReserved;
    private int quantityAvailable;
}
