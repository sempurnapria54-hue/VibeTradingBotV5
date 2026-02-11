package com.example.tradingbot.persistence.model;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "exchange_id", nullable = false, updatable = false, insertable = false)
    private Long exchangeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_id", nullable = false)
    private ExchangeEntity exchange;

    @Column(name = "instrument_id", nullable = false, updatable = false, insertable = false)
    private Long instrumentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InstrumentEntity instrument;

    @Column(name = "side", length = SIDE_LENGTH)
    private String side;

    @Column(name = "status", nullable = false, length = STATUS_LENGTH)
    private String status;

    @Column(name = "pos", length = 64)
    private String pos;

    @Column(name = "avg_px", length = 64)
    private String avgPx;

    @Column(name = "mark_px", length = 64)
    private String markPx;

    @Column(name = "liq_px", length = 64)
    private String liqPx;

    @Column(name = "lever", length = 32)
    private String lever;

    @Column(name = "mgn_mode", length = 16)
    private String mgnMode;

    @Column(name = "upl", length = 64)
    private String upl;

    @Column(name = "u_time")
    private Long uTime;
}
