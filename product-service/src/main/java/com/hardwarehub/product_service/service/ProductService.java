package com.hardwarehub.product_service.service;

import com.hardwarehub.product_service.dto.ProductRequestDTO;
import com.hardwarehub.product_service.dto.ProductResponseDTO;
import com.hardwarehub.product_service.dto.ProductUpdateRequestDTO;
import com.hardwarehub.product_service.dto.StockRequestDTO;
import com.hardwarehub.product_service.entity.ProductEntity;
import com.hardwarehub.product_service.enums.Category;
import com.hardwarehub.product_service.enums.ProductStatus;
import com.hardwarehub.product_service.exception.InsufficientStockException;
import com.hardwarehub.product_service.exception.ProductNameAlreadyExistsException;
import com.hardwarehub.product_service.exception.ProductNotFoundException;
import com.hardwarehub.product_service.exception.ProductOutOfStockException;
import com.hardwarehub.product_service.exception.ProductSkuAlreadyExistsException;
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
        validateNameOrSkuAreAvailable(requestDTO.name(), requestDTO.sku());

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

        if (product.getStatus() != ProductStatus.ACTIVE || product.getStock() == 0)
        {
            throw new ProductOutOfStockException("The product is out of stock");
        }

        return productMapper.entityToDTO(product);
    }

    public ProductResponseDTO getBySku(String sku)
    {
        return productMapper.entityToDTO(findBySkuOrThrow(sku));
    }

    public Page<ProductResponseDTO> getAllByName(String name, Pageable pageable)
    {
        Page<ProductEntity> products = productRepository.findByNameContainingIgnoreCaseAndStatus(name, ProductStatus.ACTIVE, pageable);

        return products.map(productMapper::entityToDTO);
    }

    public Page<ProductResponseDTO> getAllByCategory(Category category, Pageable pageable)
    {
        Page<ProductEntity> products = productRepository.findAllByCategoryAndStatus(category, ProductStatus.ACTIVE, pageable);

        return products.map(productMapper::entityToDTO);
    }

    public Page<ProductResponseDTO> getRecentlyUpdated(Pageable pageable)
    {
        Page<ProductEntity> products = productRepository.findTop30ByStatusOrderByUpdatedAtDesc(pageable, ProductStatus.ACTIVE);

        return products.map(productMapper::entityToDTO);
    }

    public ProductResponseDTO updateById(int id, ProductUpdateRequestDTO requestDTO)
    {
        ProductEntity product = findByIdOrThrow(id);

        updateProduct(product, requestDTO);

        return productMapper.entityToDTO(productRepository.save(product));
    }

    public void increaseStockById(int id, StockRequestDTO requestDTO)
    {
        ProductEntity product = findByIdOrThrow(id);

        product.setStock(product.getStock() + requestDTO.stock());

        if (product.getStatus() != ProductStatus.ACTIVE) product.setStatus(ProductStatus.ACTIVE);

        productRepository.save(product);
    }

    public void decreaseStockById(int id, StockRequestDTO requestDTO)
    {
        ProductEntity product = findByIdOrThrow(id);

        validateStock(product, requestDTO.stock());

        product.setStock(product.getStock() - requestDTO.stock());

        if (product.getStock() == 0) product.setStatus(ProductStatus.INACTIVE);

        productRepository.save(product);
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

    private void validateStock(ProductEntity product, int requiredStock)
    {
        if (product.getStatus() == ProductStatus.INACTIVE)
        {
            throw new ProductOutOfStockException("This product is out of stock.");
        }

        if (product.getStock() < requiredStock)
        {
            throw new InsufficientStockException("There's not enough stock of this product.");
        }
    }

    private void updateProduct(ProductEntity product, ProductUpdateRequestDTO requestDTO)
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

    private void validateNameOrSkuAreAvailable(String name, String sku)
    {
        if (productRepository.existsByName(name))
        {
            throw new ProductNameAlreadyExistsException("Name already exists.");
        }

        if (productRepository.existsBySku(sku))
        {
            throw new ProductSkuAlreadyExistsException("SKU already exists.");
        }
    }
}
