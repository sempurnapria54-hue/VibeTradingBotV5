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

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.isNull;

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

        BalanceContainer balanceContainer = balanceContainerDataService.findByExchangeIdWithBalances(exchange.getId());
        if (isNull(balanceContainer)) {
            balanceContainer = BalanceContainerFactory.createBalanceContainer(exchange.getId());
        }

        balanceContainerMapper.updateDomainFromSnapshot(externalSnapshot, balanceContainer);
        List<Balance> balances = createBalances(exchange.getId(), externalSnapshot.getBalanceExternalSnapshots());
        balanceContainer.replaceBalances(balances);
        balanceContainerDataService.save(balanceContainer);
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
            balances.add(balance);
        }
        return balances;
    }
}
