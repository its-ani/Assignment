package com.ecommerce.oms.service;

import java.math.BigDecimal;

public interface TaxCalculationService {
    BigDecimal calculateTax(BigDecimal amount);
}
