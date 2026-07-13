package com.ecommerce.oms.repository;

import com.ecommerce.oms.domain.Order;
import com.ecommerce.oms.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
    Page<Order> findByCustomerIdAndStatus(UUID customerId, OrderStatus status, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);
}

