package com.ecommerce.oms.exception;

public class DiscountNotFoundException extends ResourceNotFoundException {
    public DiscountNotFoundException(String message) {
        super(message);
    }
}
