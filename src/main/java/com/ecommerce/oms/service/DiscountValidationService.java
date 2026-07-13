package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Discount;
import java.math.BigDecimal;
import java.util.UUID;

public interface DiscountValidationService {
    Discount validate(String code, BigDecimal cartSubtotal, UUID customerId);
}
