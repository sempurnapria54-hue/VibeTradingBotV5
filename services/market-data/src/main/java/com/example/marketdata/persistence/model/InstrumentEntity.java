package com.example.marketdata.persistence.model;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence-проекция {@link Instrument} (таблица instruments) —
 * каталог инструментов подключённых площадок.
 *
 * <p><b>Площадка названа кодом, а не числовым идентификатором:</b>
 * справочник площадок принадлежит {@code auth}
 * (docs/architecture/tenant-and-exchange.md §«Три сущности вместо
 * одной»), а внешнего ключа через границу сервиса не бывает.
 *
 * <p><b>Плеча и режима маржи здесь нет:</b> они настройка СЧЁТА на
 * инструменте, а не свойство инструмента (там же, §Инструменты), а счетов
 * у market-data нет вовсе.
 *
 * <p>Справочные правила лежат JSONB-навесом в колонке владельца: своей
 * таблицы у них нет, доступ — только через инструмент.
 */
@Getter
@Setter
@Entity
@Table(name = "instruments")
public class InstrumentEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "external_status")
    private String externalStatus;

    @Column(name = "external_settlement_currency")
    private String externalSettlementCurrency;

    @Column(name = "external_base_currency")
    private String externalBaseCurrency;

    @Column(name = "external_quote_currency")
    private String externalQuoteCurrency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_rules")
    private String externalRules;
}
