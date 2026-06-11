package com.example.tradingbot.domain.model.core.balance;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted account-state snapshot aggregate по exchange account:
 * последняя известная картина equity / available средств. Не trading
 * runtime entity (нет Status / lifecycle / live risk). Обновляется
 * только REFRESH_BALANCE (replace semantics). Свежесть вычисляется
 * (externalUpdatedAt + expiration), не хранится. См.
 * docs/models/domain/core/BalanceContainer.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class BalanceContainer extends Auditable {

    /** Внутренний технический идентификатор snapshot в БД. */
    private Long id;

    /** Биржа / exchange account, которому принадлежит snapshot. */
    private Long exchangeId;

    /** Время обновления account-level snapshot на бирже (база freshness-check). */
    private OffsetDateTime externalUpdatedAt;

    /** Total equity аккаунта по данным биржи. */
    private BigDecimal externalTotalEquity;

    /** Adjusted / effective equity (предпочтительная база risk-policy). */
    private BigDecimal externalAdjustedEquity;

    /** Account-level available equity. */
    private BigDecimal externalAvailableEquity;

    /** Балансы по валютам (для SWAP/USDT обязательна settle currency). */
    private List<Balance> balances;

    /** Полная замена currency-level списка: новый валидный snapshot вытесняет старый (REFRESH_BALANCE). */
    public void replaceBalances(List<Balance> newBalances) {
        this.balances = newBalances;
    }
}
