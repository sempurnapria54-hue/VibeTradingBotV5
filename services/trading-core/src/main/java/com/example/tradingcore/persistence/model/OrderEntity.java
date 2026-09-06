package com.example.tradingcore.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Строка обычной заявки (таблица orders) — ноги транша: вход и его
 * reduce-only выходы.
 *
 * <p><b>Нога принадлежит траншу</b>, а не агрегату: {@code deal_id}
 * остаётся ключом выборки одним запросом на сделку, но собирается нога
 * обходом траншей (docs/models/domain/aggregate/Deal.md §Структура).
 *
 * <p>Встроенная защита — дочерние строки {@code attached_algo_orders} по
 * {@code order_id}; каскадной коллекции здесь нет, оркестрацию держит
 * {@code OrderDataService}. Замысел сайзинга хранится колонками
 * {@code planned_*}: проверка «взято по плану» без плана невыразима.
 */
@Getter
@Setter
@Entity
@Table(name = "orders")
public class OrderEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deal_id", nullable = false)
    private Long dealId;

    @Column(name = "deal_tranche_id")
    private Long dealTrancheId;

    /** Эпизод позиции, в котором нога исполнялась; пусто до наблюдения эпизода. */
    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "close_reason")
    private String closeReason;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "side")
    private String side;

    @Column(name = "external_status")
    private String externalStatus;

    @Column(name = "price", precision = 36, scale = 18)
    private BigDecimal price;

    @Column(name = "size", precision = 36, scale = 18)
    private BigDecimal size;

    @Column(name = "accumulated_fill_size", precision = 36, scale = 18)
    private BigDecimal accumulatedFillSize;

    @Column(name = "average_price", precision = 36, scale = 18)
    private BigDecimal averagePrice;

    @Column(name = "fee", precision = 36, scale = 18)
    private BigDecimal fee;

    @Column(name = "position_reducing_only")
    private Boolean positionReducingOnly;

    /** Клиентский идентификатор замещённой заявки — цепочка замещений. */
    @Column(name = "replaces_internal_id")
    private String replacesInternalId;

    @Column(name = "planned_entry_price", precision = 36, scale = 18)
    private BigDecimal plannedEntryPrice;

    @Column(name = "planned_stop_price", precision = 36, scale = 18)
    private BigDecimal plannedStopPrice;

    @Column(name = "planned_size_contracts", precision = 36, scale = 18)
    private BigDecimal plannedSizeContracts;

    @Column(name = "planned_contract_value", precision = 36, scale = 18)
    private BigDecimal plannedContractValue;

    @Column(name = "planned_risk_amount", precision = 36, scale = 18)
    private BigDecimal plannedRiskAmount;

    @Column(name = "planned_risk_currency")
    private String plannedRiskCurrency;

    /** Измеритель: запас до ликвидации на момент постановки; пуст — не наблюдался. */
    @Column(name = "liquidation_distance_ratio", precision = 36, scale = 18)
    private BigDecimal liquidationDistanceRatio;

    /** Измеритель: ёмкость стакана на момент постановки; пуста — свежих данных не было. */
    @Column(name = "book_depth_at_placement", precision = 36, scale = 18)
    private BigDecimal bookDepthAtPlacement;
}
