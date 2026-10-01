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
     * Снятие поля названо вместе с формой монолита {@code Exchange}
     * (.claude/work/backlog.md §«Форма монолита Exchange и
     * Instrument.exchangeId в общей библиотеке без читателя»).
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

    /**
     * Онбординг-статус инструмента в системе (писатель {@code market-data}).
     *
     * <p><b>Перечней два, и делит их писатель:</b> ступень пишет ядро,
     * каталог — {@code market-data}, и одна колонка получала бы двух
     * писателей в двух базах. Здесь — только онбординг; ступени живут в
     * {@link SafetyRung} (docs/models/domain/core/Instrument.md
     * §«Перечней два, и делит их писатель»).
     */
    public enum Status {

        /**
         * Инструмент заведён, онбординг не начинался. <b>Писателя у значения
         * нет, и оно недостижимо:</b> каталог заводит строку сразу в
         * {@link #SYNC} (docs/models/domain/core/Instrument.md §Енумы;
         * docs/lifecycles/Instrument.md).
         */
        CREATED,

        /**
         * Инструмент придержан (не вовлекается в онбординг). <b>Писателя у
         * значения нет, и оно недостижимо:</b> придерживать у онбординга
         * нечего (docs/lifecycles/Instrument.md §«Периферийные статусы»).
         */
        HOLD,

        /** Идёт синхронизация спецификации с биржей. */
        SYNC,

        /** Идёт загрузка свечной истории под таймфреймы. */
        CANDLES_LOADING,

        /** Инструмент готов к торговле (все группы свечей активны). */
        ACTIVE,

        /**
         * Инструмент ушёл из листинга площадки. <b>Писателя у значения нет до
         * фазы 6:</b> торговлю по ушедшему инструменту останавливает гейт
         * свежести (docs/lifecycles/Instrument.md §«Периферийные статусы»).
         */
        CLOSED,

        /**
         * Ошибка онбординга. <b>Писателя у значения нет, и оно недостижимо:</b>
         * застрявший онбординг выражен терминальным {@code ERROR} группы свечей
         * (docs/lifecycles/Instrument.md §«Периферийные статусы»).
         */
        ERROR
    }

    /**
     * Ступень лестницы инструмента (писатель {@code trading-core}).
     *
     * <p><b>Стои́т она на ПАРЕ «счёт, инструмент», а не на инструменте:</b>
     * инструмент принадлежит площадке, и ступень, поднятая отказами
     * одного счёта, не описывает состояние другого. Носитель —
     * {@code account_instrument_states} базы ядра
     * (docs/models/domain/core/Instrument.md §«Ступень и настройки счёта
     * на инструменте»).
     *
     * <p>Семантика ступеней и условия входа живут в
     * docs/rules/instrument-hold.md и здесь не пересказываются.
     */
    public enum SafetyRung {

        /** Ступени нет: рабочее состояние. */
        ACTIVE,

        /**
         * Мягкий запрет новых входов: инструмент выпадает из выборки
         * сканера входа, живые сделки не сворачиваются и доживают под
         * своей защитой.
         */
        ENTRY_BLOCKED,

        /**
         * Холд с kill-switch: инструмент выпадает из сканера, активные
         * сделки уводятся в ошибку с последующим снятием живого риска —
         * непрерывно, пока ступень стои́т.
         */
        TRADE_BLOCKED;

        /**
         * Ранг ступени: 2 — сворачивание, 1 — мягкая, 0 — ступени нет
         * (docs/spec/manual-halt.json, величина {@code rungRankBefore}).
         *
         * <p><b>Ранг и есть механизм монотонности</b> (дом —
         * docs/rules/exchange-hold.md §«Границы и эскалация»): подъём
         * применяется, только если запрошенная ступень СТРОГО выше
         * стоящей, поэтому запрос слабее стоящей поглощается, а автоматика
         * жёсткую ступень не понижает никогда. Порядок объявления значений
         * операндом не служит: сравнение по {@code ordinal()} молча
         * поехало бы при вставке значения в середину перечня.
         */
        public Integer rank() {
            return switch (this) {
                case ACTIVE -> 0;
                case ENTRY_BLOCKED -> 1;
                case TRADE_BLOCKED -> 2;
            };
        }
    }

    /** Нормализованный режим маржи; сырой биржевой — в externalMarginMode. */
    public enum MarginMode {

        /** Изолированная маржа. */
        ISOLATED,

        /** Кросс-маржа. */
        CROSS
    }
}
