package com.example.tradingbot.domain.model.core.instrument;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import com.example.tradingbot.domain.model.Auditable;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    /** Инструмент в стадии загрузки свечей. */
    public Boolean isCandleLoading() {
        return Objects.equals(status, Status.CANDLES_LOADING);
    }

    /**
     * Инструмент готов к активации: есть группы свечей и все они в
     * {@code ACTIVE} (координация Instrument.Status ↔ CandleGroup.Status,
     * docs/lifecycles/Instrument.md). Проверяет собственные
     * {@code candleGroups} — для активации их грузят join fetch'ем.
     */
    public Boolean isReadyForActivation() {
        return isNotEmpty(candleGroups) && candleGroups.stream().allMatch(CandleGroup::isActive);
    }

    /** Онбординг-статус инструмента в системе (готовность к торговле). */
    public enum Status {

        /** Инструмент заведён, онбординг не начинался. */
        CREATED,

        /** Инструмент придержан (не вовлекается в онбординг). */
        HOLD,

        /** Идёт синхронизация спецификации с биржей. */
        SYNC,

        /** Идёт загрузка свечной истории под таймфреймы. */
        CANDLES_LOADING,

        /** Инструмент готов к торговле (все группы свечей активны). */
        ACTIVE,

        /** Инструмент выведен из использования. */
        CLOSED,

        /** Ошибка онбординга. */
        ERROR
    }

    /** Нормализованный режим маржи; сырой биржевой — в externalMarginMode. */
    public enum MarginMode {

        /** Изолированная маржа. */
        ISOLATED,

        /** Кросс-маржа. */
        CROSS
    }
}
