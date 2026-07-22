package com.stoxsim.order.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.service.OrderApplicationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderApplicationService orders;

    public OrderController(OrderApplicationService orders) {
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PlaceOrderRequest request
    ) {
        return orders.place(userId(jwt), idempotencyKey, request);
    }

    @GetMapping
    public List<OrderResponse> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "INDIA") MarketRegion marketRegion
    ) {
        return orders.list(userId(jwt), marketRegion);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID orderId
    ) {
        return orders.get(userId(jwt), orderId);
    }

    @PutMapping("/{orderId}")
    public OrderResponse modify(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID orderId,
        @Valid @RequestBody ModifyOrderRequest request
    ) {
        return orders.modify(userId(jwt), orderId, request);
    }

    @DeleteMapping("/{orderId}")
    public OrderResponse cancel(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID orderId
    ) {
        return orders.cancel(userId(jwt), orderId);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
