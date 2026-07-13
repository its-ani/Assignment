package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Order;

public interface PaymentService {
    void charge(Order order);
}
