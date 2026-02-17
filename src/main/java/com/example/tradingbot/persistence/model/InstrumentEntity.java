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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "instrument", uniqueConstraints = {
    @UniqueConstraint(name = "uk_instrument_exchange_name", columnNames = {"exchange_id", "name"}),
    @UniqueConstraint(name = "uk_instrument_exchange_inst_id", columnNames = {"exchange_id", "inst_id"})
})
public class InstrumentEntity extends AuditableEntity {

    public static final int NAME_LENGTH = 100;
    public static final int INST_ID_LENGTH = 100;
    public static final int INST_TYPE_LENGTH = 50;
    public static final int POSITION_MODE_LENGTH = 20;
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

    @Column(name = "name", nullable = false, length = NAME_LENGTH)
    private String name;

    @Column(name = "inst_id", nullable = false, length = INST_ID_LENGTH)
    private String instId;

    @Column(name = "inst_type", nullable = false, length = INST_TYPE_LENGTH)
    private String instType;

    @Column(name = "position_mode", nullable = false, length = POSITION_MODE_LENGTH)
    private String positionMode;

    @Column(name = "status", nullable = false, length = STATUS_LENGTH)
    private String status;

    @Column(name = "last_price", length = 64)
    private String lastPrice;

    @Column(name = "mark_price", length = 64)
    private String markPrice;

    @Column(name = "index_price", length = 64)
    private String indexPrice;

    @Column(name = "price_updated_at")
    private Instant priceUpdatedAt;
}
