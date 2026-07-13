package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Order;
import com.ecommerce.oms.exception.PaymentFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    @Value("${app.payment.force-failure:false}")
    private boolean forceFailure;

    @Value("${app.payment.failure-rate:0.0}")
    private double failureRate;

    private final Random random = new Random();

    public void setForceFailure(boolean forceFailure) {
        this.forceFailure = forceFailure;
    }

    @Override
    public void charge(Order order) {
        log.info("Processing payment charge simulation for order ID: {}, amount: {}", order.getId(), order.getTotalAmount());

        if (forceFailure) {
            log.warn("Payment failed for order ID: {} due to app.payment.force-failure configuration", order.getId());
            throw new PaymentFailedException("Payment failed (forced failure)");
        }

        if (failureRate > 0.0) {
            double roll = random.nextDouble();
            if (roll < failureRate) {
                log.warn("Payment failed for order ID: {} due to simulated random failure rate", order.getId());
                throw new PaymentFailedException("Payment failed (simulated failure rate)");
            }
        }

        log.info("Payment successful for order ID: {}", order.getId());
    }
}
