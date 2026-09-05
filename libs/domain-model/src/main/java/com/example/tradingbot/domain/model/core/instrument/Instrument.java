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

    /**
     * Код площадки, которой принадлежит инструмент ({@code OKX},
     * {@code BYBIT}).
     *
     * <p><b>Код, а не числовой идентификатор</b>, потому что справочник
     * площадок принадлежит {@code auth}, а внешнего ключа через границу
     * сервиса не бывает (docs/architecture/tenant-and-exchange.md
     * §«Три сущности вместо одной»). Той же осью площадку называет реестр
     * биржевых счетов.
     */
    private String exchangeCode;

    /**
     * Внутренний ID биржи (Exchange.id) — <b>носитель монолита</b>.
     *
     * <p><b>Названный долг.</b> Числовой ключ площадки существует только
     * в схеме донора; сервисы адресуют площадку кодом. Поле живёт, пока
     * жив донор, и market-data его НЕ пишет: у него таблицы площадок нет.
     * Условие снятия — полная модель площадки
     * (.claude/work/backlog.md §«Exchange модель/lifecycle»), вместе с
     * которой поле уходит из этой формы.
     */
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

    /**
     * Расчётная валюта инструмента (OKX settleCcy) — авторитет валюты
     * риска и валюты результата сделки; операнд ветки чужой валюты на
     * записи движения (docs/models/domain/core/Instrument.md). Пишет
     * тропа синка спецификации; пусто = валюта не резолвилась — курс не
     * ищется вовсе (ступень 0 лестницы огрубления,
     * docs/components/RefreshBillsExecutor.md).
     */
    private String externalSettlementCurrency;

    /**
     * Базовая валюта инструмента (OKX baseCcy).
     *
     * <p>Вместе с котировочной образует имя индекса у площадки, по
     * которому резолвится индексная цена среза
     * (docs/models/domain/other/MarketTicker.md).
     */
    private String externalBaseCurrency;

    /** Котировочная валюта инструмента (OKX quoteCcy); вторая половина имени индекса. */
    private String externalQuoteCurrency;

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

    /** Инструмент заморожен реактивным safety-холдом (уровень 3, docs/rules/instrument-hold.md). */
    public Boolean isTradeBlocked() {
        return Objects.equals(status, Status.TRADE_BLOCKED);
    }

    /**
     * По инструменту стои́т safety-ступень с блок-сетом — мягкая
     * ({@code ENTRY_BLOCKED}) либо жёсткая ({@code TRADE_BLOCKED}).
     *
     * <p>Это операнд гейта повтора отказавшей надобности
     * (docs/rules/strategy-step-once-per-episode.md §«Надобность после
     * исчерпания бюджета — гейт по стоящей ступени»). Область — ровно две
     * ступени: рабочее состояние и онбординговые статусы значения не дают,
     * а БИРЖЕВОЙ радиус в неё не входит вовсе — под мягкой биржевой
     * ступенью живые сделки сопровождаются полностью
     * (docs/rules/exchange-hold.md), и морозить повтор там значило бы
     * отменять то, что ступень обязана сохранять.
     */
    public Boolean hasStandingSafetyRung() {
        return Objects.equals(status, Status.ENTRY_BLOCKED)
                || Objects.equals(status, Status.TRADE_BLOCKED);
    }

    /** Онбординг-статус инструмента в системе (готовность к торговле). */
    public enum Status {

        /** Инструмент заведён, онбординг не начинался. */
        CREATED,

        /** Инструмент придержан (не вовлекается в онбординг). */
        HOLD,

        /**
         * Торговля по инструменту заморожена реактивным safety-холдом
         * (уровень 3 error-градации, docs/rules/instrument-hold.md): новые
         * сделки/наращивание запрещены, safety/read разрешены. Вход — только
         * из {@code ACTIVE} по аварии; снятие — вручную в {@code ACTIVE}.
         * Отдельный смысл от онбординг-{@code HOLD}.
         */
        TRADE_BLOCKED,

        /**
         * Новые входы по инструменту запрещены МЯГКОЙ safety-ступенью
         * (docs/rules/instrument-hold.md §Enforcement): живые сделки не
         * сворачиваются и доживают под своей защитой, но наращивание и
         * ОСЛАБЛЕНИЕ защиты блокируются. Отличается от TRADE_BLOCKED тем,
         * что принятый риск покрыт — рвать его нечем.
         */
        ENTRY_BLOCKED,

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
