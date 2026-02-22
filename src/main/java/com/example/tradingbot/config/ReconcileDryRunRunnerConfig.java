package com.example.tradingbot.config;

import com.example.tradingbot.domain.service.reconcile.ReconcileService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.example.tradingbot.util.Constant.ExchangeNames.EXCHANGE_NAME_OKX;

@Configuration
public class ReconcileDryRunRunnerConfig {

    @Bean
    @ConditionalOnProperty(prefix = "reconcile", name = "dry-run-on-startup", havingValue = "true")
    public CommandLineRunner reconcileDryRunRunner(ReconcileService service) {
        return args -> service.runDryRun(EXCHANGE_NAME_OKX);
    }
}
