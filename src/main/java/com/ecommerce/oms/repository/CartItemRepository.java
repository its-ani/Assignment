package com.ecommerce.oms.repository;

import com.ecommerce.oms.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);
    List<CartItem> findByCartId(UUID cartId);
    void deleteByCartIdAndProductId(UUID cartId, UUID productId);
}
