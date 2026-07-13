package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.InventoryItem;
import com.ecommerce.oms.domain.OrderStatus;
import com.ecommerce.oms.repository.InventoryItemRepository;
import com.ecommerce.oms.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Background sweeper that detects and releases orphaned inventory reservations.
 *
 * An orphaned reservation occurs when quantityReserved on an InventoryItem
 * exceeds the sum of quantities from all OrderItems in active order states
 * (PLACED, CONFIRMED, PACKED). This can happen due to:
 * - JVM crash after REQUIRES_NEW reservation commits but before Order is saved
 * - Failed compensation during checkout error handling
 * - Any other transactional anomaly that leaves quantityReserved inflated
 *
 * Runs every 15 minutes by default (configurable via reservation.cleanup.interval-ms).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationCleanupScheduler {

    private final InventoryItemRepository inventoryItemRepository;
    private final OrderItemRepository orderItemRepository;

    /** Order statuses where stock should legitimately be reserved */
    private static final List<OrderStatus> ACTIVE_RESERVATION_STATUSES = List.of(
            OrderStatus.PLACED,
            OrderStatus.CONFIRMED,
            OrderStatus.PACKED
    );

    @Scheduled(fixedRateString = "${reservation.cleanup.interval-ms:900000}")
    @Transactional
    public void cleanupOrphanedReservations() {
        log.info("Running orphaned reservation cleanup sweep...");

        List<InventoryItem> itemsWithReservations = inventoryItemRepository.findByQuantityReservedGreaterThan(0);

        int totalCorrected = 0;

        for (InventoryItem item : itemsWithReservations) {
            int expectedReserved = orderItemRepository.sumActiveReservedQuantity(
                    item.getProduct().getId(),
                    item.getWarehouse().getId(),
                    ACTIVE_RESERVATION_STATUSES
            );

            int actualReserved = item.getQuantityReserved();

            if (actualReserved > expectedReserved) {
                int orphanedQuantity = actualReserved - expectedReserved;
                log.warn("Orphaned reservation detected: product={}, warehouse={}, actual={}, expected={}, releasing={}",
                        item.getProduct().getId(), item.getWarehouse().getId(),
                        actualReserved, expectedReserved, orphanedQuantity);

                item.setQuantityReserved(expectedReserved);
                inventoryItemRepository.save(item);
                totalCorrected++;
            }
        }

        if (totalCorrected > 0) {
            log.warn("Orphaned reservation cleanup completed. Corrected {} inventory items.", totalCorrected);
        } else {
            log.info("Orphaned reservation cleanup completed. No orphans found.");
        }
    }
}
