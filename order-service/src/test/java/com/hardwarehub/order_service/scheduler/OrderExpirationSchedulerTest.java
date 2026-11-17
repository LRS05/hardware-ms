package com.hardwarehub.order_service.scheduler;

import com.hardwarehub.order_service.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderExpirationSchedulerTest
{
    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderExpirationScheduler orderExpirationScheduler;

    @Test
    void expirePendingOrders_whenExpiredOrdersExist_thenExpireEachOrder()
    {
        // Given
        List<Integer> orderIds = List.of(1, 2, 3);

        when(orderService.findExpiredOrderIds()).thenReturn(orderIds);

        // When
        orderExpirationScheduler.expirePendingOrders();

        // Then
        verify(orderService).findExpiredOrderIds();
        verify(orderService).expire(1);
        verify(orderService).expire(2);
        verify(orderService).expire(3);
        verifyNoMoreInteractions(orderService);
    }

    @Test
    void expirePendingOrders_whenOneOrderFails_thenContinueWithRemainingOrders()
    {
        // Given
        List<Integer> orderIds = List.of(1, 2, 3);

        when(orderService.findExpiredOrderIds()).thenReturn(orderIds);
        doThrow(new RuntimeException("Failed to cancel checkout")).when(orderService).expire(1);

        // When
        orderExpirationScheduler.expirePendingOrders();

        // Then
        verify(orderService).findExpiredOrderIds();
        verify(orderService).expire(1);
        verify(orderService).expire(2);
        verify(orderService).expire(3);
        verifyNoMoreInteractions(orderService);
    }
}
