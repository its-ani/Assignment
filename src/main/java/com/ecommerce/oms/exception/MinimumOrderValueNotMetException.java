package com.ecommerce.oms.exception;

public class MinimumOrderValueNotMetException extends RuntimeException {
    public MinimumOrderValueNotMetException(String message) {
        super(message);
    }
}
