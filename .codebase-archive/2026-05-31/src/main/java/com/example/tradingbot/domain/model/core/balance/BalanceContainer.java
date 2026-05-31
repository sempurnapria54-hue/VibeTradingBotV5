package com.example.tradingbot.domain.model.core.balance;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * Снимок баланса аккаунта на бирже.
 */
@Getter
@Setter
public class BalanceContainer extends Auditable {

    /**
     * Внутренний идентификатор.
     */
    private Long id;

    /**
     * Идентификатор биржи-владельца snapshot аккаунта.
     */
    private Long exchangeId;

    /**
     * Суммарная equity аккаунта.
     */
    private BigDecimal totalEquity;

    /**
     * Суммарный нереализованный PnL аккаунта.
     */
    private BigDecimal unrealizedProfit;

    /**
     * Время последнего обновления контейнера на стороне биржи.
     */
    private OffsetDateTime externalUpdatedAt;

    /**
     * Балансы аккаунта по валютам.
     */
    private List<Balance> balances;

    public void replaceBalances(List<Balance> balances) {
        clearBalances();
        if (isEmpty(balances)) {
            return;
        }
        setBalances(balances);
    }

    private void clearBalances() {
        if (balances == null) {
            balances = new ArrayList<>();
            return;
        }
        balances.clear();
    }
}
