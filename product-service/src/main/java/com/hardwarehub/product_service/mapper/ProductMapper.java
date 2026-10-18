package com.hardwarehub.product_service.mapper;

import com.hardwarehub.product_service.dto.ProductResponseDTO;
import com.hardwarehub.product_service.entity.ProductEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper
{
    ProductResponseDTO entityToDTO(ProductEntity product);
}
