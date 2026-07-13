package com.ecommerce.oms.service;

import com.ecommerce.oms.dto.CheckoutRequest;
import com.ecommerce.oms.dto.OrderResponse;
import java.util.UUID;

public interface CheckoutService {
    OrderResponse checkout(UUID customerId, CheckoutRequest request);
}
