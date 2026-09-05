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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Проекция каталога инструментов (таблица instruments базы trading_core).
 *
 * <p><b>Не вторая истина, а проекция:</b> строки правит только тик синка,
 * прикладной код их читает
 * (docs/architecture/data-ownership.md §«Копии чужих данных»).
 *
 * <p><b>Ступени, плеча и режима маржи здесь нет:</b> их пишет само ядро, а
 * синк перезаписывает строку и запись ядра затирал бы. Их дом — таблица
 * {@code account_instrument_states} с ключом «счёт, инструмент»
 * (docs/models/domain/core/Instrument.md §«Ступень и настройки счёта на
 * инструменте»).
 *
 * <p>Справочные правила лежат JSONB-навесом в колонке владельца: они
 * операнд каждого сайзинга, и сетевой вызов на каждое действие ставил бы
 * преконтроль риска в зависимость от доступности сервиса данных.
 */
@Getter
@Setter
@Entity
@Table(name = "instruments")
public class InstrumentEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Идентичность инструмента у владельца каталога; своей ядро не заводит. */
    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "exchange_code", nullable = false, updatable = false)
    private String exchangeCode;

    @Column(name = "external_id", nullable = false, updatable = false)
    private String externalId;

    @Column(name = "external_type", nullable = false)
    private String externalType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "external_settlement_currency")
    private String externalSettlementCurrency;

    @Column(name = "external_base_currency")
    private String externalBaseCurrency;

    @Column(name = "external_quote_currency")
    private String externalQuoteCurrency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_rules")
    private String externalRules;

    /**
     * Момент снимка строки целиком — операнд гейта свежести.
     *
     * <p>Двигается только тогда, когда прочитаны И спецификация, И
     * правила: одна метка обязана описывать всю строку, иначе гейт мерил
     * бы свежесть половины (docs/models/domain/core/Instrument.md
     * §«Срок свежести проекции: величина, писатель, реакция»).
     */
    @Column(name = "projected_at", nullable = false)
    private OffsetDateTime projectedAt;
}
