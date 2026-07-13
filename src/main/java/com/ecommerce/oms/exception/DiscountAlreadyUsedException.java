package com.ecommerce.oms.exception;

public class DiscountAlreadyUsedException extends RuntimeException {
    public DiscountAlreadyUsedException(String message) {
        super(message);
    }
}
