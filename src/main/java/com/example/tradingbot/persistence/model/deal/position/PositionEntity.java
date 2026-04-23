package com.example.tradingbot.persistence.model.deal.position;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

import static com.example.tradingbot.util.Constant.Service.PRICE_PRECISION;
import static com.example.tradingbot.util.Constant.Service.PRICE_SCALE;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "positions", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_positions_external_id",
                columnNames = {"external_id"}
        )
})
public class PositionEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Идентификатор сделки-владельца.
     */
    @Column(name = "deal_id", nullable = false, updatable = false)
    private Long dealId;

    /**
     * Межсервисный идентификатор.
     */
    @Column(name = "internal_id", nullable = false)
    private String internalId;

    /**
     * Идентификатор позиции на бирже.
     */
    @Column(name = "external_id", nullable = false, updatable = false)
    private String externalId;

    /**
     * Текущий внутренний статус позиции.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    /**
     * Сторона позиции (long/short/net).
     */
    @Column(name = "side", nullable = false, updatable = false)
    private String side;

    /**
     * Сторона позиции на бирже (long/short/net).
     */
    @Column(name = "external_side", nullable = false)
    private String externalSide;

    /**
     * Размер позиции.
     */
    @Column(name = "size", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal size;

    /**
     * Средняя цена входа в позицию.
     */
    @Column(name = "average_price", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal averagePrice;

    /**
     * Текущая рыночная цена позиции.
     */
    @Column(name = "mark_price", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal markPrice;

    /**
     * Оценочная цена ликвидации позиции.
     */
    @Column(name = "liquidation_price", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal liquidationPrice;

    /**
     * Плечо позиции.
     */
    @Column(name = "leverage", nullable = false)
    private Integer leverage;

    /**
     * Биржевой режим маржи (cross/isolated).
     */
    @Column(name = "margin_mode", nullable = false, updatable = false)
    private String marginMode;

    /**
     * Нереализованный PnL по позиции.
     */
    @Column(name = "unrealized_profit", precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal unrealizedProfit;

    public enum Status {
        ACTIVE,
        CLOSED,
        ERROR
    }
}
