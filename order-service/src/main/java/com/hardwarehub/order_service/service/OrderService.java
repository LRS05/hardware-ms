package com.hardwarehub.order_service.service;

import com.hardwarehub.order_service.client.ProductClient;
import com.hardwarehub.order_service.client.dto.ProductResponseDTO;
import com.hardwarehub.order_service.dto.OrderItemResponseDTO;
import com.hardwarehub.order_service.dto.OrderResponseDTO;
import com.hardwarehub.order_service.dto.QuantityRequestDTO;
import com.hardwarehub.order_service.entity.OrderEntity;
import com.hardwarehub.order_service.entity.OrderItemEntity;
import com.hardwarehub.order_service.entity.enums.OrderStatus;
import com.hardwarehub.order_service.exception.EmptyOrderException;
import com.hardwarehub.order_service.exception.ProductNotFoundException;
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
import java.time.LocalDateTime;
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
     * Creates the initial active order when a user registers.
     * Intended for internal use only.
     */
    public void create(int userId)
    {
        orderRepository.save(buildOrder(userId));
    }

    /**
     * Checks out the order when the user is ready to pay.
     * Refreshes product availability, quantities, and prices.
     * If all items are removed due to insufficient stock or unavailable products,
     * the checkout is canceled without rolling back the order updates.
     */
    @Transactional(noRollbackFor = EmptyOrderException.class)
    public OrderResponseDTO checkout(int userId)
    {
        OrderEntity order = findActiveOrderOrThrow(userId);
        refreshOrderAndItemsPrice(order);

        if (order.getOrderItems().isEmpty())
        {
            throw new EmptyOrderException("Cannot checkout an empty order.");
        }

        order.setStatus(OrderStatus.PENDING);
        return orderMapper.entityToDTO(orderRepository.save(order));
    }

    @Transactional
    public OrderResponseDTO reopen(int userId)
    {
        OrderEntity order = findPendingOrderOrThrow(userId);
        order.setStatus(OrderStatus.ACTIVE);
        return orderMapper.entityToDTO(orderRepository.save(order));
    }

    @Transactional
    public OrderResponseDTO getCurrent(int userId)
    {
        OrderEntity order = findActiveOrderOrThrow(userId);

        refreshOrderAndItemsPrice(order);

        return orderMapper.entityToDTO(order);
    }

    public OrderResponseDTO getById(int id, int userId)
    {
        OrderEntity order = orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Order not found."));
        return orderMapper.entityToDTO(order);
    }

    public OrderResponseDTO getById(int id)
    {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found."));
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

    /**
     * Adds a product to the user's active order, creating a new item if it doesn't exist.
     * Validates stock availability and updates the item's quantity, price, and order total.
     */
    @Transactional
    public OrderItemResponseDTO addItem(int userId, int productId, QuantityRequestDTO requestDTO)
    {
        ProductResponseDTO product = productClient.getById(productId);

        OrderEntity currentOrder = findActiveOrderOrThrow(userId);

        OrderItemEntity item = orderItemRepository.findByOrderIdAndProductId(currentOrder.getId(), product.id())
                .orElseGet(() -> buildItem(product, currentOrder));

        updateItemQuantityAndPrice(item, requestDTO.quantity(), product);

        updateOrderPrice(currentOrder);

        return orderItemMapper.entityToDTO(orderItemRepository.save(item));
    }

    @Transactional
    public OrderItemResponseDTO increaseItemQuantity(int itemId, int userId)
    {
        OrderItemEntity item = findActiveItemByIdAndUserId(itemId, userId);

        ProductResponseDTO product = productClient.getById(item.getProductId());

        updateItemQuantityAndPrice(item, 1, product);

        updateOrderPrice(item.getOrder());

        return orderItemMapper.entityToDTO(item);
    }

    @Transactional
    public OrderItemResponseDTO decreaseItemQuantity(int itemId, int userId)
    {
        OrderItemEntity item = findActiveItemByIdAndUserId(itemId, userId);

        ProductResponseDTO product = productClient.getById(item.getProductId());

        updateItemQuantityAndPrice(item, -1, product);

        updateOrderPrice(item.getOrder());

        return orderItemMapper.entityToDTO(item);
    }

    @Transactional
    public void deleteItem(int itemId, int userId)
    {
        OrderItemEntity item = orderItemRepository.findActiveItemByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new RuntimeException("Item not found."));

        OrderEntity order = item.getOrder();

        order.removeItem(item);
        updateOrderPrice(order);
    }

    /*
     * Validates the requested quantity against the product's current stock,
     * updates the item's quantity, and refreshes its price using the
     * official product price.
     */
    private void updateItemQuantityAndPrice(OrderItemEntity item, int newQuantity, ProductResponseDTO product)
    {
        int totalQuantity = item.getQuantity() + newQuantity;

        if (totalQuantity > product.stock()) throw new RuntimeException("Only " + product.stock() + " units are available.");
        if (totalQuantity < 1) throw new RuntimeException("Item quantity must be at least 1.");

        item.setQuantity(totalQuantity);

        updateItemPrice(item, product);
    }

    private void updateItemPrice(OrderItemEntity item, ProductResponseDTO product)
    {
        BigDecimal newPrice = product.price().multiply(BigDecimal.valueOf(item.getQuantity()));
        item.setPrice(newPrice);
    }

    // Updates the item's quantity and price based on the product's current information.
    // If the product does not exist or is out of stock, the item is automatically removed from the order.
    private void refreshOrderAndItemsPrice(OrderEntity order)
    {
        BigDecimal orderTotalPrice = BigDecimal.ZERO;

        for (OrderItemEntity item : new ArrayList<>(order.getOrderItems()))
        {
            try
            {
                ProductResponseDTO product = productClient.getById(item.getProductId());

                if (item.getQuantity() > product.stock()) item.setQuantity(product.stock());

                updateItemPrice(item, product);

                orderTotalPrice = orderTotalPrice.add(item.getPrice());
            }
            catch (ProductNotFoundException e)
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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private OrderItemEntity buildItem(ProductResponseDTO product, OrderEntity order)
    {
        OrderItemEntity item = OrderItemEntity.builder()
                .productId(product.id())
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

    private OrderEntity findPendingOrderOrThrow(int userId)
    {
        return orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING)
                .orElseThrow(() -> new RuntimeException("You don't have a pending order."));
    }

    private OrderEntity findActiveOrderOrThrow(int userId)
    {
        return orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("You already have a pending order. Complete or cancel it before modifying it."));
    }

    private OrderItemEntity findActiveItemByIdAndUserId(int id, int userId)
    {
        return orderItemRepository.findActiveItemByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Item not found."));
    }
}