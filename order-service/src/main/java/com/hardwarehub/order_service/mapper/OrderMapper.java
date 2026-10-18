package com.hardwarehub.order_service.mapper;

import com.hardwarehub.order_service.dto.OrderResponseDTO;
import com.hardwarehub.order_service.entity.OrderEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = OrderItemMapper.class)
public interface OrderMapper
{
    OrderResponseDTO entityToDTO(OrderEntity order);
}
