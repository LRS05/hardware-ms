package com.hardwarehub.order_service.service;

import com.hardwarehub.order_service.client.PaymentClient;
import com.hardwarehub.order_service.client.ProductClient;
import com.hardwarehub.order_service.client.dto.ItemReservationDTO;
import com.hardwarehub.order_service.client.dto.ProductResponseDTO;
import com.hardwarehub.order_service.client.dto.StockReservationRequestDTO;
import com.hardwarehub.order_service.dto.OrderResponseDTO;
import com.hardwarehub.order_service.dto.PaymentLinkResponseDTO;
import com.hardwarehub.order_service.dto.PaymentRequestDTO;
import com.hardwarehub.order_service.entity.OrderEntity;
import com.hardwarehub.order_service.entity.OrderItemEntity;
import com.hardwarehub.order_service.enums.OrderStatus;
import com.hardwarehub.order_service.enums.PaymentProvider;
import com.hardwarehub.order_service.exception.*;
import com.hardwarehub.order_service.mapper.OrderItemMapper;
import com.hardwarehub.order_service.mapper.OrderMapper;
import com.hardwarehub.order_service.repository.OrderItemRepository;
import com.hardwarehub.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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
    private final PaymentClient paymentClient;

    @Value("${orders.payment-timeout-minutes}")
    private long paymentTimeoutMinutes;

    @Transactional(noRollbackFor = EmptyOrderException.class)
    public OrderResponseDTO checkout(int userId)
    {
        OrderEntity order = findActiveOrderForUpdateOrThrow(userId);

        refreshOrderAndItemsPrice(order);

        validateOrderIsNotEmpty(order);

        updateStatus(order, OrderStatus.PENDING);

        reserveProductStock(order);

        return orderMapper.entityToDTO(orderRepository.save(order));
    }

    @Transactional
    public OrderResponseDTO reopen(int userId)
    {
        OrderEntity order = findPendingOrderForUpdateOrThrow(userId);

        paymentClient.cancelCheckout(order.getId());

        releaseProductStock(order);

        updateStatus(order, OrderStatus.ACTIVE);

        return orderMapper.entityToDTO(orderRepository.save(order));
    }

    @Transactional
    public void refreshActiveOrder(int userId)
    {
        OrderEntity order = findActiveOrderForUpdateOrThrow(userId);

        refreshOrderAndItemsPrice(order);
    }

    @Transactional
    public void clearActiveOrder(int userId)
    {
        OrderEntity order = findActiveOrderForUpdateOrThrow(userId);

        for (OrderItemEntity item : new ArrayList<>(order.getOrderItems()))
        {
            order.removeItem(item);
        }

        order.setTotalPrice(BigDecimal.ZERO);
    }

    // Only used internally when a user completes a purchase.
    @Transactional
    public void complete(int orderId)
    {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found."));

        if (order.getStatus() == OrderStatus.COMPLETED)
        {
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING)
        {
            throw new InvalidOrderStatusException("Only pending orders can be completed.");
        }

        updateStatus(order, OrderStatus.COMPLETED);
    }

    @Transactional
    public PaymentLinkResponseDTO createPaymentCheckout(int userId, PaymentProvider provider)
    {
        OrderEntity order = findPendingOrderForUpdateOrThrow(userId);

        if (!order.getExpiresAt().isAfter(Instant.now()))
        {
            throw new InvalidOrderStatusException("The payment deadline has expired.");
        }

        return paymentClient.createCheckout(
                new PaymentRequestDTO(order.getId(), order.getTotalPrice(), provider, order.getExpiresAt())
        );
    }

    @Transactional
    public void cancel(int userId)
    {
        OrderEntity order = findPendingOrderForUpdateOrThrow(userId);

        // Cancels existing payment checkouts for this order.
        paymentClient.cancelCheckout(order.getId());

        releaseProductStock(order);

        updateStatus(order, OrderStatus.CANCELED);
    }

    @Transactional
    public void expire(int orderId)
    {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found."));

        if (order.getStatus() != OrderStatus.PENDING
                || order.getExpiresAt().isAfter(Instant.now()))
        {
            return;
        }

        paymentClient.cancelCheckout(orderId);

        releaseProductStock(order);

        updateStatus(order, OrderStatus.CANCELED);
    }

    @Transactional(readOnly = true)
    public List<Integer> findExpiredOrderIds()
    {
        return orderRepository.findExpiredOrderIds(OrderStatus.PENDING, Instant.now(), PageRequest.of(0, 100));
    }

    // Return an empty order without persisting it if the user has no active order.
    public OrderResponseDTO getActive(int userId)
    {
        OrderEntity order = orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)
                .orElseGet(() -> buildOrder(userId));

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

        OrderEntity currentOrder = findActiveOrderOrCreate(userId);

        OrderItemEntity item = orderItemRepository.findByOrderIdAndProductId(currentOrder.getId(), product.id())
                .orElseGet(() -> buildItem(product, currentOrder));

        updateItemQuantityAndPrice(item, item.getQuantity() + 1, product);

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
        // Blocks the original order
        OrderItemEntity item = findActiveItemByIdAndUserId(itemId, userId);

        OrderEntity order = item.getOrder();

        order.removeItem(item);

        updateOrderPrice(order);
    }

    private void updateItemQuantityAndPrice(OrderItemEntity item, int newQuantity, ProductResponseDTO product)
    {
        if (newQuantity < 1) throw new InvalidQuantityException("Quantity must be greater than zero.");

        if (newQuantity > product.stock()) throw new ProductOutOfStockException("Only " + product.stock() + " units are available.");

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
        List<ItemReservationDTO> items = order.getOrderItems().stream()
                .map(item -> new ItemReservationDTO(item.getProductId(), item.getQuantity()))
                .toList();

        productClient.reserveStock(new StockReservationRequestDTO(order.getId(), order.getExpiresAt(), items));
    }

    private void releaseProductStock(OrderEntity order)
    {
        List<ItemReservationDTO> items = order.getOrderItems().stream()
                .map(item -> new ItemReservationDTO(item.getProductId(), item.getQuantity()))
                .toList();

        productClient.releaseStock(new StockReservationRequestDTO(order.getId(), order.getExpiresAt(), items));
    }

    private OrderEntity findPendingOrderOrThrow(int userId)
    {
        return orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING)
                .orElseThrow(() -> new OrderNotFoundException("Pending order not found."));
    }

    private OrderEntity findActiveOrderOrCreate(int userId)
    {
        if (orderRepository.existsByUserIdAndStatus(userId, OrderStatus.PENDING))
        {
            throw new PendingOrderExistsException("You have a pending order.");
        }

        return orderRepository.findByUserIdAndStatusForUpdate(userId, OrderStatus.ACTIVE.name())
                .orElseGet(() -> orderRepository.save(buildOrder(userId)));
    }

    private OrderItemEntity findActiveItemByIdAndUserId(int id, int userId)
    {
        orderRepository.findByUserIdAndStatusForUpdate(userId, OrderStatus.ACTIVE.name())
                .orElseThrow(() -> new ItemNotFoundException("Item not found."));

        return orderItemRepository.findActiveItemByIdAndUserId(id, userId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found."));
    }

    private OrderEntity findActiveOrderForUpdateOrThrow(int userId)
    {
        if (orderRepository.existsByUserIdAndStatus(userId, OrderStatus.PENDING))
        {
            throw new PendingOrderExistsException("You already have a pending order.");
        }

        return orderRepository.findByUserIdAndStatusForUpdate(userId, OrderStatus.ACTIVE.name())
                .orElseThrow(() -> new EmptyOrderException("Order is empty."));
    }

    private OrderEntity findPendingOrderForUpdateOrThrow(int userId)
    {
        return orderRepository.findByUserIdAndStatusForUpdate(userId, OrderStatus.PENDING.name())
                .orElseThrow(() -> new OrderNotFoundException("Pending order not found."));
    }

    private void validateOrderIsNotEmpty(OrderEntity order)
    {
        if (order.getOrderItems().isEmpty())
        {
            throw new EmptyOrderException("Order is empty.");
        }
    }

    private void updateStatus(OrderEntity order, OrderStatus status)
    {
        order.setStatus(status);

        if (status == OrderStatus.PENDING)
        {
            order.setExpiresAt(
                    Instant.now().plus(Duration.ofMinutes(paymentTimeoutMinutes)).truncatedTo(ChronoUnit.MICROS)
            );
        }
        else
        {
            order.setExpiresAt(null);
        }
    }
}
