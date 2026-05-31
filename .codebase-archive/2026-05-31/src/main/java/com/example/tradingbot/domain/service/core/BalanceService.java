package com.example.tradingbot.domain.service.core;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.service.deal.command.refresh.RefreshBalanceExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final RefreshBalanceExecutor refreshBalanceExecutor;

    public void refreshBalance(Exchange exchange) {
        refreshBalanceExecutor.execute(exchange);
    }
}
