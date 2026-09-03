package com.example.tradingbot.domain.model.other.external_snapshot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Граничный снапшот одного движения средств источника — boundary object
 * команды добычи движений (docs/models/mapping/DealCashFlow.md
 * §«Граничный снапшот»). Категории, биржи, курса и ссылки на сделку не
 * несёт — это не факты ответа источника, их производит вызывающий;
 * поэтому доменная модель за границу не выходит. Пустая комиссионная
 * компонента остаётся пустой: пустота нулём не подменяется
 * (docs/rules/absent-value-semantics.md).
 */
@Value
@Builder
public class DealCashFlowExternalSnapshot {

    /** Идентификатор записи источника (OKX billId) — ключ дедупа и якорь пагинации. */
    String externalBillId;

    /** Знаковая сумма движения по балансу счёта (OKX balChg). */
    BigDecimal amount;

    /**
     * Знаковое изменение маржи позиции (OKX posBalChg). На isolated-марже
     * несёт то, чего нет в балансе: расчёт финансирования ложится сюда
     * при нулевом balChg (наблюдение AG1.7 2026-09-02), у торговых
     * записей — перевод маржи (наблюдение AG6.1). Сырой факт; как он
     * входит в пары сверки — решает дом сверки, не граница.
     */
    BigDecimal positionBalanceChange;

    /** Знаковая комиссионная компонента, сырая (OKX fee); пусто — источник поля не отдал. */
    BigDecimal externalFee;

    /** Валюта движения (OKX ccy). */
    String ccy;

    /** Сырой тип записи (OKX type). */
    String externalType;

    /** Сырой подтип записи (OKX subType). */
    String externalSubType;

    /** Заявка, если движение с ней связано (OKX ordId). */
    String externalOrderId;

    /** Инструмент движения (OKX instId) — ось предиката линковки. */
    String externalInstrumentId;

    /** Время события источника (OKX ts). */
    OffsetDateTime externalCreatedAt;
}
