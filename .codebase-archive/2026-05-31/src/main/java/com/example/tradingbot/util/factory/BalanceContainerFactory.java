package com.example.tradingbot.util.factory;

import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.mapping.BalanceContainerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class BalanceContainerFactory {

    private final BalanceContainerMapper mapper;

    public BalanceContainer createFromSnapshot(Long exchangeId, BalanceContainerExternalSnapshot snapshot) {
        BalanceContainer balanceContainer = createEmptyBalanceContainer(exchangeId);
        mapper.updateDomainFromExternalSnapshot(snapshot, balanceContainer);
        return balanceContainer;
    }

    private static BalanceContainer createEmptyBalanceContainer(Long exchangeId) {
        BalanceContainer balanceContainer = new BalanceContainer();
        balanceContainer.setExchangeId(exchangeId);
        balanceContainer.setTotalEquity(BigDecimal.ZERO);
        balanceContainer.setUnrealizedProfit(BigDecimal.ZERO);
        balanceContainer.setBalances(new ArrayList<>());
        return balanceContainer;
    }
}
