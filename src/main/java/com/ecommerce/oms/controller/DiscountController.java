package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.Discount;
import com.ecommerce.oms.domain.DiscountType;
import com.ecommerce.oms.dto.DiscountPreviewResponse;
import com.ecommerce.oms.dto.DiscountRequest;
import com.ecommerce.oms.dto.DiscountResponse;
import com.ecommerce.oms.security.SecurityUtils;
import com.ecommerce.oms.service.DiscountService;
import com.ecommerce.oms.service.DiscountValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/discounts")
@RequiredArgsConstructor
public class DiscountController {

    private final DiscountService discountService;
    private final DiscountValidationService discountValidationService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DiscountResponse> createDiscount(@Valid @RequestBody DiscountRequest request) {
        DiscountResponse response = discountService.createDiscount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DiscountResponse> updateDiscount(@PathVariable UUID id, @Valid @RequestBody DiscountRequest request) {
        DiscountResponse response = discountService.updateDiscount(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDiscount(@PathVariable UUID id) {
        discountService.softDeleteDiscount(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DiscountResponse> getDiscountById(@PathVariable UUID id) {
        DiscountResponse response = discountService.getDiscountById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<DiscountResponse>> listDiscounts(Pageable pageable) {
        Page<DiscountResponse> response = discountService.listDiscounts(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<DiscountPreviewResponse> validateDiscount(
            @RequestParam String code,
            @RequestParam BigDecimal cartTotal
    ) {
        UUID customerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Access denied: User is not authenticated"));

        Discount discount = discountValidationService.validate(code, cartTotal, customerId);

        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (discount.getType() == DiscountType.PERCENTAGE) {
            discountAmount = cartTotal.multiply(discount.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (discount.getType() == DiscountType.FLAT) {
            discountAmount = discount.getValue().setScale(2, RoundingMode.HALF_UP);
        }

        if (discountAmount.compareTo(cartTotal) > 0) {
            discountAmount = cartTotal;
        }

        DiscountPreviewResponse response = DiscountPreviewResponse.builder()
                .code(discount.getCode())
                .type(discount.getType())
                .value(discount.getValue())
                .discountAmount(discountAmount)
                .eligible(true)
                .build();

        return ResponseEntity.ok(response);
    }
}
