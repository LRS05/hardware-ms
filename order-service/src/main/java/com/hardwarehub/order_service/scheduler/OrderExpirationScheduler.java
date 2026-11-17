package com.hardwarehub.order_service.scheduler;

import com.hardwarehub.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler
{
    private final OrderService orderService;

    @Scheduled(
            fixedDelayString = "${orders.expiration-check-delay-ms}",
            initialDelayString = "${orders.expiration-check-delay-ms}"
    )
    public void expirePendingOrders()
    {
        for (Integer orderId : orderService.findExpiredOrderIds())
        {
            try
            {
                orderService.expire(orderId);
            }
            catch (RuntimeException e)
            {
                log.error("Failed to expire order {}", orderId, e);
            }
        }
    }
}
