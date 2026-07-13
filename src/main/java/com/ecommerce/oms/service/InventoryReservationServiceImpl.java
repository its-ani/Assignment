package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.InventoryItem;
import com.ecommerce.oms.dto.ReservationDetail;
import com.ecommerce.oms.exception.InsufficientStockException;
import com.ecommerce.oms.exception.InventoryContentionException;
import com.ecommerce.oms.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationServiceImpl implements InventoryReservationService {

    private final InventoryItemRepository inventoryItemRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(
        retryFor = { OptimisticLockingFailureException.class },
        maxAttempts = 5,
        backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 500)
    )
    public List<ReservationDetail> reserveProductStock(UUID productId, int quantity) {
        log.info("Attempting to reserve quantity {} for product {}", quantity, productId);

        List<InventoryItem> items = inventoryItemRepository.findByProductId(productId);

        // Calculate total available stock across all warehouses
        int totalAvailable = items.stream()
                .mapToInt(item -> item.getQuantityOnHand() - item.getQuantityReserved())
                .filter(avail -> avail > 0)
                .sum();

        if (totalAvailable < quantity) {
            int shortfall = quantity - totalAvailable;
            throw new InsufficientStockException("Insufficient stock for product ID: " + productId + 
                    ". Requested: " + quantity + ", Available: " + totalAvailable + ", Shortfall: " + shortfall);
        }

        // Sort items by availability descending
        List<InventoryItem> sortedItems = items.stream()
                .filter(item -> (item.getQuantityOnHand() - item.getQuantityReserved()) > 0)
                .sorted((item1, item2) -> {
                    int avail1 = item1.getQuantityOnHand() - item1.getQuantityReserved();
                    int avail2 = item2.getQuantityOnHand() - item2.getQuantityReserved();
                    return Integer.compare(avail2, avail1);
                })
                .collect(Collectors.toList());

        List<ReservationDetail> details = new ArrayList<>();
        int remainingToReserve = quantity;

        for (InventoryItem item : sortedItems) {
            if (remainingToReserve <= 0) {
                break;
            }

            int available = item.getQuantityOnHand() - item.getQuantityReserved();
            int toReserve = Math.min(available, remainingToReserve);

            item.setQuantityReserved(item.getQuantityReserved() + toReserve);
            inventoryItemRepository.save(item);

            details.add(ReservationDetail.builder()
                    .warehouseId(item.getWarehouse().getId())
                    .quantity(toReserve)
                    .build());

            remainingToReserve -= toReserve;
        }

        log.info("Successfully reserved quantity {} for product {} across {} warehouses", quantity, productId, details.size());
        return details;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseReservation(UUID productId, UUID warehouseId, int quantity) {
        log.info("Releasing reservation of quantity {} for product {} in warehouse {}", quantity, productId, warehouseId);
        InventoryItem item = inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found for product ID: " + productId + 
                        " and warehouse ID: " + warehouseId));
        
        int newReserved = item.getQuantityReserved() - quantity;
        if (newReserved < 0) {
            newReserved = 0;
        }
        item.setQuantityReserved(newReserved);
        inventoryItemRepository.save(item);
    }

    @Recover
    public List<ReservationDetail> recover(OptimisticLockingFailureException e, UUID productId, int quantity) {
        log.error("Exhausted retries reserving stock for product {}. High contention detected.", productId, e);
        throw new InventoryContentionException("High contention on stock for product " + productId + ", please try again.");
    }

    @Recover
    public List<ReservationDetail> recover(Throwable e, UUID productId, int quantity) throws Throwable {
        throw e;
    }
}
