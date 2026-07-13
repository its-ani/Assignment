package com.ecommerce.oms.exception;

public class WarehouseHasInventoryException extends RuntimeException {
    public WarehouseHasInventoryException(String message) {
        super(message);
    }
}
