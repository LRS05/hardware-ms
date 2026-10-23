package com.hardwarehub.order_service.service;

import com.hardwarehub.order_service.client.ProductClient;
import com.hardwarehub.order_service.client.dto.ProductResponseDTO;
import com.hardwarehub.order_service.dto.OrderResponseDTO;
import com.hardwarehub.order_service.entity.OrderEntity;
import com.hardwarehub.order_service.entity.OrderItemEntity;
import com.hardwarehub.order_service.enums.OrderStatus;
import com.hardwarehub.order_service.exception.EmptyOrderException;
import com.hardwarehub.order_service.exception.ItemNotFoundException;
import com.hardwarehub.order_service.exception.OrderNotFoundException;
import com.hardwarehub.order_service.exception.ProductNotFoundException;
import com.hardwarehub.order_service.exception.ProductOutOfStockException;
import com.hardwarehub.order_service.mapper.OrderItemMapper;
import com.hardwarehub.order_service.mapper.OrderMapper;
import com.hardwarehub.order_service.repository.OrderItemRepository;
import com.hardwarehub.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * The user ID is extracted from the JWT claims.
 */
@Service
@RequiredArgsConstructor
public class OrderService
{
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductClient productClient;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemMapper orderItemMapper;

    /**
     * El metodo es estrictamente utilizado por el user-service
     * al momento de registrar un usuario.
     * No sera utilizado en ningun otro momento y tampoco sera accessible.
     */
    public void create(int userId)
    {
        if (!orderRepository.existsByUserIdAndStatus(userId, OrderStatus.ACTIVE))
        {
            orderRepository.save(buildOrder(userId));
        }
    }

    @Transactional(noRollbackFor = EmptyOrderException.class)
    public OrderResponseDTO checkout(int userId)
    {
        OrderEntity order = findActiveOrderOrThrow(userId);

        refreshOrderAndItemsPrice(order);

        if (order.getOrderItems().isEmpty())
        {
            throw new EmptyOrderException("Cannot checkout an empty order.");
        }

        reserveProductStock(order);

        order.setStatus(OrderStatus.PENDING);

        return orderMapper.entityToDTO(orderRepository.save(order));
    }

    @Transactional
    public OrderResponseDTO reopen(int userId)
    {
        OrderEntity order = findPendingOrderOrThrow(userId);

        releaseProductStock(order);

        order.setStatus(OrderStatus.ACTIVE);

        return orderMapper.entityToDTO(orderRepository.save(order));
    }

    @Transactional
    public void refreshActiveOrder(int userId)
    {
        OrderEntity order = findActiveOrderOrThrow(userId);
        refreshOrderAndItemsPrice(order);
    }

    @Transactional
    public void clearActiveOrder(int userId)
    {
        OrderEntity order = findActiveOrderOrThrow(userId);

        for (OrderItemEntity item : new ArrayList<>(order.getOrderItems()))
        {
            order.removeItem(item);
        }

        order.setTotalPrice(BigDecimal.ZERO);
    }

    public OrderResponseDTO getActive(int userId)
    {
        OrderEntity order = findActiveOrderOrThrow(userId);
        return orderMapper.entityToDTO(order);
    }

    public OrderResponseDTO getPending(int userId)
    {
        OrderEntity order = findPendingOrderOrThrow(userId);
        return orderMapper.entityToDTO(order);
    }

    public OrderResponseDTO getById(int id, int userId)
    {
        OrderEntity order = orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found."));
        return orderMapper.entityToDTO(order);
    }

    public OrderResponseDTO getById(int id)
    {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found."));
        return orderMapper.entityToDTO(order);
    }

    public Page<OrderResponseDTO> getAll(int userId, Pageable pageable)
    {
        Page<OrderEntity> orders = orderRepository.findAllByUserIdAndStatusNotOrderByCreatedAtDesc(
                userId,
                OrderStatus.ACTIVE,
                pageable
        );
        return orders.map(orderMapper::entityToDTO);
    }

    public Page<OrderResponseDTO> getAllByUserId(int userId, Pageable pageable)
    {
        Page<OrderEntity> orders = orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        return orders.map(orderMapper::entityToDTO);
    }

    public Page<OrderResponseDTO> getAllByStatus(int userId, OrderStatus status, Pageable pageable)
    {
        Page<OrderEntity> orders = orderRepository
                .findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        return orders.map(orderMapper::entityToDTO);
    }

    @Transactional
    public void addItem(int userId, int productId)
    {
        ProductResponseDTO product = productClient.getById(productId);

        OrderEntity currentOrder = findActiveOrderOrThrow(userId);

        OrderItemEntity item = orderItemRepository.findByOrderIdAndProductId(currentOrder.getId(), product.id())
                .orElseGet(() -> buildItem(product, currentOrder));

        updateItemQuantityAndPrice(item, 1, product);

        updateOrderPrice(currentOrder);

        orderItemRepository.save(item);
    }

    @Transactional
    public void updateItemQuantity(int itemId, int userId, int quantity)
    {
        OrderItemEntity item = findActiveItemByIdAndUserId(itemId, userId);

        ProductResponseDTO product = productClient.getById(item.getProductId());

        updateItemQuantityAndPrice(item, quantity, product);

        updateOrderPrice(item.getOrder());
    }

    @Transactional
    public void deleteItemById(int itemId, int userId)
    {
        OrderItemEntity item = findActiveItemByIdAndUserId(itemId, userId);

        OrderEntity order = item.getOrder();

        order.removeItem(item);

        updateOrderPrice(order);
    }

    private void updateItemQuantityAndPrice(OrderItemEntity item, int newQuantity, ProductResponseDTO product)
    {
        if (newQuantity > product.stock()) throw new ProductOutOfStockException("Only " + product.stock() + " units are available.");

        if (newQuantity < 1)
        {
            OrderEntity order = item.getOrder();
            order.removeItem(item);
            return;
        }

        item.setQuantity(newQuantity);

        updateItemPrice(item, product);
    }

    private void updateItemPrice(OrderItemEntity item, ProductResponseDTO product)
    {
        BigDecimal newPrice = product.price().multiply(BigDecimal.valueOf(item.getQuantity()));
        item.setPrice(newPrice);
    }

    private void refreshOrderAndItemsPrice(OrderEntity order)
    {
        BigDecimal orderTotalPrice = BigDecimal.ZERO;

        for (OrderItemEntity item : new ArrayList<>(order.getOrderItems()))
        {
            try
            {
                ProductResponseDTO product = productClient.getById(item.getProductId());

                // The stock is always > 0, else throws exception.
                if (item.getQuantity() > product.stock()) item.setQuantity(product.stock());

                updateItemPrice(item, product);

                orderTotalPrice = orderTotalPrice.add(item.getPrice());
            }
            catch (ProductNotFoundException | ProductOutOfStockException e)
            {
                order.removeItem(item);
            }
        }
        order.setTotalPrice(orderTotalPrice);
    }

    private OrderEntity buildOrder(int userId)
    {
        return OrderEntity.builder()
                .userId(userId)
                .status(OrderStatus.ACTIVE)
                .totalPrice(BigDecimal.ZERO)
                .build();
    }

    private OrderItemEntity buildItem(ProductResponseDTO product, OrderEntity order)
    {
        OrderItemEntity item = OrderItemEntity.builder()
                .productId(product.id())
                .productName(product.name())
                .price(BigDecimal.ZERO)
                .quantity(0)
                .build();

        order.addItem(item);

        return item;
    }

    private void updateOrderPrice(OrderEntity order)
    {
        BigDecimal newPrice = BigDecimal.ZERO;

        for (OrderItemEntity item : order.getOrderItems())
        {
            newPrice = newPrice.add(item.getPrice());
        }

        order.setTotalPrice(newPrice);
    }

    private void reserveProductStock(OrderEntity order)
    {
        order.getOrderItems()
                .forEach(o -> productClient.decreaseStockById(
                        o.getProductId(),
                        o.getQuantity()
                ));
    }

    private void releaseProductStock(OrderEntity order)
    {
        order.getOrderItems()
                .forEach(o -> productClient.increaseStockById(
                        o.getProductId(),
                        o.getQuantity()
                ));
    }

    private OrderEntity findPendingOrderOrThrow(int userId)
    {
        return orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING)
                .orElseThrow(() -> new OrderNotFoundException("Pending order not found."));
    }

    private OrderEntity findActiveOrderOrThrow(int userId)
    {
        return orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)
                .orElseThrow(() -> new OrderNotFoundException("Active order not found."));
    }

    private OrderItemEntity findActiveItemByIdAndUserId(int id, int userId)
    {
        return orderItemRepository.findActiveItemByIdAndUserId(id, userId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found."));
    }
}
