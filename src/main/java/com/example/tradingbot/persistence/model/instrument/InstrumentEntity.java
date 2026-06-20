package com.example.tradingbot.persistence.model.instrument;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.persistence.model.AuditableEntity;
import com.example.tradingbot.persistence.model.candle.CandleGroupEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence-проекция {@link Instrument} (таблица instruments).
 * Владеет группами свечей (агрегат: cascade ALL + orphanRemoval).
 * Реальная схема (уникальности internal_id и (exchange_id,
 * external_id), FK) — во Flyway-миграциях. Enum'ы — только в домене;
 * статус и режим маржи хранятся строкой (значение = {@code name()}).
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

    @Column(name = "exchange_id", nullable = false, updatable = false)
    private Long exchangeId;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "external_type", nullable = false)
    private String externalType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "external_status")
    private String externalStatus;

    @Column(name = "margin_mode", nullable = false, updatable = false)
    private String marginMode;

    @Column(name = "external_margin_mode")
    private String externalMarginMode;

    @Column(name = "leverage", nullable = false)
    private Integer leverage;

    @Column(name = "external_leverage")
    private String externalLeverage;

    @Column(name = "planned_candle_start_date")
    private Long plannedCandleStartDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_rules")
    private String externalRules;

    @OneToMany(mappedBy = "instrument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CandleGroupEntity> candleGroups;
}
