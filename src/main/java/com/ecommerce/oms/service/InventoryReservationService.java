package com.ecommerce.oms.service;

import com.ecommerce.oms.dto.ReservationDetail;
import java.util.List;
import java.util.UUID;

public interface InventoryReservationService {
    List<ReservationDetail> reserveProductStock(UUID productId, int quantity);
    void releaseReservation(UUID productId, UUID warehouseId, int quantity);
}
