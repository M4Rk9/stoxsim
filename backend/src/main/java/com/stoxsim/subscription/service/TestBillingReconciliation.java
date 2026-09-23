package com.stoxsim.subscription.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(name="stoxsim.billing.test.reconciliation-enabled",havingValue="true",matchIfMissing=true)
public class TestBillingReconciliation {
    private final RazorpayTestBillingService billing;
    public TestBillingReconciliation(RazorpayTestBillingService billing) { this.billing=billing; }
    @Bean(name="testBillingScheduler", defaultCandidate=false)
    public ThreadPoolTaskScheduler scheduler() {
        var scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("test-billing-");return scheduler;
    }
    @Scheduled(initialDelay=60000,fixedDelay=60000,scheduler="testBillingScheduler")
    public void reconcile() { billing.reconcileBenefits(); }
}
