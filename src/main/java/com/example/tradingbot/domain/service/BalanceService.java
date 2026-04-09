package com.example.tradingbot.domain.service;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.balance.Balance;
import com.example.tradingbot.domain.model.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.domain.model.balance.external_snapshot.BalanceExternalSnapshot;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.mapping.BalanceMapper;
import com.example.tradingbot.persistence.service.BalanceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final ClientManager clientManager;
    private final BalanceDataService balanceDataService;
    private final BalanceMapper balanceMapper;

    @Transactional
    public void refreshBalance(Exchange exchange) {
        BalanceContainerExternalSnapshot container = clientManager.getClientService(exchange.getName())
                                                                  .getBalanceContainer(exchange);
        if (container == null) {
            return;
        }
        List<BalanceExternalSnapshot> snapshots = container.getBalances();
        if (snapshots == null) {
            return;
        }

        for (BalanceExternalSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.getCurrency() == null) {
                continue;
            }
            Balance balance = resolveOrCreate(exchange.getId(), snapshot.getCurrency());
            balanceMapper.updateDomainFromSnapshot(snapshot, balance);
            applyDefaults(balance);
            balanceDataService.save(balance);
        }
    }

    private Balance resolveOrCreate(Long exchangeId, String currency) {
        Optional<Balance> balanceOptional = balanceDataService.findByExchangeIdAndCurrency(exchangeId, currency);
        if (balanceOptional.isPresent()) {
            return balanceOptional.get();
        }

        Balance created = new Balance();
        created.setExchangeId(exchangeId);
        created.setCurrency(currency);
        created.setAvailable(BigDecimal.ZERO);
        created.setFrozen(BigDecimal.ZERO);
        created.setTotal(BigDecimal.ZERO);
        return created;
    }

    private void applyDefaults(Balance balance) {
        if (balance.getAvailable() == null) {
            balance.setAvailable(BigDecimal.ZERO);
        }
        if (balance.getFrozen() == null) {
            balance.setFrozen(BigDecimal.ZERO);
        }
        if (balance.getTotal() == null) {
            balance.setTotal(BigDecimal.ZERO);
        }
    }
}
