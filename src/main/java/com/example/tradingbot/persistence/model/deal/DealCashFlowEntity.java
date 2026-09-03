package com.example.tradingbot.persistence.model.deal;

import com.example.tradingbot.persistence.model.AuditableEntity;
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
 * Persistence-проекция {@link com.example.tradingbot.domain.model.other.DealCashFlow}
 * (таблица deal_cash_flows). Енумы хранятся строкой без
 * {@code @Enumerated}; ключ идемпотентности — уникальное ограничение
 * (exchange_id, external_bill_id), носитель ключа — схема
 * (docs/rules/idempotency-via-unique.md). Время события источника —
 * унаследованная audit-колонка external_created_at; её обязательность
 * обеспечивается на границе разбора, а не колонкой.
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

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "position_balance_change")
    private BigDecimal positionBalanceChange;

    @Column(name = "external_fee")
    private BigDecimal externalFee;

    @Column(name = "ccy", nullable = false)
    private String ccy;

    @Column(name = "applied_rate")
    private BigDecimal appliedRate;

    @Column(name = "rate_status", nullable = false)
    private String rateStatus;

    @Column(name = "applied_rate_candle_instrument")
    private String appliedRateCandleInstrument;

    @Column(name = "applied_rate_candle_timeframe")
    private String appliedRateCandleTimeframe;

    @Column(name = "applied_rate_candle_open_time")
    private OffsetDateTime appliedRateCandleOpenTime;

    @Column(name = "exchange_id", nullable = false)
    private Long exchangeId;

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
