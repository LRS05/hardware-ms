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
import com.hardwarehub.product_service.factory.ProductFactory;
import com.hardwarehub.product_service.mapper.ProductMapper;
import com.hardwarehub.product_service.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest
{
    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    @Test
    void create_whenNameAndSkuAreAvailable_thenSaveAndReturnProduct()
    {
        // Given
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                ProductStatus.ACTIVE,
                Category.GRAPHIC_CARD,
                "NEW-GPU-001",
                "New GPU",
                "A new graphics card",
                BigDecimal.valueOf(1200),
                30
        );

        ProductEntity product = ProductFactory.graphicCard();
        ProductResponseDTO productDTO = ProductFactory.graphicCardDTO();

        when(productRepository.existsByName(requestDTO.name())).thenReturn(false);
        when(productRepository.existsBySku(requestDTO.sku())).thenReturn(false);
        when(productRepository.save(any(ProductEntity.class))).thenReturn(product);
        when(productMapper.entityToDTO(product)).thenReturn(productDTO);

        // When
        ProductResponseDTO result = productService.create(requestDTO);

        // Then
        assertEquals(productDTO, result);

        verify(productRepository).existsByName(requestDTO.name());
        verify(productRepository).existsBySku(requestDTO.sku());
        verify(productRepository).save(any(ProductEntity.class));
        verify(productMapper).entityToDTO(product);
        verifyNoMoreInteractions(productRepository, productMapper);
    }

    @Test
    void create_whenNameAlreadyExists_thenThrowProductNameAlreadyExistsException()
    {
        // Given
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                ProductStatus.ACTIVE,
                Category.GRAPHIC_CARD,
                "NEW-GPU-001",
                "Existing GPU",
                "A graphics card",
                BigDecimal.valueOf(1200),
                30
        );

        when(productRepository.existsByName(requestDTO.name())).thenReturn(true);

        // When
        assertThrows(ProductNameAlreadyExistsException.class, () -> productService.create(requestDTO));

        // Then
        verify(productRepository).existsByName(requestDTO.name());
        verifyNoMoreInteractions(productRepository);
        verifyNoInteractions(productMapper);
    }

    @Test
    void create_whenSkuAlreadyExists_thenThrowProductSkuAlreadyExistsException()
    {
        // Given
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                ProductStatus.ACTIVE,
                Category.GRAPHIC_CARD,
                "EXISTING-GPU-001",
                "New GPU",
                "A graphics card",
                BigDecimal.valueOf(1200),
                30
        );

        when(productRepository.existsByName(requestDTO.name())).thenReturn(false);
        when(productRepository.existsBySku(requestDTO.sku())).thenReturn(true);

        // When
        assertThrows(ProductSkuAlreadyExistsException.class, () -> productService.create(requestDTO));

        // Then
        verify(productRepository).existsByName(requestDTO.name());
        verify(productRepository).existsBySku(requestDTO.sku());
        verifyNoMoreInteractions(productRepository);
        verifyNoInteractions(productMapper);
    }

    @Test
    void getById_whenActiveProductHasStock_thenReturnProduct()
    {
        // Given
        int id = 1;
        ProductEntity product = ProductFactory.graphicCard();
        ProductResponseDTO productDTO = ProductFactory.graphicCardDTO();

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productMapper.entityToDTO(product)).thenReturn(productDTO);

        // When
        ProductResponseDTO result = productService.getById(id);

        // Then
        assertEquals(productDTO, result);
    }

    @Test
    void getById_whenProductDoesNotExist_thenThrowProductNotFoundException()
    {
        // Given
        int id = 999;
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // When
        assertThrows(ProductNotFoundException.class, () -> productService.getById(id));

        // Then
        verify(productRepository).findById(id);
        verifyNoMoreInteractions(productRepository);
        verifyNoInteractions(productMapper);
    }

    @Test
    void getById_whenProductIsInactive_thenThrowProductOutOfStockException()
    {
        // Given
        int id = 1;
        ProductEntity product = ProductFactory.graphicCard();
        product.setStatus(ProductStatus.INACTIVE);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        // When
        assertThrows(ProductOutOfStockException.class, () -> productService.getById(id));

        // Then
        verify(productRepository).findById(id);
        verifyNoMoreInteractions(productRepository);
        verifyNoInteractions(productMapper);
    }

    @Test
    void getById_whenProductHasNoStock_thenThrowProductOutOfStockException()
    {
        // Given
        int id = 1;
        ProductEntity product = ProductFactory.graphicCard();
        product.setStock(0);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        // When
        assertThrows(ProductOutOfStockException.class, () -> productService.getById(id));

        // Then
        verify(productRepository).findById(id);
        verifyNoMoreInteractions(productRepository);
        verifyNoInteractions(productMapper);
    }

    @Test
    void getBySku_whenProductExists_thenReturnProduct()
    {
        // Given
        String sku = "GV-N108T GAMING-OC-11G";
        ProductEntity product = ProductFactory.graphicCard();
        ProductResponseDTO productDTO = ProductFactory.graphicCardDTO();

        when(productRepository.findBySku(sku)).thenReturn(Optional.of(product));
        when(productMapper.entityToDTO(product)).thenReturn(productDTO);

        // When
        ProductResponseDTO result = productService.getBySku(sku);

        // Then
        assertEquals(productDTO, result);
    }

    @Test
    void getBySku_whenProductDoesNotExist_thenThrowProductNotFoundException()
    {
        // Given
        String sku = "MISSING-SKU";

        when(productRepository.findBySku(sku)).thenReturn(Optional.empty());

        // When
        assertThrows(ProductNotFoundException.class, () -> productService.getBySku(sku));

        // Then
        verify(productRepository).findBySku(sku);
        verifyNoMoreInteractions(productRepository);
        verifyNoInteractions(productMapper);
    }

    @Test
    void getAllByName_whenMatchingProductsExist_thenReturnProductPage()
    {
        // Given
        String name = "gpu";
        Pageable pageable = PageRequest.of(0, 10);
        ProductEntity product = ProductFactory.graphicCard();
        ProductResponseDTO productDTO = ProductFactory.graphicCardDTO();
        Page<ProductEntity> products = new PageImpl<>(List.of(product));

        when(productRepository.findByNameContainingIgnoreCaseAndStatus(name, ProductStatus.ACTIVE, pageable)).thenReturn(products);
        when(productMapper.entityToDTO(product)).thenReturn(productDTO);

        // When
        Page<ProductResponseDTO> result = productService.getAllByName(name, pageable);

        // Then
        assertEquals(List.of(productDTO), result.getContent());
    }

    @Test
    void getAllByName_whenNoProductsMatch_thenReturnEmptyPage()
    {
        // Given
        String name = "missing";

        Pageable pageable = PageRequest.of(0, 10);

        when(productRepository.findByNameContainingIgnoreCaseAndStatus(name, ProductStatus.ACTIVE, pageable)).thenReturn(Page.empty(pageable));

        // When
        Page<ProductResponseDTO> result = productService.getAllByName(name, pageable);

        // Then
        assertTrue(result.isEmpty());

        verifyNoInteractions(productMapper);
    }

    @Test
    void getAllByCategory_whenProductsWithCategoryExist_thenReturnProductPage()
    {
        // Given
        Category category = Category.GRAPHIC_CARD;
        Pageable pageable = PageRequest.of(0, 10);
        ProductEntity product = ProductFactory.graphicCard();
        ProductResponseDTO productDTO = ProductFactory.graphicCardDTO();
        Page<ProductEntity> products = new PageImpl<>(List.of(product));

        when(productRepository.findAllByCategoryAndStatus(category, ProductStatus.ACTIVE, pageable)).thenReturn(products);
        when(productMapper.entityToDTO(product)).thenReturn(productDTO);

        // When
        Page<ProductResponseDTO> result = productService.getAllByCategory(category, pageable);

        // Then
        assertEquals(List.of(productDTO), result.getContent());
    }

    @Test
    void getAllByCategory_whenNoProductsHaveCategory_thenReturnEmptyPage()
    {
        // Given
        Category category = Category.PROCESSOR;
        Pageable pageable = PageRequest.of(0, 10);


        when(productRepository.findAllByCategoryAndStatus(category, ProductStatus.ACTIVE, pageable)).thenReturn(Page.empty(pageable));

        // When
        Page<ProductResponseDTO> result = productService.getAllByCategory(category, pageable);

        // Then
        assertTrue(result.isEmpty());

        verifyNoInteractions(productMapper);
    }

    @Test
    void getRecentlyUpdated_whenProductsExist_thenReturnProductPage()
    {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        ProductEntity product = ProductFactory.graphicCard();
        ProductResponseDTO productDTO = ProductFactory.graphicCardDTO();
        Page<ProductEntity> products = new PageImpl<>(List.of(product));

        when(productRepository.findTop30ByStatusOrderByUpdatedAtDesc(pageable, ProductStatus.ACTIVE)).thenReturn(products);
        when(productMapper.entityToDTO(product)).thenReturn(productDTO);

        // When
        Page<ProductResponseDTO> result = productService.getRecentlyUpdated(pageable);

        // Then
        assertEquals(List.of(productDTO), result.getContent());
    }

    @Test
    void getRecentlyUpdated_whenNoProductsExist_thenReturnEmptyPage()
    {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findTop30ByStatusOrderByUpdatedAtDesc(pageable, ProductStatus.ACTIVE)).thenReturn(Page.empty(pageable));

        // When
        Page<ProductResponseDTO> result = productService.getRecentlyUpdated(pageable);

        // Then
        assertTrue(result.isEmpty());

        verifyNoInteractions(productMapper);
    }

    @Test
    void updateById_whenRequestFieldsArePresent_thenUpdateAndReturnProduct()
    {
        // Given
        int id = 1;
        ProductEntity product = ProductFactory.graphicCard();
        ProductResponseDTO productDTO = ProductFactory.graphicCardDTO();
        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                ProductStatus.INACTIVE,
                Category.PROCESSOR,
                "UPDATED-SKU",
                "Updated product",
                "Updated description",
                BigDecimal.valueOf(1500),
                75
        );

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(productMapper.entityToDTO(product)).thenReturn(productDTO);

        // When
        ProductResponseDTO result = productService.updateById(id, requestDTO);

        // Then
        assertEquals(productDTO, result);
        assertEquals(ProductStatus.INACTIVE, product.getStatus());
        assertEquals(Category.PROCESSOR, product.getCategory());
        assertEquals(requestDTO.sku(), product.getSku());
        assertEquals(requestDTO.name(), product.getName());
        assertEquals(requestDTO.description(), product.getDescription());
        assertEquals(BigDecimal.valueOf(1500), product.getPrice());
        assertEquals(75, product.getStock());

        verify(productRepository).save(product);
    }

    @Test
    void updateById_whenRequestFieldsAreNull_thenKeepCurrentValues()
    {
        // Given
        int id = 1;
        ProductEntity product = ProductFactory.graphicCard();
        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ProductResponseDTO productDTO = ProductFactory.graphicCardDTO();

        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(productMapper.entityToDTO(product)).thenReturn(productDTO);

        // When
        ProductResponseDTO result = productService.updateById(id, requestDTO);

        // Then
        assertEquals(productDTO, result);
        assertEquals(ProductStatus.ACTIVE, product.getStatus());
        assertEquals(productDTO.category(), product.getCategory());
        assertEquals(productDTO.sku(), product.getSku());
        assertEquals(productDTO.name(), product.getName());
        assertEquals(productDTO.description(), product.getDescription());
        assertEquals(BigDecimal.valueOf(1000), product.getPrice());
        assertEquals(50, product.getStock());

        verify(productRepository).save(product);
    }

    @Test
    void updateById_whenProductDoesNotExist_thenThrowProductNotFoundException()
    {
        // Given
        int id = 999;
        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                null,
                null,
                null,
                "Updated product",
                null,
                null,
                null
        );

        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // When
        assertThrows(ProductNotFoundException.class, () -> productService.updateById(id, requestDTO));

        // Then
        verify(productRepository).findById(id);
        verifyNoMoreInteractions(productRepository);
        verifyNoInteractions(productMapper);
    }

    @Test
    void increaseStockById_whenProductIsActive_thenIncreaseStock()
    {
        // Given
        int id = 1;
        ProductEntity product = ProductFactory.graphicCard();
        StockRequestDTO requestDTO = new StockRequestDTO(25);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        // When
        productService.increaseStockById(id, requestDTO);

        // Then
        assertEquals(75, product.getStock());
        assertEquals(ProductStatus.ACTIVE, product.getStatus());

        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void increaseStockById_whenProductIsInactive_thenIncreaseStockAndActivate()
    {
        // Given
        int id = 1;
        ProductEntity product = ProductFactory.graphicCard();
        product.setStatus(ProductStatus.INACTIVE);
        StockRequestDTO requestDTO = new StockRequestDTO(25);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        // When
        productService.increaseStockById(id, requestDTO);

        // Then
        assertEquals(75, product.getStock());
        assertEquals(ProductStatus.ACTIVE, product.getStatus());

        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void increaseStockById_whenProductDoesNotExist_thenThrowProductNotFoundException()
    {
        // Given
        int id = 999;
        StockRequestDTO requestDTO = new StockRequestDTO(25);
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // When
        assertThrows(ProductNotFoundException.class, () -> productService.increaseStockById(id, requestDTO));

        // Then
        verify(productRepository).findById(id);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void decreaseStockById_whenEnoughStockRemains_thenDecreaseStock()
    {
        // Given
        int id = 1;
        ProductEntity product = ProductFactory.graphicCard();
        StockRequestDTO requestDTO = new StockRequestDTO(10);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        // When
        productService.decreaseStockById(id, requestDTO);

        // Then
        assertEquals(40, product.getStock());
        assertEquals(ProductStatus.ACTIVE, product.getStatus());

        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void decreaseStockById_whenStockReachesZero_thenDeactivateProduct()
    {
        // Given
        int id = 1;

        ProductEntity product = ProductFactory.graphicCard();
        product.setStock(10);
        StockRequestDTO requestDTO = new StockRequestDTO(10);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        // When
        productService.decreaseStockById(id, requestDTO);

        // Then
        assertEquals(0, product.getStock());
        assertEquals(ProductStatus.INACTIVE, product.getStatus());

        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void decreaseStockById_whenProductIsInactive_thenThrowProductOutOfStockException()
    {
        // Given
        int id = 1;

        ProductEntity product = ProductFactory.graphicCard();
        product.setStatus(ProductStatus.INACTIVE);
        StockRequestDTO requestDTO = new StockRequestDTO(10);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        // When
        assertThrows(ProductOutOfStockException.class, () -> productService.decreaseStockById(id, requestDTO));

        // Then
        verify(productRepository).findById(id);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void decreaseStockById_whenRequestedStockExceedsAvailableStock_thenThrowInsufficientStockException()
    {
        // Given
        int id = 1;
        ProductEntity product = ProductFactory.graphicCard();
        StockRequestDTO requestDTO = new StockRequestDTO(51);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        // When
        assertThrows(InsufficientStockException.class, () -> productService.decreaseStockById(id, requestDTO));

        // Then
        verify(productRepository).findById(id);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void decreaseStockById_whenProductDoesNotExist_thenThrowProductNotFoundException()
    {
        // Given
        int id = 999;
        StockRequestDTO requestDTO = new StockRequestDTO(10);
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // When
        assertThrows(ProductNotFoundException.class, () -> productService.decreaseStockById(id, requestDTO));

        // Then
        verify(productRepository).findById(id);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void deactivateById_whenProductExists_thenDeactivateAndSaveProduct()
    {
        // Given
        int id = 1;
        ProductEntity product = ProductFactory.graphicCard();
        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        // When
        productService.deactivateById(id);

        // Then
        assertEquals(ProductStatus.INACTIVE, product.getStatus());

        verify(productRepository).save(product);
        verifyNoMoreInteractions(productRepository);
    }

    @Test
    void deactivateById_whenProductDoesNotExist_thenThrowProductNotFoundException()
    {
        // Given
        int id = 999;
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        // When
        assertThrows(ProductNotFoundException.class, () -> productService.deactivateById(id));

        // Then
        verify(productRepository).findById(id);
        verifyNoMoreInteractions(productRepository);
    }
}
