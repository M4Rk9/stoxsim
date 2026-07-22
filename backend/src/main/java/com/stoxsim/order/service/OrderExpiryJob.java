package com.stoxsim.order.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stoxsim.order.repository.PaperOrderRepository;

@Component
public class OrderExpiryJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderExpiryJob.class);
    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    private final PaperOrderRepository orders;
    private final OrderApplicationService orderService;

    public OrderExpiryJob(
        PaperOrderRepository orders,
        OrderApplicationService orderService
    ) {
        this.orders = orders;
        this.orderService = orderService;
    }

    @Scheduled(cron = "0 31 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void expireIndiaDayOrders() {
        LocalDate tradeDate = LocalDate.now(INDIA_ZONE);
        var due = orders.findOpenIdsDueBy(tradeDate);
        due.forEach(orderService::expire);
        if (!due.isEmpty()) {
            LOGGER.info("Expired {} India DAY orders", due.size());
        }
    }
}
