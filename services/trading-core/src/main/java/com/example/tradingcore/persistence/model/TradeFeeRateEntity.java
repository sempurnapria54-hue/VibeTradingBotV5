package com.example.tradingcore.persistence.model;

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
 * Ставка комиссии комиссионной группы биржевого счёта (таблица
 * trade_fee_rates, docs/models/domain/other/TradeFeeRate.md).
 *
 * <p><b>Владелец — СЧЁТ, а не площадка:</b> чтение ставок приватное, то
 * есть требует ключей счёта, и уровень комиссии — свойство счёта. Строка
 * донора ключевалась биржей; радиус переведён вместе с переездом реестра к
 * владельцу счёта.
 *
 * <p><b>Строк на группу несколько, и это правило истории:</b> значение
 * изменилось — новая строка, совпало — подтверждение последней на месте.
 * Актуальная резолвится порядком (последняя по идентификатору), поэтому
 * уникального ключа по тройке здесь нет: он запретил бы историю.
 */
@Getter
@Setter
@Entity
@Table(name = "trade_fee_rates")
public class TradeFeeRateEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exchange_account_id", nullable = false)
    private Long exchangeAccountId;

    /** Ось группы: сырой тип инструмента источника. */
    @Column(name = "external_instrument_type", nullable = false)
    private String externalInstrumentType;

    /** Ось группы: сырой идентификатор комиссионной группы. */
    @Column(name = "external_fee_group_id", nullable = false)
    private String externalFeeGroupId;

    /** Доменная проекция типа — строкой, без {@code @Enumerated}. */
    @Column(name = "instrument_type", nullable = false)
    private String instrumentType;

    /** Ставка taker как издержка; строкой по названному исключению численной конвенции. */
    @Column(name = "external_taker_fee_rate", nullable = false)
    private String externalTakerFeeRate;

    /** Ставка maker, та же конвенция. */
    @Column(name = "external_maker_fee_rate", nullable = false)
    private String externalMakerFeeRate;

    /** Комиссионный уровень счёта на момент чтения. */
    @Column(name = "external_fee_level")
    private String externalFeeLevel;

    /** Время данных источника на момент последнего подтверждения строки. */
    @Column(name = "external_modified_at")
    private OffsetDateTime externalModifiedAt;

    /** Счётчик подтверждений строки. */
    @Column(name = "refresh_count", nullable = false)
    private Long refreshCount;
}
