package com.ecommerce.oms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class TaxCalculationServiceImpl implements TaxCalculationService {

    private final BigDecimal taxRate;

    public TaxCalculationServiceImpl(@Value("${app.tax-rate:0.08}") double taxRate) {
        this.taxRate = BigDecimal.valueOf(taxRate);
    }

    @Override
    public BigDecimal calculateTax(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    }
}
