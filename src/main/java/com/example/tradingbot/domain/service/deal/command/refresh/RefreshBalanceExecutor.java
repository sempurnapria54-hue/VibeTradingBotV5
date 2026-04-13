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
import com.example.tradingbot.util.factory.BalanceContainerFactory;
import com.example.tradingbot.util.factory.BalanceFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefreshBalanceExecutor {

    private final ClientManager clientManager;
    private final BalanceContainerDataService balanceContainerDataService;
    private final BalanceContainerMapper balanceContainerMapper;
    private final BalanceMapper balanceMapper;

    @Transactional
    public void execute(Exchange exchange) {
        BalanceContainerExternalSnapshot externalSnapshot = clientManager.getClientService(exchange.getName())
                                                                         .getBalanceContainer(exchange);
        if (externalSnapshot == null) {
            return;
        }

        BalanceContainer balanceContainer = balanceContainerDataService.findByExchangeIdWithBalances(exchange.getId())
                                                                       .orElseGet(() -> BalanceContainerFactory.createBalanceContainer(
                                                                               exchange.getId()));
        balanceContainerMapper.updateDomainFromSnapshot(externalSnapshot, balanceContainer);
        applyContainerDefaults(balanceContainer);
        List<Balance> balances = createBalances(exchange.getId(), externalSnapshot.getBalanceExternalSnapshots());
        balanceContainer.replaceBalances(balances);
        balanceContainerDataService.save(balanceContainer);
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

    private List<Balance> createBalances(Long exchangeId, List<BalanceExternalSnapshot> balanceSnapshots) {
        List<Balance> balances = new ArrayList<>();
        if (balanceSnapshots == null) {
            return balances;
        }

        for (BalanceExternalSnapshot snapshot : balanceSnapshots) {
            if (snapshot == null || snapshot.getCurrency() == null) {
                continue;
            }
            Balance balance = BalanceFactory.createBalance(exchangeId, snapshot.getCurrency());
            balanceMapper.updateDomainFromSnapshot(snapshot, balance);
            applyBalanceDefaults(balance);
            balances.add(balance);
        }
        return balances;
    }
}
