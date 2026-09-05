package com.example.connector.okx.snapshot;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Currency-level нормализованный снапшот баланса внутри
 * BalanceContainerExternalSnapshot. Числовые поля остаются строками (уже
 * провалидированы как parseable decimal; парс в BigDecimal — при записи
 * в домен RefreshBalanceExecutor'ом). См.
 * docs/models/domain/core/BalanceContainer.md, docs/models/mapping/Balance.md.
 */
@Value
@Builder
public class BalanceExternalSnapshot {

    /** Валюта (OKX ccy). */
    String externalCurrency;

    /** Время обновления currency snapshot (OKX uTime). */
    OffsetDateTime externalUpdatedAt;

    /** Equity по валюте. */
    String externalEquity;

    /** Cash balance по валюте. */
    String externalCashBalance;

    /** Доступный баланс по валюте. */
    String externalAvailableBalance;

    /** Замороженный баланс по валюте. */
    String externalFrozenBalance;
}
