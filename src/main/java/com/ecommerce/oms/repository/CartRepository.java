package com.ecommerce.oms.repository;

import com.ecommerce.oms.domain.Cart;
import com.ecommerce.oms.domain.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByCustomerIdAndStatus(UUID customerId, CartStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.customer.id = :customerId AND c.status = com.ecommerce.oms.domain.CartStatus.ACTIVE")
    Optional<Cart> findActiveCartWithWriteLock(@Param("customerId") UUID customerId);
}
