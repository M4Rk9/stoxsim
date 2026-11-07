package com.stoxsim.billing;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
@Configuration
@ConditionalOnProperty(name="stoxsim.billing.reconciliation-enabled",havingValue="true",matchIfMissing=true)
public class BillingReconciliation {
    private final LiveBillingService billing;public BillingReconciliation(LiveBillingService billing){this.billing=billing;}
    @Bean(name="liveBillingScheduler",defaultCandidate=false)
    public ThreadPoolTaskScheduler scheduler(){var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("live-billing-");return scheduler;}
    @Scheduled(initialDelay=60000,fixedDelay=60000,scheduler="liveBillingScheduler")public void reconcile(){billing.reconcileDue();}
}
