package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.util.Constants;
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

/**
 * Persistence-проекция {@link StrategyAlgoOrderAction} (таблица
 * strategy_algo_order_actions, вид ALGO_ORDER наследования JOINED).
 * Вложенные настройки (stopLossSettings, trailingSettings) — JSONB на
 * этой строке.
 */
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

    @Column(name = "close_fraction_percents",
            precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal closeFractionPercents;

    @Column(name = "trigger_profit_percents",
            precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal triggerProfitPercents;

    @Column(name = "trigger_price_type")
    private String triggerPriceType;
}
