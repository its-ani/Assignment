package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Order;
import java.math.BigDecimal;

public interface PaymentService {
    void charge(Order order);
    void refund(Order order, BigDecimal amount);
}
