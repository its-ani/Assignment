package com.ecommerce.oms.repository;

import com.ecommerce.oms.domain.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, UUID> {
    Optional<Discount> findByCodeAndActiveTrue(String code);
    Optional<Discount> findByCode(String code);
    boolean existsByCode(String code);
}
