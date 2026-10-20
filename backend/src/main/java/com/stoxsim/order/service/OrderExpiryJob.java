package com.stoxsim.order.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.stoxsim.order.repository.PaperOrderRepository;

@Component
public class OrderExpiryJob {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(OrderExpiryJob.class);
    private final PaperOrderRepository orders;
    private final OrderApplicationService orderService;

    public OrderExpiryJob(PaperOrderRepository orders, OrderApplicationService orderService) {
        this.orders = orders;
        this.orderService = orderService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    public void expireDayOrders() {
        // The service checks each exchange's local date and close under the order lock.
        orders.findOpenWithInstruments().forEach(order -> {
            try { orderService.expire(order.getId()); }
            catch (RuntimeException failure) { LOGGER.warn("Could not expire DAY order {}", order.getId(), failure); }
        });
    }
}
