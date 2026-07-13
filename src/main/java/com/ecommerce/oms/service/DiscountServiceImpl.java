package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Discount;
import com.ecommerce.oms.domain.DiscountType;
import com.ecommerce.oms.dto.DiscountRequest;
import com.ecommerce.oms.dto.DiscountResponse;
import com.ecommerce.oms.exception.DuplicateResourceException;
import com.ecommerce.oms.exception.ResourceNotFoundException;
import com.ecommerce.oms.repository.DiscountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements DiscountService {

    private final DiscountRepository discountRepository;

    @Override
    @Transactional
    public DiscountResponse createDiscount(DiscountRequest request) {
        validateRequest(request);

        if (discountRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Discount code already exists: " + request.getCode());
        }

        Discount discount = Discount.builder()
                .code(request.getCode())
                .type(request.getType())
                .value(request.getValue())
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .minOrderValue(request.getMinOrderValue())
                .active(request.isActive())
                .build();

        Discount saved = discountRepository.save(discount);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public DiscountResponse updateDiscount(UUID id, DiscountRequest request) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with ID: " + id));

        validateRequest(request);

        if (!discount.getCode().equalsIgnoreCase(request.getCode()) && discountRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Discount code already exists: " + request.getCode());
        }

        discount.setCode(request.getCode());
        discount.setType(request.getType());
        discount.setValue(request.getValue());
        discount.setValidFrom(request.getValidFrom());
        discount.setValidTo(request.getValidTo());
        discount.setMinOrderValue(request.getMinOrderValue());
        discount.setActive(request.isActive());

        Discount updated = discountRepository.save(discount);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void softDeleteDiscount(UUID id) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with ID: " + id));
        discount.setActive(false);
        discountRepository.save(discount);
    }

    @Override
    @Transactional(readOnly = true)
    public DiscountResponse getDiscountById(UUID id) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with ID: " + id));
        return mapToResponse(discount);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DiscountResponse> listDiscounts(Pageable pageable) {
        return discountRepository.findAll(pageable).map(this::mapToResponse);
    }

    private void validateRequest(DiscountRequest request) {
        if (request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than zero");
        }
        if (request.getType() == DiscountType.PERCENTAGE && request.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discount value cannot exceed 100");
        }
        if (request.getValidTo().isBefore(request.getValidFrom()) || request.getValidTo().equals(request.getValidFrom())) {
            throw new IllegalArgumentException("Valid to date must be after valid from date");
        }
    }

    private DiscountResponse mapToResponse(Discount discount) {
        return DiscountResponse.builder()
                .id(discount.getId())
                .code(discount.getCode())
                .type(discount.getType())
                .value(discount.getValue())
                .validFrom(discount.getValidFrom())
                .validTo(discount.getValidTo())
                .minOrderValue(discount.getMinOrderValue())
                .active(discount.isActive())
                .build();
    }
}
