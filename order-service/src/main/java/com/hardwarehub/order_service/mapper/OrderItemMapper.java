package com.hardwarehub.order_service.mapper;

import com.hardwarehub.order_service.dto.OrderItemResponseDTO;
import com.hardwarehub.order_service.entity.OrderItemEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderItemMapper
{
    OrderItemResponseDTO entityToDTO(OrderItemEntity orderItem);
}
