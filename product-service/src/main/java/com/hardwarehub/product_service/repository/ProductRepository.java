package com.hardwarehub.product_service.repository;

import com.hardwarehub.product_service.entity.ProductEntity;
import com.hardwarehub.product_service.entity.enums.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Integer>
{
    Page<ProductEntity> findAllByCategory(Category category, Pageable pageable);

    Optional<ProductEntity> findBySku(String sku);

    Page<ProductEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    boolean existsByName(String name);

    boolean existsBySku(String sku);

    Page<ProductEntity> findTop30ByOrderByUpdatedAtDesc(Pageable pageable);
}
