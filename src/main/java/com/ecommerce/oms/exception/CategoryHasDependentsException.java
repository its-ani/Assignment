package com.ecommerce.oms.exception;

public class CategoryHasDependentsException extends RuntimeException {
    public CategoryHasDependentsException(String message) {
        super(message);
    }
}
