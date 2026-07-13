package com.ecommerce.oms.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TaxCalculationServiceTest {

    @Test
    void testCalculateTaxWithDefaultRate() {
        TaxCalculationService service = new TaxCalculationServiceImpl(0.08);

        BigDecimal tax = service.calculateTax(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("8.00"), tax);

        tax = service.calculateTax(new BigDecimal("50.00"));
        assertEquals(new BigDecimal("4.00"), tax);

        tax = service.calculateTax(new BigDecimal("9.99")); // 9.99 * 0.08 = 0.7992 -> 0.80
        assertEquals(new BigDecimal("0.80"), tax);

        tax = service.calculateTax(null);
        assertEquals(new BigDecimal("0.00"), tax);
    }

    @Test
    void testCalculateTaxWithCustomRate() {
        TaxCalculationService service = new TaxCalculationServiceImpl(0.15);

        BigDecimal tax = service.calculateTax(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("15.00"), tax);
    }
}
