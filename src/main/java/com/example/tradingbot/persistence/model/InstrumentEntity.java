package com.example.tradingbot.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "instruments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_instrument_internal_id", columnNames = "internal_id"),
        @UniqueConstraint(name = "uk_instrument_exchange_id_external_id", columnNames = {"exchange_id", "external_id"})
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
    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    /**
     * Внутренний идентификатор биржи.
     */
    @Column(name = "exchange_id", nullable = false, updatable = false)
    private Long exchangeId;

    /**
     * Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP.
     */
    @Column(name = "external_id", nullable = false)
    private String externalId;

    /**
     * Тип инструмента на бирже: SPOT/MARGIN/SWAP/FUTURES/OPTION.
     */
    @Column(name = "type", nullable = false)
    private String type;

    /**
     * Статус: CREATED/HOLD/SYNC/CANDLES_LOADING/ACTIVE.
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Режим маржи (cross/isolated).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "margin_mode", nullable = false, updatable = false)
    private MarginMode marginMode;

    /**
     * Плечо.
     */
    @Column(name = "leverage", nullable = false)
    private Integer leverage;

    /**
     * Набор групп свечей для разных таймфреймов инструмента.
     */
    @OneToMany(mappedBy = "instrument", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CandleGroupEntity> candleGroups;

    public enum MarginMode {
        ISOLATED,
        CROSS
    }

}
