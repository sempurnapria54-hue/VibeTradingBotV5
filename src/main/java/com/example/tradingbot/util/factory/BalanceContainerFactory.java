package com.example.tradingbot.util.factory;

import com.example.tradingbot.domain.model.balance.BalanceContainer;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

@UtilityClass
public class BalanceContainerFactory {

    public static BalanceContainer createBalanceContainer(Long exchangeId) {
        BalanceContainer balanceContainer = new BalanceContainer();
        balanceContainer.setExchangeId(exchangeId);
        balanceContainer.setTotalEquity(BigDecimal.ZERO);
        balanceContainer.setUnrealizedProfit(BigDecimal.ZERO);
        balanceContainer.clearBalances();
        return balanceContainer;
    }
}
