package com.stoxsim.portfolio.service;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PortfolioHistorySchedule {
    private final JdbcTemplate db;
    private final PortfolioHistoryCollector collector;
    @Value("${stoxsim.portfolio.history.enabled:true}") private boolean enabled;
    public PortfolioHistorySchedule(JdbcTemplate db,PortfolioHistoryCollector collector) { this.db=db;this.collector=collector; }
    @Bean(name="portfolioHistoryScheduler",defaultCandidate=false)
    public ThreadPoolTaskScheduler scheduler() {
        var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("portfolio-history-");return scheduler;
    }
    @Scheduled(cron="0 40 23 * * *",zone="UTC",scheduler="portfolioHistoryScheduler")
    public void collect() {
        UUID cursor=new UUID(0,0);
        while(enabled) {
            var ids=db.query("SELECT id FROM virtual_account WHERE id>? ORDER BY id LIMIT 100",(rs,n)->rs.getObject(1,UUID.class),cursor);
            if(ids.isEmpty()) break;
            for(var id:ids) try { collector.capture(id); } catch(RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Portfolio history observation failed for account {}",id);
            }
            cursor=ids.getLast();
        }
        db.update("DELETE FROM portfolio_history WHERE observed_day < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 364");
    }
}
