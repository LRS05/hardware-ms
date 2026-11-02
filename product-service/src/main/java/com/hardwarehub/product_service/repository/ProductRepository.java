package com.hardwarehub.product_service.repository;

import com.hardwarehub.product_service.entity.ProductEntity;
import com.hardwarehub.product_service.enums.Category;
import com.hardwarehub.product_service.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Integer>
{
    Page<ProductEntity> findAllByCategoryAndStatus(Category category, ProductStatus status, Pageable pageable);

    Optional<ProductEntity> findBySku(String sku);

    Page<ProductEntity> findByNameContainingIgnoreCaseAndStatus(String name, ProductStatus status, Pageable pageable);

    boolean existsByName(String name);

    boolean existsBySku(String sku);

    Page<ProductEntity> findTop30ByStatusOrderByUpdatedAtDesc(Pageable pageable, ProductStatus status);

    @Query(
            value = "SELECT * FROM products WHERE id = :id FOR UPDATE",
            nativeQuery = true
    )
    Optional<ProductEntity> findByIdForUpdate(@Param("id") int id);

    boolean existsByNameAndIdNot(String name, int id);

    boolean existsBySkuAndIdNot(String sku, int id);
}
