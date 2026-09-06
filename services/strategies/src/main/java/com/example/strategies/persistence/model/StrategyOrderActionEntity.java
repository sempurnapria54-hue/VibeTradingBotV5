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

/** Вид действия «обычная заявка» (таблица strategy_order_actions). */
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

    @Column(name = "allocation_percents", precision = 36, scale = 18)
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
