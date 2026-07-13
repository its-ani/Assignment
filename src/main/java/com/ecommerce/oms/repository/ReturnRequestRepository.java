package com.ecommerce.oms.repository;

import com.ecommerce.oms.domain.ReturnRequest;
import com.ecommerce.oms.domain.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {
    
    @Query("SELECT r FROM ReturnRequest r WHERE r.order.customer.id = :customerId")
    Page<ReturnRequest> findByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);

    List<ReturnRequest> findByOrderId(UUID orderId);

    Page<ReturnRequest> findByStatus(ReturnStatus status, Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM ReturnRequest r WHERE r.orderItem.id = :orderItemId AND r.status != com.ecommerce.oms.domain.ReturnStatus.REJECTED")
    int sumReturnedQuantityByOrderItemId(@Param("orderItemId") UUID orderItemId);
}
