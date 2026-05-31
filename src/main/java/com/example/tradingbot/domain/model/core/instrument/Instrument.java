package com.example.tradingbot.domain.model.core.instrument;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Торговый инструмент биржи — базовая идентичность для рыночных
 * данных и торговли. Несёт внутренний/межсервисный id, привязку к
 * бирже и биржевое воплощение (externalId = OKX instId). Владеет
 * группами свечей. Биржевые externalStatus/externalLeverage приходят
 * в InstrumentExternalSnapshot и персистятся; справочные sizing-поля
 * в шаге 1 персистентного дома не имеют (INSTR-Q1). См.
 * docs/models/domain/core/Instrument.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Instrument extends Auditable {

    /** Внутренний идентификатор инструмента. */
    private Long id;

    /** Межсервисный идентификатор инструмента. */
    private String internalId;

    /** Внутренний ID биржи (Exchange.id). */
    private Long exchangeId;

    /** Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP. */
    private String externalId;

    /** Тип инструмента на бирже: SPOT/MARGIN/SWAP/FUTURES/OPTION (сырой). */
    private String externalType;

    /** Нормализованный онбординг-статус инструмента в системе. */
    private Status status;

    /** Биржевой статус инструмента (сырой, OKX state). Не путать с онбординг-status. */
    private String externalStatus;

    /** Режим маржи (нормализованный enum). */
    private MarginMode marginMode;

    /** Сырой режим маржи биржи (cross/isolated). */
    private String externalMarginMode;

    /** Рабочее плечо инструмента; задаётся при создании, не из снапшота. */
    private Integer leverage;

    /** Биржевое значение плеча (сырое, OKX lever). */
    private String externalLeverage;

    /** Плановая нижняя граница истории свечей (UTC мс), общая для всех таймфреймов. */
    private Long plannedCandleStartDate;

    /** Группы свечей по таймфреймам инструмента (1:many). */
    private List<CandleGroup> candleGroups;

    /**
     * Инструмент готов к активации, когда есть группы свечей и все они
     * в {@code ACTIVE} (координация Instrument.Status ↔ CandleGroup.Status,
     * docs/lifecycles/Instrument.md).
     */
    public boolean isReadyForActivation(List<CandleGroup> groups) {
        return CollectionUtils.isNotEmpty(groups) && groups.stream().allMatch(CandleGroup::isActive);
    }

    /** Онбординг-статус инструмента в системе (готовность к торговле). */
    public enum Status {
        CREATED, HOLD, SYNC, CANDLES_LOADING, ACTIVE, CLOSED, ERROR
    }

    /** Нормализованный режим маржи; сырой биржевой — в externalMarginMode. */
    public enum MarginMode {
        ISOLATED, CROSS
    }
}
