package com.example.tradingcore.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Строка категорийной разбивки результата сделки (таблица
 * {@code deal_cash_flows}): одно движение счёта источника — одна строка
 * (docs/models/domain/other/DealCashFlow.md).
 *
 * <p><b>Ключ идемпотентности — пара «счёт, идентификатор записи».</b>
 * Идентификатор — номенклатура одного счёта, и без его оси повторный
 * проход по соседнему счёту мог бы столкнуться на чужом номере
 * (docs/rules/idempotency-via-unique.md).
 *
 * <p><b>Ссылка на сделку пуста законно:</b> движение вне окна живой
 * сделки линковке не подлежит и остаётся общим фактом счёта
 * (docs/spec/cash-flow-linkage.json).
 */
@Getter
@Setter
@Entity
@Table(name = "deal_cash_flows")
public class DealCashFlowEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deal_id")
    private Long dealId;

    @Column(name = "exchange_account_id", nullable = false)
    private Long exchangeAccountId;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "amount", nullable = false, precision = 36, scale = 18)
    private BigDecimal amount;

    @Column(name = "position_balance_change", precision = 36, scale = 18)
    private BigDecimal positionBalanceChange;

    @Column(name = "external_fee", precision = 36, scale = 18)
    private BigDecimal externalFee;

    @Column(name = "ccy", nullable = false)
    private String ccy;

    @Column(name = "applied_rate", precision = 36, scale = 18)
    private BigDecimal appliedRate;

    @Column(name = "rate_status", nullable = false)
    private String rateStatus;

    @Column(name = "applied_rate_candle_instrument")
    private String appliedRateCandleInstrument;

    @Column(name = "applied_rate_candle_timeframe")
    private String appliedRateCandleTimeframe;

    @Column(name = "applied_rate_candle_open_time")
    private OffsetDateTime appliedRateCandleOpenTime;

    @Column(name = "external_instrument_id")
    private String externalInstrumentId;

    @Column(name = "external_bill_id", nullable = false)
    private String externalBillId;

    @Column(name = "external_type", nullable = false)
    private String externalType;

    @Column(name = "external_sub_type")
    private String externalSubType;

    @Column(name = "external_order_id")
    private String externalOrderId;
}
