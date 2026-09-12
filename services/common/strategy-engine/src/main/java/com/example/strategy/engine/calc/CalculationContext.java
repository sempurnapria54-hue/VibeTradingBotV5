package com.example.strategy.engine.calc;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Рабочий контекст калькуляторов на ОДНО рассчитываемое действие
 * (docs/components/models/CalculationContext.md). Неизменяемый
 * runtime-объект, не хранится; контекст прохода он не заменяет.
 *
 * <p><b>Операнды приезжают вызовом, а не чтением базы.</b> Расчётный слой
 * живёт в общем артефакте, и его пригодность для бэктеста стои́т ровно на
 * этом: библиотека к базе не ходит
 * (docs/architecture/services.md §«Что в библиотеку НЕ уезжает»). Отсюда
 * операндами, а не выводом, приезжают база риска и сумма уже поставленных
 * ступеней защитного набора — обе резолвятся у того, кто держит
 * персистентность.
 */
@Value
@Builder
public class CalculationContext {

    /** Сделка, для которой идёт расчёт. */
    Deal deal;

    /**
     * Транш, которому принадлежит действие, — носитель ЭКСПОЗИЦИИ, от
     * которой берётся доля выхода и размер защитной ступени
     * (docs/spec/order-sizing.json, операнд {@code tranche.exposure}).
     *
     * <p><b>Нетто-размер эпизода экспозицию не заменяет:</b> на сетке он
     * относится к нескольким траншам сразу, и доля, взятая от него,
     * уводила бы экспозицию транша в минус — при том, что сумма по сделке
     * сходится и сверка такого класса не ловит.
     */
    DealTranche dealTranche;

    /** Инструмент сделки. */
    Instrument instrument;

    /** Закреплённая деталь стратегии. */
    StrategyDetail strategyDetail;

    /** Действие, для которого считаются параметры. */
    StrategyAction action;

    /** Справочные правила инструмента с гидрированной ставкой комиссии. */
    InstrumentExternalRules instrumentExternalRules;

    /** Runtime-цены инструмента. */
    MarketPriceData marketPriceData;

    /**
     * Готовые значения индикаторов по <b>авторскому имени операнда</b>.
     *
     * <p><b>Ключ — имя, а не идентичность вычисления.</b> Идентичность есть
     * номенклатура владельца рыночных данных, и связать её с объявлением
     * стратегии может только тот, кто это соответствие держит, — читатель
     * (docs/models/domain/other/IndicatorValue.md §«Ключевание —
     * идентичностью вычисления»). Прежняя редакция резолвила ключ
     * равенством «идентичность значения == идентификатор строки
     * объявления»: в монолите оно держалось, потому что одна настройка и
     * была одним вычислением, а в сервисной конструкции это две разные
     * номенклатуры — резолв возвращал бы пустоту ВСЕГДА, и цена по уровню
     * индикатора считалась бы на недоступном операнде.
     */
    Map<String, IndicatorValue> indicatorValues;

    /** Готовые структуры рынка по авторскому имени операнда — тот же довод. */
    Map<String, MarketStructure> marketStructures;

    /** Актуальная фаза рынка. */
    MarketPhase marketPhase;

    /**
     * <b>База риска</b> — делитель поактного потолка при сайзинге
     * (docs/components/SizeCalculator.md). Резолв «снимок сделки, а при
     * его отсутствии живая база счёта» живёт в доме-спеке
     * docs/spec/risk-limits.json (величина {@code base}) и здесь не
     * переписывается: контекст получает уже разрешённое число.
     *
     * <p><b>Снимок средств базой не является</b>
     * (docs/models/domain/core/BalanceContainer.md): доступный остаток
     * расчётной валюты — операнд ДВИЖЕНИЯ базы, а не сама база, и сайзинг
     * по нему считал бы не тот потолок.
     */
    BigDecimal riskBase;

    /**
     * Сумма размеров уже поставленных ступеней защитного набора этого
     * шага (docs/spec/order-sizing.json, операнд
     * {@code ladder.previousStepsTotal}). Селектор набора и источник суммы
     * — docs/rules/live-risk-protection.md §«Размер ступени лестницы».
     *
     * <p>Пусто законно только там, где набор состоит из одной ступени: у
     * лестницы из нескольких ступеней пустота читалась бы нулём и отдала
     * бы последней ступени всю экспозицию — благоприятное умолчание,
     * запрещённое docs/rules/absent-value-semantics.md. Поэтому
     * калькулятор на такой пустоте отказывает, а не подставляет ноль.
     */
    BigDecimal ladderPreviousStepsTotal;

    /** Живой эпизод позиции, если открыт. */
    Position activePosition;

    /** Входная заявка, если создана. */
    Order entryOrder;

    /** Направление стратегии. */
    StrategyTradeDirection strategyDirection;

    /** Готовое значение индикатора по авторскому имени операнда; пусто — вход недоступен. */
    public IndicatorValue findIndicatorValueByKey(String key) {
        return isBlank(key) || isNull(indicatorValues) ? null : indicatorValues.get(key);
    }

    /** Готовая структура рынка по авторскому имени операнда; пусто — вход недоступен. */
    public MarketStructure findMarketStructureByKey(String key) {
        return isBlank(key) || isNull(marketStructures) ? null : marketStructures.get(key);
    }
}
