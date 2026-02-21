package com.example.tradingbot.domain.model.entity;

import com.example.tradingbot.rest.model.request.CreateInstrumentRequest;
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
import java.util.UUID;

import static com.example.tradingbot.util.Constant.Service.DEFAULT_POSITION_MODE;
import static com.example.tradingbot.util.Constant.Status.Instrument.INSTRUMENT_STATUS_CREATED;
import static com.example.tradingbot.util.factory.CandleGroupFactory.createCandleGroup;
import static java.util.stream.Collectors.toList;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "instrument", uniqueConstraints = {
        @UniqueConstraint(name = "uk_instrument_internal_id", columnNames = "internal_id"),
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
    @Column(name = "internal_id", nullable = false, updatable = false, length = 36)
    private String internalId;

    /**
     * Внутренний идентификатор биржи.
     */
    @Column(name = "exchange_id", nullable = false, updatable = false, insertable = false)
    private Long exchangeId;

    /**
     * Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP.
     */
    @Column(name = "external_name", nullable = false)
    private String externalId;

    /**
     * Внутреннее имя инструмента.
     */
    @Column(name = "name", nullable = false)
    private String name;

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

    /**
     * Последняя торговая цена по инструменту.
     */
    @Column(name = "last_price")
    private String lastPrice;

    /**
     * Текущая mark price инструмента.
     */
    @Column(name = "mark_price")
    private String markPrice;

    /**
     * Текущая индексная цена инструмента.
     */
    @Column(name = "index_price")
    private String indexPrice;

    /**
     * Время последнего обновления ценовых полей.
     */
    @Column(name = "price_updated_at")
    private Instant priceUpdatedAt;

    /**
     * Набор групп свечей для разных таймфреймов инструмента.
     */
    @OneToMany(mappedBy = "instrument", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CandleGroupEntity> candleGroups;

    public void initOnCreate(Long exchangeId, CreateInstrumentRequest request) {
        setInternalId(UUID.randomUUID().toString());
        setType(request.getType());
        setExternalId(request.getExternalId());
        setName(request.getName());
        setExchangeId(exchangeId);
        setPositionMode(DEFAULT_POSITION_MODE);
        setStatus(INSTRUMENT_STATUS_CREATED);
        List<CandleGroupEntity> groupEntities = request.getTimeFrames().stream()
                .map(timeFrame -> createCandleGroup(this, timeFrame))
                .collect(toList());

        setCandleGroups(groupEntities);
    }
}
