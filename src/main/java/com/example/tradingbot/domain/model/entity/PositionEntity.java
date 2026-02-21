package com.example.tradingbot.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "position")
public class PositionEntity extends AuditableEntity {

    public static final int SIDE_LENGTH = 20;
    public static final int STATUS_LENGTH = 50;

    /** Внутренний идентификатор позиции. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Идентификатор биржи, к которой относится позиция. */
    @Column(name = "exchange_id", nullable = false, updatable = false, insertable = false)
    private Long exchangeId;

    /** Ссылка на биржу позиции. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_id", nullable = false)
    private ExchangeEntity exchange;

    /** Идентификатор инструмента позиции. */
    @Column(name = "instrument_id", nullable = false, updatable = false, insertable = false)
    private Long instrumentId;

    /** Ссылка на инструмент позиции. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InstrumentEntity instrument;

    /** Сторона позиции (long/short/net). */
    @Column(name = "side", length = SIDE_LENGTH)
    private String side;

    /** Текущий внутренний статус позиции. */
    @Column(name = "status", nullable = false, length = STATUS_LENGTH)
    private String status;

    /** Размер позиции. */
    @Column(name = "pos", length = 64)
    private String pos;

    /** Средняя цена входа в позицию. */
    @Column(name = "avg_px", length = 64)
    private String avgPx;

    /** Текущая mark price позиции. */
    @Column(name = "mark_px", length = 64)
    private String markPx;

    /** Оценочная цена ликвидации позиции. */
    @Column(name = "liq_px", length = 64)
    private String liqPx;

    /** Плечо позиции. */
    @Column(name = "lever", length = 32)
    private String lever;

    /** Режим маржи (cross/isolated). */
    @Column(name = "mgn_mode", length = 16)
    private String mgnMode;

    /** Нереализованный PnL по позиции. */
    @Column(name = "upl", length = 64)
    private String upl;

    /** Время обновления позиции на бирже в UTC миллисекундах. */
    @Column(name = "u_time")
    private Long uTime;
}
