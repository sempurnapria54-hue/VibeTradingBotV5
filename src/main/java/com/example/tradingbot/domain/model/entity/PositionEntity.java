package com.example.tradingbot.domain.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "position")
public class PositionEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор позиции.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Идентификатор биржи, к которой относится позиция.
     */
    @Column(name = "exchange_id", nullable = false, updatable = false)
    private Long exchangeId;

    /**
     * Идентификатор инструмента позиции.
     */
    @Column(name = "instrument_id", nullable = false, updatable = false)
    private Long instrumentId;

    /**
     * Сторона позиции (long/short/net).
     */
    @Column(name = "side")
    private String positionSide;

    /**
     * Текущий внутренний статус позиции.
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Размер позиции.
     */
    @Column(name = "pos")
    private String positionSize;

    /**
     * Средняя цена входа в позицию.
     */
    @Column(name = "avg_px")
    private String averagePrice;

    /**
     * Текущая mark price позиции.
     */
    @Column(name = "mark_px")
    private String markPrice;

    /**
     * Оценочная цена ликвидации позиции.
     */
    @Column(name = "liq_px")
    private String liquidationPrice;

    /**
     * Плечо позиции.
     */
    @Column(name = "lever")
    private String leverage;

    /**
     * Режим маржи (cross/isolated).
     */
    @Column(name = "mgn_mode")
    private String marginMode;

    /**
     * Нереализованный PnL по позиции.
     */
    @Column(name = "upl")
    private String unrealizedProfit;

    /**
     * Время обновления позиции на бирже в UTC миллисекундах.
     */
    @Column(name = "u_time")
    private OffsetDateTime exchangeModifiedAt;
}
