package com.ecommerce.oms.repository;

import com.ecommerce.oms.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Query("SELECT c FROM Category c WHERE c.parentCategory.id = :parentId")
    List<Category> findByParentCategoryId(@Param("parentId") UUID parentId);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.parentCategory.id = :categoryId")
    boolean hasSubcategories(@Param("categoryId") UUID categoryId);
}
