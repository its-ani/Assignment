package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Order;
import com.ecommerce.oms.domain.OrderItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RefundCalculationService {

    /**
     * Calculates the proportional refund amount for a returned OrderItem quantity.
     * Formula:
     * Refund Amount = Order Total Amount * ((OrderItem Unit Price * Returned Quantity) / Original Order Subtotal)
     *
     * This ensures the discount and tax are factored proportionally without complex recalculations.
     */
    public BigDecimal calculateRefundAmount(Order order, OrderItem targetItem, List<OrderItem> allItems, int returnedQuantity) {
        if (returnedQuantity <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem item : allItems) {
            BigDecimal itemPrice = item.getUnitPrice();
            subtotal = subtotal.add(itemPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal returnedPreDiscountValue = targetItem.getUnitPrice().multiply(BigDecimal.valueOf(returnedQuantity));
        BigDecimal totalAmount = order.getTotalAmount();

        // Perform multiplication first to maintain precision, then divide
        return totalAmount.multiply(returnedPreDiscountValue)
                .divide(subtotal, 2, RoundingMode.HALF_UP);
    }
}
