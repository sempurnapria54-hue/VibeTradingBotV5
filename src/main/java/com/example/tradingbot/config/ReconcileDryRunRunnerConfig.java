package com.example.tradingbot.config;

import com.example.tradingbot.domain.service.reconcile.SynchronizeExecutionEnvironmentService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReconcileDryRunRunnerConfig {

    @Bean
    @ConditionalOnProperty(prefix = "reconcile", name = "dry-run-on-startup", havingValue = "true")
    public CommandLineRunner reconcileDryRunRunner(SynchronizeExecutionEnvironmentService service) {
        return args -> service.runDryRun();
    }
}
