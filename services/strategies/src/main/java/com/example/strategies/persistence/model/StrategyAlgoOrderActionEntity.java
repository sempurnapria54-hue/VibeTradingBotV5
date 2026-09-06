package com.example.strategies.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Вид действия «условная заявка» (таблица strategy_algo_order_actions). */
@Getter
@Setter
@Entity
@Table(name = "strategy_algo_order_actions")
@DiscriminatorValue("ALGO_ORDER")
@PrimaryKeyJoinColumn(name = "id")
public class StrategyAlgoOrderActionEntity extends StrategyActionEntity {

    @Column(name = "condition_type", nullable = false)
    private String conditionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stop_loss_settings")
    private String stopLossSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trailing_settings")
    private String trailingSettings;

    @Column(name = "close_fraction_percents", precision = 36, scale = 18)
    private BigDecimal closeFractionPercents;

    @Column(name = "trigger_profit_percents", precision = 36, scale = 18)
    private BigDecimal triggerProfitPercents;

    @Column(name = "trigger_price_type")
    private String triggerPriceType;
}
