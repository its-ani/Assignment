package com.ecommerce.oms.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {
    private String idempotencyKey;
}
