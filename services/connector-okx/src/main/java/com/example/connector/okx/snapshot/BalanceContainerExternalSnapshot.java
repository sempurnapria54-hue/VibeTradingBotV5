package com.example.connector.okx.snapshot;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Account-level нормализованный снапшот баланса — validated boundary
 * object для REFRESH_BALANCE (raw OKX DTO за adapter не выходит).
 * Числовые поля остаются строками (провалидированы как parseable).
 *
 * <p><b>Счёта снимок не несёт:</b> коннектор стейтлесс и внутренних
 * идентификаторов наших строк не знает — {@code exchangeAccountId}
 * проставляет ядро, приземляя снимок
 * (docs/models/domain/core/BalanceContainer.md,
 * docs/models/mapping/Balance.md).
 */
@Value
@Builder
public class BalanceContainerExternalSnapshot {

    /** Время обновления account snapshot (OKX uTime). */
    OffsetDateTime externalUpdatedAt;

    /** Total equity аккаунта. */
    String externalTotalEquity;

    /** Adjusted / effective equity. */
    String externalAdjustedEquity;

    /** Account-level available equity. */
    String externalAvailableEquity;

    /** Балансы по валютам. */
    List<BalanceExternalSnapshot> balances;
}
