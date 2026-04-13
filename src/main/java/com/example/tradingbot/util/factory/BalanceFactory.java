package com.example.tradingbot.util.factory;

import com.example.tradingbot.domain.model.balance.Balance;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;

@UtilityClass
public class BalanceFactory {

    public static Balance createBalance(Long exchangeId, String currency) {
        Balance balance = new Balance();
        balance.setExchangeId(exchangeId);
        balance.setCurrency(currency);
        balance.setAvailable(BigDecimal.ZERO);
        balance.setFrozen(BigDecimal.ZERO);
        balance.setTotal(BigDecimal.ZERO);
        return balance;
    }
}
