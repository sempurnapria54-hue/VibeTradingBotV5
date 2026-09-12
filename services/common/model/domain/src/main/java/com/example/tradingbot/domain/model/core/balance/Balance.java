package com.example.tradingbot.domain.model.core.balance;

import com.example.tradingbot.domain.model.Auditable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Currency-level snapshot одной валюты внутри BalanceContainer (раздел
 * модели, не самостоятельная торговая сущность). См.
 * docs/models/domain/core/BalanceContainer.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class Balance extends Auditable {

    /** Внутренний идентификатор currency balance в БД. */
    private Long id;

    /** Родительский BalanceContainer. */
    private Long balanceContainerId;

    /** Валюта по данным биржи (например, USDT). */
    private String externalCurrency;

    /** Время обновления currency-level snapshot на бирже. */
    private OffsetDateTime externalUpdatedAt;

    /** Equity по валюте. */
    private BigDecimal externalEquity;

    /** Cash balance по валюте. */
    private BigDecimal externalCashBalance;

    /** Доступный баланс по валюте. */
    private BigDecimal externalAvailableBalance;

    /** Замороженный баланс по валюте (диагностика недоступных средств). */
    private BigDecimal externalFrozenBalance;
}
