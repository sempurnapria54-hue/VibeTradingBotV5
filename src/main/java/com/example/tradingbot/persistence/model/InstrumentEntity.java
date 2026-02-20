package com.example.tradingbot.persistence.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.example.tradingbot.util.Constant.Service.DEFAULT_POSITION_MODE;
import static com.example.tradingbot.util.Constant.Status.Instrument.INSTRUMENT_STATUS_CREATED;
import static com.example.tradingbot.util.factory.CandleGroupFactory.createCandleGroup;
import static java.util.stream.Collectors.toList;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "instrument", uniqueConstraints = {
        @UniqueConstraint(name = "uk_instrument_exchange_name", columnNames = {"exchange_id", "name"}),
        @UniqueConstraint(name = "uk_instrument_exchange_inst_id", columnNames = {"exchange_id", "inst_id"})
})
public class InstrumentEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор инструмента.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Межсервисный идентификатор инструмента.
     */
    @Column(name = "internal_id", nullable = false)
    private String internalId;

    /**
     * Внутренний идентификатор биржи.
     */
    @Column(name = "exchange_id", nullable = false, updatable = false, insertable = false)
    private Long exchangeId;

    /**
     * Ссылка на биржу (например OKX)
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_id", nullable = false)
    private ExchangeEntity exchange;

    /**
     * Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP.
     */
    @Column(name = "external_name", nullable = false)
    private String externalName;

    /**
     * Тип инструмента на бирже: SPOT/MARGIN/SWAP/FUTURES/OPTION.
     */
    @Column(name = "type", nullable = false)
    private String type;

    /**
     * Признак наличия позиций: OPEN/NONE.
     */
    @Column(name = "position_mode", nullable = false)
    private String positionMode;

    /**
     * Статус: CREATED/HOLD/SYNC/CANDLES_LOADING/ACTIVE.
     */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "last_price")
    private String lastPrice;

    @Column(name = "mark_price")
    private String markPrice;

    @Column(name = "index_price")
    private String indexPrice;

    @Column(name = "price_updated_at")
    private Instant priceUpdatedAt;

    @OneToMany(mappedBy = "instrument", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CandleGroupEntity> candleGroups;

    public void initOnCreate(ExchangeEntity exchange, Set<String> timeFrames) {
        setExchange(exchange);
        setPositionMode(DEFAULT_POSITION_MODE);
        setStatus(INSTRUMENT_STATUS_CREATED);
        List<CandleGroupEntity> groupEntities = timeFrames.stream()
                .map(timeFrame -> createCandleGroup(this, timeFrame))
                .collect(toList());

        setCandleGroups(groupEntities);
    }
}
