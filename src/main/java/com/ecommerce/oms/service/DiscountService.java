package com.ecommerce.oms.service;

import com.ecommerce.oms.dto.DiscountRequest;
import com.ecommerce.oms.dto.DiscountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface DiscountService {
    DiscountResponse createDiscount(DiscountRequest request);
    DiscountResponse updateDiscount(UUID id, DiscountRequest request);
    void softDeleteDiscount(UUID id);
    DiscountResponse getDiscountById(UUID id);
    Page<DiscountResponse> listDiscounts(Pageable pageable);
}
