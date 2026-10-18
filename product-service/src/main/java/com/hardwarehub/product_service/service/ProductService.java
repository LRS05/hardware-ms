package com.hardwarehub.product_service.service;

import com.hardwarehub.product_service.dto.ProductRequestDTO;
import com.hardwarehub.product_service.dto.ProductResponseDTO;
import com.hardwarehub.product_service.dto.StockRequestDTO;
import com.hardwarehub.product_service.entity.ProductEntity;
import com.hardwarehub.product_service.entity.enums.Category;
import com.hardwarehub.product_service.entity.enums.ProductStatus;
import com.hardwarehub.product_service.exception.ProductNotFoundException;
import com.hardwarehub.product_service.mapper.ProductMapper;
import com.hardwarehub.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductService
{
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductResponseDTO create(ProductRequestDTO requestDTO)
    {
        if (productRepository.existsByName(requestDTO.name()) ||
                productRepository.existsBySku(requestDTO.sku()))
        {
            throw new RuntimeException("The product (sku/name) is already in.");
        }

        ProductEntity product = productRepository.save(
                ProductEntity.builder()
                        .status(requestDTO.status())
                        .category(requestDTO.category())
                        .sku(requestDTO.sku())
                        .name(requestDTO.name())
                        .description(requestDTO.description())
                        .price(requestDTO.price())
                        .stock(requestDTO.stock())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        return productMapper.entityToDTO(product);
    }

    public ProductResponseDTO getById(int id)
    {
        ProductEntity product = findByIdOrThrow(id);

        if (product.getStatus() != ProductStatus.ACTIVE)
        {
            throw new RuntimeException("This product is not ACTIVE.");
        }

        return productMapper.entityToDTO(product);
    }

    public ProductResponseDTO getBySku(String sku)
    {
        return productMapper.entityToDTO(findBySkuOrThrow(sku));
    }

    public Page<ProductResponseDTO> getAllByName(String name, Pageable pageable)
    {
        Page<ProductEntity> products = productRepository.findByNameContainingIgnoreCase(name, pageable);

        return products.map(productMapper::entityToDTO);
    }

    public Page<ProductResponseDTO> getAllByCategory(Category category, Pageable pageable)
    {
        Page<ProductEntity> products = productRepository.findAllByCategory(category, pageable);

        return products.map(productMapper::entityToDTO);
    }

    public Page<ProductResponseDTO> getRecentlyUpdated(Pageable pageable)
    {
        Page<ProductEntity> products = productRepository.findTop30ByOrderByUpdatedAtDesc(pageable);

        return products.map(productMapper::entityToDTO);
    }

    public ProductResponseDTO updateById(int id, ProductRequestDTO requestDTO)
    {
        ProductEntity product = findByIdOrThrow(id);

        updateProduct(product, requestDTO);

        return productMapper.entityToDTO(productRepository.save(product));
    }

    public ProductResponseDTO increaseStockById(int id, StockRequestDTO requestDTO)
    {
        ProductEntity product = findByIdOrThrow(id);

        product.setStock(product.getStock() + requestDTO.stock());

        if (product.getStatus() != ProductStatus.ACTIVE)
        {
            product.setStatus(ProductStatus.ACTIVE);
        }

        return productMapper.entityToDTO(productRepository.save(product));
    }

    public ProductResponseDTO decreaseStockById(int id, StockRequestDTO requestDTO)
    {
        ProductEntity product = findByIdOrThrow(id);

        if (product.getStatus() == ProductStatus.INACTIVE)
        {
            throw new RuntimeException("This product is already out of stock.");
        }

        product.setStock(Math.max(product.getStock() - requestDTO.stock(), 0));

        if (product.getStock() == 0)
        {
            product.setStatus(ProductStatus.INACTIVE);
        }

        return productMapper.entityToDTO(productRepository.save(product));
    }

    public void deactivateById(int id)
    {
        ProductEntity product = findByIdOrThrow(id);

        product.setStatus(ProductStatus.INACTIVE);

        // TODO: Add log with product information.

        productRepository.save(product);
    }

    private ProductEntity findByIdOrThrow(int id)
    {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found."));
    }

    private ProductEntity findBySkuOrThrow(String sku)
    {
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException("Product not found."));
    }

    private void updateProduct(ProductEntity product, ProductRequestDTO requestDTO)
    {
        if (requestDTO.status() != null)
        {
            product.setStatus(requestDTO.status());
        }

        if (requestDTO.category() != null)
        {
            product.setCategory(requestDTO.category());
        }

        if (requestDTO.sku() != null)
        {
            product.setSku(requestDTO.sku());
        }

        if (requestDTO.name() != null)
        {
            product.setName(requestDTO.name());
        }

        if (requestDTO.description() != null)
        {
            product.setDescription(requestDTO.description());
        }

        if (requestDTO.price() != null)
        {
            product.setPrice(requestDTO.price());
        }

        if (requestDTO.stock() != null)
        {
            product.setStock(requestDTO.stock());
        }
    }
}