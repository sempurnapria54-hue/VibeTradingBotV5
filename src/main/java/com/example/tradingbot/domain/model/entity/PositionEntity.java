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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "position")
public class PositionEntity extends AuditableEntity {

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
    @Column(name = "side")
    private String positionSide;

    /** Текущий внутренний статус позиции. */
    @Column(name = "status", nullable = false)
    private String status;

    /** Размер позиции. */
    @Column(name = "pos")
    private String positionSize;

    /** Средняя цена входа в позицию. */
    @Column(name = "avg_px")
    private String averagePrice;

    /** Текущая mark price позиции. */
    @Column(name = "mark_px")
    private String markPrice;

    /** Оценочная цена ликвидации позиции. */
    @Column(name = "liq_px")
    private String liquidationPrice;

    /** Плечо позиции. */
    @Column(name = "lever")
    private String leverage;

    /** Режим маржи (cross/isolated). */
    @Column(name = "mgn_mode")
    private String marginMode;

    /** Нереализованный PnL по позиции. */
    @Column(name = "upl")
    private String unrealizedProfit;

    /** Время обновления позиции на бирже в UTC миллисекундах. */
    @Column(name = "u_time")
    private OffsetDateTime exchangeModifiedAt;
}
