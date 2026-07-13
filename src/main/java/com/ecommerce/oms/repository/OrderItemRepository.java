package com.ecommerce.oms.repository;

import com.ecommerce.oms.domain.OrderItem;
import com.ecommerce.oms.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderId(UUID orderId);

    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
           "WHERE oi.product.id = :productId AND oi.warehouse.id = :warehouseId " +
           "AND oi.order.status IN :activeStatuses")
    int sumActiveReservedQuantity(@Param("productId") UUID productId,
                                 @Param("warehouseId") UUID warehouseId,
                                 @Param("activeStatuses") List<OrderStatus> activeStatuses);
}
