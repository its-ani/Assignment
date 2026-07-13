package com.ecommerce.oms.repository;

import com.ecommerce.oms.domain.Cart;
import com.ecommerce.oms.domain.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByCustomerIdAndStatus(UUID customerId, CartStatus status);
}
