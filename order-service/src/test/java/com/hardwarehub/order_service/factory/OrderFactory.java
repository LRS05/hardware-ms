package com.hardwarehub.order_service.factory;

import com.hardwarehub.order_service.dto.OrderItemResponseDTO;
import com.hardwarehub.order_service.dto.OrderResponseDTO;
import com.hardwarehub.order_service.entity.OrderEntity;
import com.hardwarehub.order_service.entity.OrderItemEntity;
import com.hardwarehub.order_service.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderFactory
{
    public static OrderEntity completeOrder()
    {
        return buildOrder(1, 1, OrderStatus.COMPLETED, BigDecimal.valueOf(2000), List.of(graphicCardOne()));
    }

    public static OrderEntity activeOrder()
    {
        return buildOrder(2, 1, OrderStatus.ACTIVE, BigDecimal.valueOf(2000), List.of(graphicCardTwo()));
    }

    public static OrderItemEntity graphicCardOne()
    {
        return buildItem(1, 1, "NVIDIA GTX 1080-TI", BigDecimal.valueOf(2000), 2);
    }

    public static OrderItemEntity graphicCardTwo()
    {
        return buildItem(2, 1, "NVIDIA GTX 1080-TI", BigDecimal.valueOf(2000), 2);
    }

    public static OrderResponseDTO completeOrderDTO()
    {
        return entityToDTO(completeOrder());
    }

    public static OrderResponseDTO activeOrderDTO()
    {
        return entityToDTO(activeOrder());
    }

    public static OrderItemResponseDTO graphicCardOneDTO()
    {
        return entityToDTO(graphicCardOne());
    }

    public static OrderItemResponseDTO graphicCardTwoDTO()
    {
        return entityToDTO(graphicCardTwo());
    }

    private static OrderEntity buildOrder(int id, int userId, OrderStatus status, BigDecimal totalPrice, List<OrderItemEntity> items)
    {
        OrderEntity order = OrderEntity.builder()
                .id(id)
                .userId(userId)
                .status(status)
                .totalPrice(totalPrice)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        for (OrderItemEntity item : items)
        {
            order.addItem(item);
        }

        return order;
    }

    private static OrderItemEntity buildItem(int id, int productId, String productName, BigDecimal price, int quantity)
    {
        return OrderItemEntity.builder()
                .id(id)
                .productId(productId)
                .productName(productName)
                .price(price)
                .quantity(quantity)
                .build();
    }

    private static OrderResponseDTO entityToDTO(OrderEntity order)
    {
        return new OrderResponseDTO(
                order.getId(),
                order.getTotalPrice(),
                itemsToDTO(order.getOrderItems())
        );
    }

    private static OrderItemResponseDTO entityToDTO(OrderItemEntity item)
    {
        return new OrderItemResponseDTO(
                item.getProductName(),
                item.getPrice(),
                item.getQuantity()
        );
    }

    private static List<OrderItemResponseDTO> itemsToDTO(List<OrderItemEntity> items)
    {
        return items.stream()
                .map(OrderFactory::entityToDTO)
                .toList();
    }

    private static List<OrderResponseDTO> entityToDTO(List<OrderEntity> orders)
    {
        return orders.stream()
                .map(OrderFactory::entityToDTO)
                .toList();
    }
}
