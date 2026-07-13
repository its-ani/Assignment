package com.ecommerce.oms.dto;

import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDetail {
    private UUID warehouseId;
    private int quantity;
}
