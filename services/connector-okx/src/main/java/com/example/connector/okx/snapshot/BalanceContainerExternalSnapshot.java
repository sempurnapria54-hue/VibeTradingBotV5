package com.example.connector.okx.snapshot;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Account-level нормализованный снапшот баланса — validated boundary
 * object для REFRESH_BALANCE (raw OKX DTO за adapter не выходит).
 * Числовые поля остаются строками (провалидированы как parseable).
 * exchangeId проставляет executor из DealContext (не из OKX). См.
 * docs/models/domain/core/BalanceContainer.md, docs/models/mapping/Balance.md.
 */
@Value
@Builder
public class BalanceContainerExternalSnapshot {

    /** Внутренний идентификатор биржи/аккаунта (проставляется executor'ом). */
    Long exchangeId;

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
