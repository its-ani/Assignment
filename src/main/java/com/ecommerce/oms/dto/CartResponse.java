package com.ecommerce.oms.dto;

import com.ecommerce.oms.domain.CartStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private UUID id;
    private CartStatus status;
    private List<CartItemResponse> items;
    private BigDecimal subtotal;
    private int itemCount;
}
