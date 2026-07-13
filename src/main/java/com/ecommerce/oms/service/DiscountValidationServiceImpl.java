package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Discount;
import com.ecommerce.oms.domain.OrderStatus;
import com.ecommerce.oms.exception.*;
import com.ecommerce.oms.repository.DiscountRepository;
import com.ecommerce.oms.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscountValidationServiceImpl implements DiscountValidationService {

    private final DiscountRepository discountRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public Discount validate(String code, BigDecimal cartSubtotal, UUID customerId) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Discount code cannot be empty");
        }

        Discount discount = discountRepository.findByCode(code)
                .orElseThrow(() -> new DiscountNotFoundException("Discount code not found: " + code));

        if (!discount.isActive()) {
            throw new DiscountInactiveException("Discount code is inactive: " + code);
        }

        Instant now = Instant.now();
        if (now.isBefore(discount.getValidFrom()) || now.isAfter(discount.getValidTo())) {
            throw new DiscountExpiredException("Discount code is not valid at this time: " + code);
        }

        if (cartSubtotal == null || cartSubtotal.compareTo(discount.getMinOrderValue()) < 0) {
            throw new MinimumOrderValueNotMetException(
                    "Minimum order value of " + discount.getMinOrderValue() + " not met for discount: " + code
            );
        }

        boolean alreadyUsed = orderRepository.existsByCustomerIdAndDiscountCodeAndStatusNot(customerId, code, OrderStatus.CANCELLED);
        if (alreadyUsed) {
            throw new DiscountAlreadyUsedException("Discount code has already been used by this customer: " + code);
        }

        return discount;
    }
}
