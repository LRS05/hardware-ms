package com.hardwarehub.order_service.service;

import com.hardwarehub.order_service.client.ProductClient;
import com.hardwarehub.order_service.client.dto.ProductResponseDTO;
import com.hardwarehub.order_service.dto.OrderResponseDTO;
import com.hardwarehub.order_service.entity.OrderEntity;
import com.hardwarehub.order_service.entity.OrderItemEntity;
import com.hardwarehub.order_service.enums.OrderStatus;
import com.hardwarehub.order_service.exception.EmptyOrderException;
import com.hardwarehub.order_service.exception.ItemNotFoundException;
import com.hardwarehub.order_service.exception.InvalidQuantityException;
import com.hardwarehub.order_service.exception.OrderNotFoundException;
import com.hardwarehub.order_service.exception.ProductNotFoundException;
import com.hardwarehub.order_service.exception.ProductOutOfStockException;
import com.hardwarehub.order_service.factory.OrderFactory;
import com.hardwarehub.order_service.mapper.OrderItemMapper;
import com.hardwarehub.order_service.mapper.OrderMapper;
import com.hardwarehub.order_service.repository.OrderItemRepository;
import com.hardwarehub.order_service.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest
{
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ProductClient productClient;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderItemMapper orderItemMapper;

    @InjectMocks
    private OrderService orderService;

    @Test
    void create_whenNoActiveOrderExists_thenSaveNewActiveOrder()
    {
        // Given
        int userId = 1;

        when(orderRepository.existsByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(false);

        // When
        orderService.create(userId);

        // Then
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(captor.capture());
        OrderEntity order = captor.getValue();

        assertEquals(userId, order.getUserId());
        assertEquals(OrderStatus.ACTIVE, order.getStatus());
        assertEquals(BigDecimal.ZERO, order.getTotalPrice());
        assertTrue(order.getOrderItems().isEmpty());
    }

    @Test
    void create_whenActiveOrderAlreadyExists_thenDoNotSaveAnotherOrder()
    {
        // Given
        int userId = 1;

        when(orderRepository.existsByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(true);

        // When
        orderService.create(userId);

        // Then
        verify(orderRepository).existsByUserIdAndStatus(userId, OrderStatus.ACTIVE);
        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(orderMapper, productClient, orderItemRepository);
    }

    @Test
    void getActive_whenOrderExists_thenReturnOrder()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        order.setStatus(OrderStatus.ACTIVE);

        OrderResponseDTO orderDTO = OrderFactory.activeOrderDTO();

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));
        when(orderMapper.entityToDTO(order)).thenReturn(orderDTO);

        // When
        OrderResponseDTO result = orderService.getActive(userId);

        // Then
        assertEquals(orderDTO, result);

        verify(orderRepository).findByUserIdAndStatus(userId, OrderStatus.ACTIVE);
        verify(orderMapper).entityToDTO(order);
    }

    @Test
    void getPending_whenOrderExists_thenReturnOrder()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        order.setStatus(OrderStatus.PENDING);

        OrderResponseDTO orderDTO = OrderFactory.activeOrderDTO();

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING)).thenReturn(Optional.of(order));
        when(orderMapper.entityToDTO(order)).thenReturn(orderDTO);

        // When
        OrderResponseDTO result = orderService.getPending(userId);

        // Then
        assertEquals(orderDTO, result);

        verify(orderRepository).findByUserIdAndStatus(userId, OrderStatus.PENDING);
        verify(orderMapper).entityToDTO(order);
    }

    @Test
    void checkout_whenOrderDoesNotExist_thenThrowOrderNotFoundException()
    {
        // Given
        int userId = 1;

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.empty());

        // When
        assertThrows(OrderNotFoundException.class, () -> orderService.checkout(userId));

        // Then
        verify(orderRepository).findByUserIdAndStatus(userId, OrderStatus.ACTIVE);
        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(orderMapper, productClient, orderItemRepository);
    }

    @Test
    void complete_whenOrderDoesNotExist_thenThrowOrderNotFoundException()
    {
        // Given
        int userId = 1;

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING)).thenReturn(Optional.empty());

        // When
        assertThrows(OrderNotFoundException.class, () -> orderService.complete(userId));

        // Then
        verify(orderRepository).findByUserIdAndStatus(userId, OrderStatus.PENDING);
        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(orderMapper, productClient, orderItemRepository);
    }

    @Test
    void reopen_whenOrderDoesNotExist_thenThrowOrderNotFoundException()
    {
        // Given
        int userId = 1;

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING)).thenReturn(Optional.empty());

        // When
        assertThrows(OrderNotFoundException.class, () -> orderService.reopen(userId));

        // Then
        verify(orderRepository).findByUserIdAndStatus(userId, OrderStatus.PENDING);
        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(orderMapper, productClient, orderItemRepository);
    }

    @Test
    void refreshActiveOrder_whenOrderDoesNotExist_thenThrowOrderNotFoundException()
    {
        // Given
        int userId = 1;

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.empty());

        // When
        assertThrows(OrderNotFoundException.class, () -> orderService.refreshActiveOrder(userId));

        // Then
        verify(orderRepository).findByUserIdAndStatus(userId, OrderStatus.ACTIVE);
        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(orderMapper, productClient, orderItemRepository);
    }

    @Test
    void clearActiveOrder_whenOrderDoesNotExist_thenThrowOrderNotFoundException()
    {
        // Given
        int userId = 1;

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.empty());

        // When
        assertThrows(OrderNotFoundException.class, () -> orderService.clearActiveOrder(userId));

        // Then
        verify(orderRepository).findByUserIdAndStatus(userId, OrderStatus.ACTIVE);
        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(orderMapper, productClient, orderItemRepository);
    }

    @Test
    void getActive_whenOrderDoesNotExist_thenThrowOrderNotFoundException()
    {
        // Given
        int userId = 1;

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.empty());

        // When
        assertThrows(OrderNotFoundException.class, () -> orderService.getActive(userId));

        // Then
        verify(orderRepository).findByUserIdAndStatus(userId, OrderStatus.ACTIVE);
        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(orderMapper, productClient, orderItemRepository);
    }

    @Test
    void getPending_whenOrderDoesNotExist_thenThrowOrderNotFoundException()
    {
        // Given
        int userId = 1;

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING)).thenReturn(Optional.empty());

        // When
        assertThrows(OrderNotFoundException.class, () -> orderService.getPending(userId));

        // Then
        verify(orderRepository).findByUserIdAndStatus(userId, OrderStatus.PENDING);
        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(orderMapper, productClient, orderItemRepository);
    }

    @Test
    void getByIdAndUserId_whenOrderExists_thenReturnOrder()
    {
        // Given
        int id = 2;
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();

        OrderResponseDTO orderDTO = OrderFactory.activeOrderDTO();

        when(orderRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(order));
        when(orderMapper.entityToDTO(order)).thenReturn(orderDTO);

        // When
        OrderResponseDTO result = orderService.getById(id, userId);

        // Then
        assertEquals(orderDTO, result);

        verify(orderRepository).findByIdAndUserId(id, userId);
        verify(orderMapper).entityToDTO(order);
    }

    @Test
    void getByIdAndUserId_whenOrderDoesNotExist_thenThrowOrderNotFoundException()
    {
        // Given
        int id = 2;
        int userId = 1;

        when(orderRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        // When
        assertThrows(OrderNotFoundException.class, () -> orderService.getById(id, userId));

        // Then
        verify(orderRepository).findByIdAndUserId(id, userId);
        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(orderMapper);
    }

    @Test
    void getById_whenOrderExists_thenReturnOrder()
    {
        // Given
        int id = 2;

        OrderEntity order = OrderFactory.activeOrder();

        OrderResponseDTO orderDTO = OrderFactory.activeOrderDTO();

        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        when(orderMapper.entityToDTO(order)).thenReturn(orderDTO);

        // When
        OrderResponseDTO result = orderService.getById(id);

        // Then
        assertEquals(orderDTO, result);

        verify(orderRepository).findById(id);
        verify(orderMapper).entityToDTO(order);
    }

    @Test
    void getById_whenOrderDoesNotExist_thenThrowOrderNotFoundException()
    {
        // Given
        int id = 2;

        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        // When
        assertThrows(OrderNotFoundException.class, () -> orderService.getById(id));

        // Then
        verify(orderRepository).findById(id);
        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(orderMapper);
    }

    @Test
    void getAll_whenOrdersExist_thenReturnOrderPage()
    {
        // Given
        int userId = 1;
        Pageable pageable = PageRequest.of(0, 10);

        OrderEntity order = OrderFactory.completeOrder();

        OrderResponseDTO orderDTO = OrderFactory.completeOrderDTO();

        Page<OrderEntity> orders = new PageImpl<>(List.of(order), pageable, 1);

        when(orderRepository.findAllByUserIdAndStatusNotOrderByCreatedAtDesc(userId, OrderStatus.ACTIVE, pageable)).thenReturn(orders);
        when(orderMapper.entityToDTO(order)).thenReturn(orderDTO);

        // When
        Page<OrderResponseDTO> result = orderService.getAll(userId, pageable);

        // Then
        assertEquals(List.of(orderDTO), result.getContent());
        assertEquals(pageable, result.getPageable());
        assertEquals(1, result.getTotalElements());

        verify(orderRepository).findAllByUserIdAndStatusNotOrderByCreatedAtDesc(userId, OrderStatus.ACTIVE, pageable);
    }

    @Test
    void getAll_whenNoOrdersExist_thenReturnEmptyPage()
    {
        // Given
        int userId = 1;
        Pageable pageable = PageRequest.of(0, 10);

        when(orderRepository.findAllByUserIdAndStatusNotOrderByCreatedAtDesc(userId, OrderStatus.ACTIVE, pageable)).thenReturn(Page.empty(pageable));

        // When
        Page<OrderResponseDTO> result = orderService.getAll(userId, pageable);

        // Then
        assertTrue(result.isEmpty());

        verify(orderRepository).findAllByUserIdAndStatusNotOrderByCreatedAtDesc(userId, OrderStatus.ACTIVE, pageable);
        verifyNoInteractions(orderMapper);
    }

    @Test
    void getAllByUserId_whenOrdersExist_thenReturnOrderPage()
    {
        // Given
        int userId = 1;
        Pageable pageable = PageRequest.of(0, 10);

        OrderEntity order = OrderFactory.completeOrder();

        OrderResponseDTO orderDTO = OrderFactory.completeOrderDTO();

        Page<OrderEntity> orders = new PageImpl<>(List.of(order), pageable, 1);

        when(orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(orders);
        when(orderMapper.entityToDTO(order)).thenReturn(orderDTO);

        // When
        Page<OrderResponseDTO> result = orderService.getAllByUserId(userId, pageable);

        // Then
        assertEquals(List.of(orderDTO), result.getContent());
        assertEquals(pageable, result.getPageable());
        assertEquals(1, result.getTotalElements());

        verify(orderRepository).findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Test
    void getAllByUserId_whenNoOrdersExist_thenReturnEmptyPage()
    {
        // Given
        int userId = 1;
        Pageable pageable = PageRequest.of(0, 10);

        when(orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(Page.empty(pageable));

        // When
        Page<OrderResponseDTO> result = orderService.getAllByUserId(userId, pageable);

        // Then
        assertTrue(result.isEmpty());

        verify(orderRepository).findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        verifyNoInteractions(orderMapper);
    }

    @Test
    void getAllByStatus_whenOrdersExist_thenReturnOrderPage()
    {
        // Given
        int userId = 1;
        Pageable pageable = PageRequest.of(0, 10);

        OrderEntity order = OrderFactory.completeOrder();

        OrderResponseDTO orderDTO = OrderFactory.completeOrderDTO();

        Page<OrderEntity> orders = new PageImpl<>(List.of(order), pageable, 1);

        when(orderRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, OrderStatus.COMPLETED, pageable)).thenReturn(orders);
        when(orderMapper.entityToDTO(order)).thenReturn(orderDTO);

        // When
        Page<OrderResponseDTO> result = orderService.getAllByStatus(userId, OrderStatus.COMPLETED, pageable);

        // Then
        assertEquals(List.of(orderDTO), result.getContent());
        assertEquals(pageable, result.getPageable());
        assertEquals(1, result.getTotalElements());

        verify(orderRepository).findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, OrderStatus.COMPLETED, pageable);
    }

    @Test
    void getAllByStatus_whenNoOrdersExist_thenReturnEmptyPage()
    {
        // Given
        int userId = 1;
        Pageable pageable = PageRequest.of(0, 10);

        when(orderRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, OrderStatus.COMPLETED, pageable)).thenReturn(Page.empty(pageable));

        // When
        Page<OrderResponseDTO> result = orderService.getAllByStatus(userId, OrderStatus.COMPLETED, pageable);

        // Then
        assertTrue(result.isEmpty());

        verify(orderRepository).findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, OrderStatus.COMPLETED, pageable);
        verifyNoInteractions(orderMapper);
    }

    @Test
    void checkout_whenOrderHasItems_thenRefreshPricesReserveStockAndReturnPendingOrder()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );
        OrderResponseDTO orderDTO = OrderFactory.activeOrderDTO();

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));
        when(productClient.getById(item.getProductId())).thenReturn(product);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.entityToDTO(order)).thenReturn(orderDTO);

        // When
        OrderResponseDTO result = orderService.checkout(userId);

        // Then
        assertEquals(orderDTO, result);
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(BigDecimal.valueOf(3000), item.getPrice());
        assertEquals(BigDecimal.valueOf(3000), order.getTotalPrice());

        verify(productClient).decreaseStockById(item.getProductId(), 2);
        verify(orderRepository).save(order);
        verify(orderMapper).entityToDTO(order);
    }

    @Test
    void checkout_whenOrderIsEmpty_thenThrowEmptyOrderException()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        order.getOrderItems().clear();

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));

        // When
        assertThrows(EmptyOrderException.class, () -> orderService.checkout(userId));

        // Then
        assertTrue(order.getOrderItems().isEmpty());
        assertEquals(BigDecimal.ZERO, order.getTotalPrice());
        assertEquals(OrderStatus.ACTIVE, order.getStatus());

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productClient, never()).decreaseStockById(anyInt(), anyInt());
        verifyNoInteractions(orderMapper);
    }

    @Test
    void checkout_whenAllProductsAreRemoved_thenThrowEmptyOrderException()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();

        when(productClient.getById(1)).thenThrow(new ProductNotFoundException("Product not found."));
        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));

        // When
        assertThrows(EmptyOrderException.class, () -> orderService.checkout(userId));

        // Then
        assertTrue(order.getOrderItems().isEmpty());
        assertEquals(BigDecimal.ZERO, order.getTotalPrice());
        assertEquals(OrderStatus.ACTIVE, order.getStatus());

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productClient, never()).decreaseStockById(anyInt(), anyInt());
        verifyNoInteractions(orderMapper);
    }

    @Test
    void checkout_whenStockReservationFails_thenPropagateExceptionWithoutSaving()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));
        when(productClient.getById(1)).thenReturn(product);
        doThrow(new ProductOutOfStockException("Insufficient stock.")).when(productClient).decreaseStockById(1, 2);

        // When
        assertThrows(ProductOutOfStockException.class, () -> orderService.checkout(userId));

        // Then
        assertEquals(OrderStatus.ACTIVE, order.getStatus());

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verifyNoInteractions(orderMapper);
    }

    @Test
    void complete_whenActiveOrderExists_thenCompleteAndCreateNewActiveOrder()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING)).thenReturn(Optional.of(order));
        when(orderRepository.existsByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(false);

        // When
        orderService.complete(userId);

        // Then
        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(captor.capture());
        OrderEntity newOrder = captor.getValue();

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(userId, newOrder.getUserId());
        assertEquals(OrderStatus.ACTIVE, newOrder.getStatus());
        assertEquals(BigDecimal.ZERO, newOrder.getTotalPrice());
        assertTrue(newOrder.getOrderItems().isEmpty());
    }

    @Test
    void reopen_whenPendingOrderExists_thenReleaseStockAndReturnActiveOrder()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        order.setStatus(OrderStatus.PENDING);

        OrderResponseDTO orderDTO = OrderFactory.activeOrderDTO();

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.entityToDTO(order)).thenReturn(orderDTO);

        // When
        OrderResponseDTO result = orderService.reopen(userId);

        // Then
        assertEquals(orderDTO, result);
        assertEquals(OrderStatus.ACTIVE, order.getStatus());

        verify(productClient).increaseStockById(1, 2);
        verify(orderRepository).save(order);
    }

    @Test
    void reopen_whenStockReleaseFails_thenPropagateExceptionWithoutSaving()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.PENDING)).thenReturn(Optional.of(order));
        doThrow(new ProductNotFoundException("Product not found.")).when(productClient).increaseStockById(1, 2);

        // When
        assertThrows(ProductNotFoundException.class, () -> orderService.reopen(userId));

        // Then
        assertEquals(OrderStatus.PENDING, order.getStatus());

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verifyNoInteractions(orderMapper);
    }

    @Test
    void refreshActiveOrder_whenPricesChange_thenRecalculateItemsAndTotal()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));
        when(productClient.getById(1)).thenReturn(product);

        // When
        orderService.refreshActiveOrder(userId);

        // Then
        assertEquals(2, item.getQuantity());
        assertEquals(BigDecimal.valueOf(3000), item.getPrice());
        assertEquals(item.getPrice(), order.getTotalPrice());

        verify(productClient).getById(1);
    }

    @Test
    void refreshActiveOrder_whenQuantityExceedsStock_thenReduceQuantityAndRecalculatePrices()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                1,
                null,
                null
        );

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));
        when(productClient.getById(1)).thenReturn(product);

        // When
        orderService.refreshActiveOrder(userId);

        // Then
        assertEquals(1, item.getQuantity());
        assertEquals(BigDecimal.valueOf(1500), item.getPrice());
        assertEquals(item.getPrice(), order.getTotalPrice());

        verify(productClient).getById(1);
    }

    @Test
    void refreshActiveOrder_whenProductDoesNotExist_thenRemoveItemAndKeepOtherItems()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);
        OrderItemEntity otherItem = OrderFactory.graphicCardOne();
        otherItem.setProductId(3);
        order.addItem(otherItem);

        ProductResponseDTO product = new ProductResponseDTO(
                3,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));
        when(productClient.getById(1)).thenThrow(new ProductNotFoundException("Product unavailable."));
        when(productClient.getById(3)).thenReturn(product);

        // When
        orderService.refreshActiveOrder(userId);

        // Then
        assertEquals(List.of(otherItem), order.getOrderItems());
        assertNull(item.getOrder());
        assertEquals(BigDecimal.valueOf(3000), order.getTotalPrice());
    }

    @Test
    void refreshActiveOrder_whenProductHasNoStock_thenRemoveItemAndKeepOtherItems()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);
        OrderItemEntity otherItem = OrderFactory.graphicCardOne();
        otherItem.setProductId(3);
        order.addItem(otherItem);

        ProductResponseDTO product = new ProductResponseDTO(
                3,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));
        when(productClient.getById(1)).thenThrow(new ProductOutOfStockException("Product unavailable."));
        when(productClient.getById(3)).thenReturn(product);

        // When
        orderService.refreshActiveOrder(userId);

        // Then
        assertEquals(List.of(otherItem), order.getOrderItems());
        assertNull(item.getOrder());
        assertEquals(BigDecimal.valueOf(3000), order.getTotalPrice());
    }

    @Test
    void refreshActiveOrder_whenOrderIsEmpty_thenSetTotalToZero()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        order.getOrderItems().clear();

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));

        // When
        orderService.refreshActiveOrder(userId);

        // Then
        assertEquals(BigDecimal.ZERO, order.getTotalPrice());

        verifyNoInteractions(productClient);
    }

    @Test
    void clearActiveOrder_whenOrderHasMultipleItems_thenLeaveEmptyOrderWithZeroTotal()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity firstItem = order.getOrderItems().get(0);
        OrderItemEntity secondItem = OrderFactory.graphicCardOne();
        order.addItem(secondItem);

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));

        // When
        orderService.clearActiveOrder(userId);

        // Then
        assertTrue(order.getOrderItems().isEmpty());
        assertEquals(BigDecimal.ZERO, order.getTotalPrice());
        assertNull(firstItem.getOrder());
        assertNull(secondItem.getOrder());

        verifyNoInteractions(productClient, orderItemRepository);
    }

    @Test
    void clearActiveOrder_whenOrderIsAlreadyEmpty_thenLeaveEmptyOrderWithZeroTotal()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        order.getOrderItems().clear();

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));

        // When
        orderService.clearActiveOrder(userId);

        // Then
        assertTrue(order.getOrderItems().isEmpty());
        assertEquals(BigDecimal.ZERO, order.getTotalPrice());

        verifyNoInteractions(productClient, orderItemRepository);
    }

    @Test
    void addItem_whenProductIsNew_thenAddItemAndRecalculateTotal()
    {
        // Given
        int userId = 1;
        int productId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        order.getOrderItems().clear();

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));
        when(productClient.getById(productId)).thenReturn(product);
        when(orderItemRepository.findByOrderIdAndProductId(order.getId(), productId)).thenReturn(Optional.empty());

        // When
        orderService.addItem(userId, productId);

        // Then
        OrderItemEntity item = order.getOrderItems().get(0);

        assertEquals(1, order.getOrderItems().size());
        assertEquals(1, item.getQuantity());
        assertEquals(BigDecimal.valueOf(1500), item.getPrice());
        assertEquals(item.getPrice(), order.getTotalPrice());
        assertSame(order, item.getOrder());
        assertEquals(productId, item.getProductId());
        assertEquals(product.name(), item.getProductName());

        verify(orderItemRepository).save(item);
    }

    @Test
    void addItem_whenProductAlreadyExists_thenIncrementQuantityAndRecalculateTotal()
    {
        // Given
        int userId = 1;
        int productId = 1;

        OrderEntity order = OrderFactory.activeOrder();

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        OrderItemEntity item = order.getOrderItems().get(0);

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));
        when(productClient.getById(productId)).thenReturn(product);
        when(orderItemRepository.findByOrderIdAndProductId(order.getId(), productId)).thenReturn(Optional.of(item));

        // When
        orderService.addItem(userId, productId);

        // Then
        assertEquals(1, order.getOrderItems().size());
        assertEquals(3, item.getQuantity());
        assertEquals(BigDecimal.valueOf(4500), item.getPrice());
        assertEquals(item.getPrice(), order.getTotalPrice());
        assertSame(order, item.getOrder());
        assertEquals(productId, item.getProductId());
        assertEquals(product.name(), item.getProductName());

        verify(orderItemRepository).save(item);
    }

    @Test
    void addItem_whenIncrementExceedsStock_thenThrowProductOutOfStockException()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                2,
                null,
                null
        );

        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.of(order));
        when(productClient.getById(1)).thenReturn(product);
        when(orderItemRepository.findByOrderIdAndProductId(order.getId(), 1)).thenReturn(Optional.of(item));

        // When
        assertThrows(ProductOutOfStockException.class, () -> orderService.addItem(userId, 1));

        // Then
        assertEquals(2, item.getQuantity());
        assertEquals(BigDecimal.valueOf(2000), item.getPrice());
        assertEquals(BigDecimal.valueOf(2000), order.getTotalPrice());

        verify(orderItemRepository, never()).save(any(OrderItemEntity.class));
    }

    @Test
    void addItem_whenActiveOrderDoesNotExist_thenThrowOrderNotFoundException()
    {
        // Given
        int userId = 1;

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        when(productClient.getById(1)).thenReturn(product);
        when(orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE)).thenReturn(Optional.empty());

        // When
        assertThrows(OrderNotFoundException.class, () -> orderService.addItem(userId, 1));

        // Then
        verifyNoInteractions(orderItemRepository);
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void addItem_whenProductDoesNotExist_thenPropagateException()
    {
        // Given
        int userId = 1;

        when(productClient.getById(1)).thenThrow(new ProductNotFoundException("Product unavailable."));

        // When
        assertThrows(ProductNotFoundException.class, () -> orderService.addItem(userId, 1));

        // Then
        verifyNoInteractions(orderRepository, orderItemRepository);
    }

    @Test
    void addItem_whenProductHasNoStock_thenPropagateException()
    {
        // Given
        int userId = 1;

        when(productClient.getById(1)).thenThrow(new ProductOutOfStockException("Product unavailable."));

        // When
        assertThrows(ProductOutOfStockException.class, () -> orderService.addItem(userId, 1));

        // Then
        verifyNoInteractions(orderRepository, orderItemRepository);
    }

    @Test
    void updateItemQuantity_whenProductDoesNotExist_thenPropagateException()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);

        when(orderItemRepository.findActiveItemByIdAndUserId(item.getId(), userId)).thenReturn(Optional.of(item));
        when(productClient.getById(1)).thenThrow(new ProductNotFoundException("Product unavailable."));

        // When
        assertThrows(ProductNotFoundException.class, () -> orderService.updateItemQuantity(item.getId(), userId, 3));

        // Then
        assertEquals(2, item.getQuantity());
        assertEquals(BigDecimal.valueOf(2000), item.getPrice());
        assertEquals(BigDecimal.valueOf(2000), order.getTotalPrice());
    }

    @Test
    void updateItemQuantity_whenProductHasNoStock_thenPropagateException()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);

        when(orderItemRepository.findActiveItemByIdAndUserId(item.getId(), userId)).thenReturn(Optional.of(item));
        when(productClient.getById(1)).thenThrow(new ProductOutOfStockException("Product unavailable."));

        // When
        assertThrows(ProductOutOfStockException.class, () -> orderService.updateItemQuantity(item.getId(), userId, 3));

        // Then
        assertEquals(2, item.getQuantity());
        assertEquals(BigDecimal.valueOf(2000), item.getPrice());
        assertEquals(BigDecimal.valueOf(2000), order.getTotalPrice());
    }

    @Test
    void updateItemQuantity_whenQuantityIsAvailable_thenUpdateQuantityPriceAndTotal()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);
        OrderItemEntity otherItem = OrderFactory.graphicCardOne();
        otherItem.setProductId(3);
        order.addItem(otherItem);

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        when(orderItemRepository.findActiveItemByIdAndUserId(item.getId(), userId)).thenReturn(Optional.of(item));
        when(productClient.getById(1)).thenReturn(product);

        // When
        orderService.updateItemQuantity(item.getId(), userId, 3);

        // Then
        assertEquals(3, item.getQuantity());
        assertEquals(BigDecimal.valueOf(4500), item.getPrice());
        assertEquals(BigDecimal.valueOf(6500), order.getTotalPrice());
        assertSame(order, item.getOrder());
    }

    @Test
    void updateItemQuantity_whenQuantityIsOne_thenUpdateQuantityPriceAndTotal()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);
        OrderItemEntity otherItem = OrderFactory.graphicCardOne();
        otherItem.setProductId(3);
        order.addItem(otherItem);

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        when(orderItemRepository.findActiveItemByIdAndUserId(item.getId(), userId)).thenReturn(Optional.of(item));
        when(productClient.getById(1)).thenReturn(product);

        // When
        orderService.updateItemQuantity(item.getId(), userId, 1);

        // Then
        assertEquals(1, item.getQuantity());
        assertEquals(BigDecimal.valueOf(1500), item.getPrice());
        assertEquals(BigDecimal.valueOf(3500), order.getTotalPrice());
        assertSame(order, item.getOrder());
    }

    @Test
    void updateItemQuantity_whenQuantityEqualsStock_thenUpdateQuantityPriceAndTotal()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);
        OrderItemEntity otherItem = OrderFactory.graphicCardOne();
        otherItem.setProductId(3);
        order.addItem(otherItem);

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        when(orderItemRepository.findActiveItemByIdAndUserId(item.getId(), userId)).thenReturn(Optional.of(item));
        when(productClient.getById(1)).thenReturn(product);

        // When
        orderService.updateItemQuantity(item.getId(), userId, 10);

        // Then
        assertEquals(10, item.getQuantity());
        assertEquals(BigDecimal.valueOf(15000), item.getPrice());
        assertEquals(BigDecimal.valueOf(17000), order.getTotalPrice());
        assertSame(order, item.getOrder());
    }

    @Test
    void updateItemQuantity_whenQuantityExceedsStock_thenThrowProductOutOfStockException()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);
        OrderItemEntity otherItem = OrderFactory.graphicCardOne();
        otherItem.setProductId(3);
        order.addItem(otherItem);

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        when(orderItemRepository.findActiveItemByIdAndUserId(item.getId(), userId)).thenReturn(Optional.of(item));
        when(productClient.getById(1)).thenReturn(product);

        // When
        assertThrows(ProductOutOfStockException.class, () -> orderService.updateItemQuantity(item.getId(), userId, 11));

        // Then
        assertEquals(2, item.getQuantity());
        assertEquals(BigDecimal.valueOf(2000), item.getPrice());
    }

    @Test
    void updateItemQuantity_whenQuantityIsZero_thenThrowInvalidQuantityException()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);
        OrderItemEntity otherItem = OrderFactory.graphicCardOne();
        otherItem.setProductId(3);
        order.addItem(otherItem);

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        when(orderItemRepository.findActiveItemByIdAndUserId(item.getId(), userId)).thenReturn(Optional.of(item));
        when(productClient.getById(1)).thenReturn(product);

        // When
        InvalidQuantityException exception = assertThrows(InvalidQuantityException.class,
                () -> orderService.updateItemQuantity(item.getId(), userId, 0));

        // Then
        assertEquals("Quantity must be greater than zero.", exception.getMessage());
        assertEquals(List.of(item, otherItem), order.getOrderItems());
        assertSame(order, item.getOrder());
        assertEquals(2, item.getQuantity());
        assertEquals(BigDecimal.valueOf(2000), item.getPrice());
        assertEquals(BigDecimal.valueOf(2000), order.getTotalPrice());

        verify(orderItemRepository).findActiveItemByIdAndUserId(item.getId(), userId);
        verify(productClient).getById(1);
        verifyNoMoreInteractions(orderItemRepository, productClient);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void updateItemQuantity_whenQuantityIsNegative_thenThrowInvalidQuantityException()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);
        OrderItemEntity otherItem = OrderFactory.graphicCardOne();
        otherItem.setProductId(3);
        order.addItem(otherItem);

        ProductResponseDTO product = new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GPU-001",
                "NVIDIA GTX 1080-TI",
                "Graphics card",
                BigDecimal.valueOf(1500),
                10,
                null,
                null
        );

        when(orderItemRepository.findActiveItemByIdAndUserId(item.getId(), userId)).thenReturn(Optional.of(item));
        when(productClient.getById(1)).thenReturn(product);

        // When
        InvalidQuantityException exception = assertThrows(InvalidQuantityException.class,
                () -> orderService.updateItemQuantity(item.getId(), userId, -1));

        // Then
        assertEquals("Quantity must be greater than zero.", exception.getMessage());
        assertEquals(List.of(item, otherItem), order.getOrderItems());
        assertSame(order, item.getOrder());
        assertEquals(2, item.getQuantity());
        assertEquals(BigDecimal.valueOf(2000), item.getPrice());
        assertEquals(BigDecimal.valueOf(2000), order.getTotalPrice());

        verify(orderItemRepository).findActiveItemByIdAndUserId(item.getId(), userId);
        verify(productClient).getById(1);
        verifyNoMoreInteractions(orderItemRepository, productClient);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void updateItemQuantity_whenItemDoesNotExist_thenThrowItemNotFoundException()
    {
        // Given
        int userId = 1;
        int itemId = 999;

        when(orderItemRepository.findActiveItemByIdAndUserId(itemId, userId)).thenReturn(Optional.empty());

        // When
        assertThrows(ItemNotFoundException.class, () -> orderService.updateItemQuantity(itemId, userId, 3));

        // Then
        verify(orderItemRepository).findActiveItemByIdAndUserId(itemId, userId);
        verifyNoMoreInteractions(orderItemRepository);
        verifyNoInteractions(productClient, orderRepository);
    }

    @Test
    void deleteItemById_whenItemDoesNotExist_thenThrowItemNotFoundException()
    {
        // Given
        int userId = 1;
        int itemId = 999;

        when(orderItemRepository.findActiveItemByIdAndUserId(itemId, userId)).thenReturn(Optional.empty());

        // When
        assertThrows(ItemNotFoundException.class, () -> orderService.deleteItemById(itemId, userId));

        // Then
        verify(orderItemRepository).findActiveItemByIdAndUserId(itemId, userId);
        verifyNoMoreInteractions(orderItemRepository);
        verifyNoInteractions(productClient, orderRepository);
    }

    @Test
    void deleteItemById_whenOtherItemsRemain_thenRemoveItemAndRecalculateTotal()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);
        OrderItemEntity otherItem = OrderFactory.graphicCardOne();
        order.addItem(otherItem);

        when(orderItemRepository.findActiveItemByIdAndUserId(item.getId(), userId)).thenReturn(Optional.of(item));

        // When
        orderService.deleteItemById(item.getId(), userId);

        // Then
        assertNull(item.getOrder());
        assertEquals(List.of(otherItem), order.getOrderItems());
        assertEquals(otherItem.getPrice(), order.getTotalPrice());

        verifyNoInteractions(productClient);
    }

    @Test
    void deleteItemById_whenItemIsLast_thenLeaveEmptyOrderWithZeroTotal()
    {
        // Given
        int userId = 1;

        OrderEntity order = OrderFactory.activeOrder();
        OrderItemEntity item = order.getOrderItems().get(0);

        when(orderItemRepository.findActiveItemByIdAndUserId(item.getId(), userId)).thenReturn(Optional.of(item));

        // When
        orderService.deleteItemById(item.getId(), userId);

        // Then
        assertNull(item.getOrder());
        assertTrue(order.getOrderItems().isEmpty());
        assertEquals(BigDecimal.ZERO, order.getTotalPrice());

        verifyNoInteractions(productClient);
    }
}
