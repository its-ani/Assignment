package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Discount;
import com.ecommerce.oms.domain.DiscountType;
import com.ecommerce.oms.domain.OrderStatus;
import com.ecommerce.oms.exception.*;
import com.ecommerce.oms.repository.DiscountRepository;
import com.ecommerce.oms.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class DiscountValidationServiceTest {

    private DiscountValidationService validationService;

    @Mock
    private DiscountRepository discountRepository;

    @Mock
    private OrderRepository orderRepository;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validationService = new DiscountValidationServiceImpl(discountRepository, orderRepository);
        customerId = UUID.randomUUID();
    }

    @Test
    void testDiscountNotFound() {
        String code = "NOTFOUND";
        when(discountRepository.findByCode(code)).thenReturn(Optional.empty());

        assertThrows(DiscountNotFoundException.class, () -> 
            validationService.validate(code, new BigDecimal("100.00"), customerId)
        );
    }

    @Test
    void testDiscountInactive() {
        String code = "INACTIVE";
        Discount discount = Discount.builder()
                .code(code)
                .active(false)
                .build();
        when(discountRepository.findByCode(code)).thenReturn(Optional.of(discount));

        assertThrows(DiscountInactiveException.class, () -> 
            validationService.validate(code, new BigDecimal("100.00"), customerId)
        );
    }

    @Test
    void testDiscountNotYetValid() {
        String code = "FUTURE";
        Discount discount = Discount.builder()
                .code(code)
                .active(true)
                .validFrom(Instant.now().plus(1, ChronoUnit.DAYS))
                .validTo(Instant.now().plus(2, ChronoUnit.DAYS))
                .build();
        when(discountRepository.findByCode(code)).thenReturn(Optional.of(discount));

        assertThrows(DiscountExpiredException.class, () -> 
            validationService.validate(code, new BigDecimal("100.00"), customerId)
        );
    }

    @Test
    void testDiscountExpired() {
        String code = "EXPIRED";
        Discount discount = Discount.builder()
                .code(code)
                .active(true)
                .validFrom(Instant.now().minus(2, ChronoUnit.DAYS))
                .validTo(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        when(discountRepository.findByCode(code)).thenReturn(Optional.of(discount));

        assertThrows(DiscountExpiredException.class, () -> 
            validationService.validate(code, new BigDecimal("100.00"), customerId)
        );
    }

    @Test
    void testMinOrderValueNotMet() {
        String code = "MINVAL";
        Discount discount = Discount.builder()
                .code(code)
                .active(true)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validTo(Instant.now().plus(1, ChronoUnit.DAYS))
                .minOrderValue(new BigDecimal("150.00"))
                .build();
        when(discountRepository.findByCode(code)).thenReturn(Optional.of(discount));

        assertThrows(MinimumOrderValueNotMetException.class, () -> 
            validationService.validate(code, new BigDecimal("100.00"), customerId)
        );
    }

    @Test
    void testDiscountAlreadyUsed() {
        String code = "ONCEONLY";
        Discount discount = Discount.builder()
                .code(code)
                .active(true)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validTo(Instant.now().plus(1, ChronoUnit.DAYS))
                .minOrderValue(new BigDecimal("50.00"))
                .build();
        when(discountRepository.findByCode(code)).thenReturn(Optional.of(discount));
        when(orderRepository.existsByCustomerIdAndDiscountCodeAndStatusNot(customerId, code, OrderStatus.CANCELLED))
                .thenReturn(true);

        assertThrows(DiscountAlreadyUsedException.class, () -> 
            validationService.validate(code, new BigDecimal("100.00"), customerId)
        );
    }

    @Test
    void testDiscountValid() {
        String code = "VALID10";
        Discount discount = Discount.builder()
                .code(code)
                .active(true)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validTo(Instant.now().plus(1, ChronoUnit.DAYS))
                .minOrderValue(new BigDecimal("50.00"))
                .type(DiscountType.PERCENTAGE)
                .value(new BigDecimal("10.00"))
                .build();
        when(discountRepository.findByCode(code)).thenReturn(Optional.of(discount));
        when(orderRepository.existsByCustomerIdAndDiscountCodeAndStatusNot(customerId, code, OrderStatus.CANCELLED))
                .thenReturn(false);

        Discount validated = validationService.validate(code, new BigDecimal("100.00"), customerId);
        assertNotNull(validated);
        assertEquals(code, validated.getCode());
        assertEquals(DiscountType.PERCENTAGE, validated.getType());
    }
}
