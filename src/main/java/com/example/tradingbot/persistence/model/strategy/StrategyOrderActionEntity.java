package com.example.tradingbot.persistence.model.strategy;

import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
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
 * Persistence-проекция {@link StrategyOrderAction} (таблица
 * strategy_order_actions, вид ORDER наследования JOINED). Вложенные
 * настройки (placement, attachedProtection) — JSONB на этой строке.
 */
@Getter
@Setter
@Entity
@Table(name = "strategy_order_actions")
@DiscriminatorValue("ORDER")
@PrimaryKeyJoinColumn(name = "id")
public class StrategyOrderActionEntity extends StrategyActionEntity {

    @Column(name = "order_type", nullable = false)
    private String orderType;

    @Column(name = "direction", nullable = false)
    private String direction;

    @Column(name = "allocation_percents",
            precision = Constants.Price.PRECISION, scale = Constants.Price.SCALE)
    private BigDecimal allocationPercents;

    @Column(name = "position_reducing_only")
    private Boolean positionReducingOnly;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "placement")
    private String placement;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attached_protection")
    private String attachedProtection;
}
