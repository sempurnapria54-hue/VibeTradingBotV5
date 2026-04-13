package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.balance.Balance;
import com.example.tradingbot.domain.model.balance.BalanceContainer;
import com.example.tradingbot.domain.model.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.domain.model.balance.external_snapshot.BalanceExternalSnapshot;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.mapping.BalanceContainerMapper;
import com.example.tradingbot.mapping.BalanceMapper;
import com.example.tradingbot.persistence.service.BalanceContainerDataService;
import com.example.tradingbot.persistence.service.BalanceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshBalanceExecutor {

    private final ClientManager clientManager;
    private final BalanceContainerDataService balanceContainerDataService;
    private final BalanceDataService balanceDataService;
    private final BalanceContainerMapper balanceContainerMapper;
    private final BalanceMapper balanceMapper;

    @Transactional
    public void execute(Exchange exchange) {
        BalanceContainerExternalSnapshot externalSnapshot = clientManager.getClientService(exchange.getName())
                                                                         .getBalanceContainer(exchange);
        if (externalSnapshot == null) {
            return;
        }

        BalanceContainer balanceContainer = resolveOrCreateContainer(exchange.getId());
        balanceContainerMapper.updateDomainFromSnapshot(externalSnapshot, balanceContainer);
        applyContainerDefaults(balanceContainer);
        BalanceContainer savedContainer = balanceContainerDataService.save(balanceContainer);

        List<BalanceExternalSnapshot> balanceSnapshots = externalSnapshot.getBalanceExternalSnapshots();
        if (balanceSnapshots == null) {
            return;
        }

        for (BalanceExternalSnapshot snapshot : balanceSnapshots) {
            if (snapshot == null || snapshot.getCurrency() == null) {
                continue;
            }
            Balance balance = resolveOrCreateBalance(exchange.getId(), snapshot.getCurrency(), savedContainer.getId());
            balanceMapper.updateDomainFromSnapshot(snapshot, balance);
            applyBalanceDefaults(balance);
            balanceDataService.save(balance);
        }
    }

    private BalanceContainer resolveOrCreateContainer(Long exchangeId) {
        Optional<BalanceContainer> existingContainer = balanceContainerDataService.findByExchangeId(exchangeId);
        if (existingContainer.isPresent()) {
            return existingContainer.get();
        }

        BalanceContainer createdContainer = new BalanceContainer();
        createdContainer.setExchangeId(exchangeId);
        createdContainer.setTotalEquity(BigDecimal.ZERO);
        createdContainer.setUnrealizedProfit(BigDecimal.ZERO);
        return createdContainer;
    }

    private Balance resolveOrCreateBalance(Long exchangeId, String currency, Long balanceContainerId) {
        Optional<Balance> existingBalance = balanceDataService.findByExchangeIdAndCurrency(exchangeId, currency);
        if (existingBalance.isPresent()) {
            Balance balance = existingBalance.get();
            balance.setBalanceContainerId(balanceContainerId);
            return balance;
        }

        Balance createdBalance = new Balance();
        createdBalance.setExchangeId(exchangeId);
        createdBalance.setCurrency(currency);
        createdBalance.setBalanceContainerId(balanceContainerId);
        createdBalance.setAvailable(BigDecimal.ZERO);
        createdBalance.setFrozen(BigDecimal.ZERO);
        createdBalance.setTotal(BigDecimal.ZERO);
        return createdBalance;
    }

    private void applyContainerDefaults(BalanceContainer balanceContainer) {
        if (balanceContainer.getTotalEquity() == null) {
            balanceContainer.setTotalEquity(BigDecimal.ZERO);
        }
        if (balanceContainer.getUnrealizedProfit() == null) {
            balanceContainer.setUnrealizedProfit(BigDecimal.ZERO);
        }
    }

    private void applyBalanceDefaults(Balance balance) {
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
