package com.ecommerce.oms.repository;

import com.ecommerce.oms.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);
    List<InventoryItem> findByProductId(UUID productId);
    List<InventoryItem> findByWarehouseId(UUID warehouseId);
    boolean existsByWarehouseId(UUID warehouseId);
}
