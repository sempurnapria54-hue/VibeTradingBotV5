package com.example.tradingbot.persistence.model.fee;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@link com.example.tradingbot.domain.model.other.TradeFeeRate}
 * (таблица trade_fee_rates). Строка на комиссионную группу; ставки —
 * строки по названному исключению численной конвенции
 * (docs/rules/persistence-representation.md), доменная проекция типа —
 * строкой без {@code @Enumerated}.
 */
@Getter
@Setter
@Entity
@Table(name = "trade_fee_rates")
public class TradeFeeRateEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exchange_id", nullable = false)
    private Long exchangeId;

    @Column(name = "external_instrument_type", nullable = false)
    private String externalInstrumentType;

    @Column(name = "external_fee_group_id", nullable = false)
    private String externalFeeGroupId;

    @Column(name = "instrument_type", nullable = false)
    private String instrumentType;

    @Column(name = "external_taker_fee_rate", nullable = false)
    private String externalTakerFeeRate;

    @Column(name = "external_maker_fee_rate", nullable = false)
    private String externalMakerFeeRate;

    @Column(name = "external_fee_level")
    private String externalFeeLevel;

    @Column(name = "external_modified_at")
    private OffsetDateTime externalModifiedAt;

    @Column(name = "refresh_count", nullable = false)
    private Long refreshCount;
}
