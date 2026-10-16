package com.stoxsim.finwiz.service;

import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;

@Service
public class FinwizAccess {
    private final UserSubscriptionRepository subscriptions;
    private final Clock clock;

    public FinwizAccess(UserSubscriptionRepository subscriptions, Clock clock) {
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    // Resolve lazy user/test eligibility inside a short transaction, never around a provider call.
    @Transactional(readOnly = true)
    public FinwizDepth depth(UUID userId) {
        var subscription = subscriptions.findByUserId(userId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription state not found"));
        return FinwizDepth.from(subscription.effectiveFinwizPlan(clock.instant()));
    }
}
