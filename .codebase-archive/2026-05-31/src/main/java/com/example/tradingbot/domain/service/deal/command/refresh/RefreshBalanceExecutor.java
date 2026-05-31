package com.example.tradingbot.domain.service.deal.command.refresh;

import com.example.tradingbot.client.service.ClientManager;
import com.example.tradingbot.domain.model.core.balance.Balance;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.mapping.BalanceContainerMapper;
import com.example.tradingbot.persistence.service.BalanceContainerDataService;
import com.example.tradingbot.util.factory.BalanceContainerFactory;
import com.example.tradingbot.util.factory.BalanceFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class RefreshBalanceExecutor {

    private final ClientManager clientManager;
    private final BalanceContainerDataService balanceContainerDataService;
    private final BalanceFactory balanceFactory;
    private final BalanceContainerFactory balanceContainerFactory;
    private final BalanceContainerMapper balanceContainerMapper;

    @Transactional
    public void execute(Exchange exchange) {
        BalanceContainerExternalSnapshot externalSnapshot = clientManager.getClientService(exchange.getName())
                                                                         .getBalanceContainer(exchange);
        if (externalSnapshot == null) {
            return;
        }

        BalanceContainer balanceContainer = balanceContainerDataService.findByExchangeIdWithBalances(exchange.getId());
        if (isNull(balanceContainer)) {
            balanceContainer = balanceContainerFactory.createFromSnapshot(exchange.getId(), externalSnapshot);
        } else {
            balanceContainerMapper.updateDomainFromExternalSnapshot(externalSnapshot, balanceContainer);
        }

        List<Balance> balances = balanceFactory.createBalances(externalSnapshot.getBalanceExternalSnapshots());
        balanceContainer.replaceBalances(balances);
        balanceContainerDataService.save(balanceContainer);
    }
}
