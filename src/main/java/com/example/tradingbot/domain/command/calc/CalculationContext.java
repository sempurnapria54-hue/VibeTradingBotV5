package com.example.tradingbot.domain.command.calc;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.domain.model.trade.market_price.MarketPriceData;
import com.example.tradingbot.domain.model.trade.market_structure.MarketStructure;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Objects;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Рабочий runtime-контекст калькуляторов, собираемый
 * CalculationContextFactory на один рассчитываемый StrategyAction
 * максимально близко ко времени создания команды. RVO, не persisted; не
 * заменяет DealContext. Каталоги настроек ({@code indicatorSettings},
 * {@code marketStructureSettings}) включены, чтобы калькулятор резолвил
 * готовые значения по «мягкому» ключу (indicatorKey / structureKey).
 * Риск-настройки сделки берутся из {@code strategyDetail}
 * (riskPerTradePercent). См. docs/components/models/CalculationContext.md.
 */
@Value
@Builder
public class CalculationContext {

    /**
     * Сделка, для которой выполняется расчёт.
     */
    Deal deal;

    /**
     * Инструмент сделки.
     */
    Instrument instrument;

    /**
     * Pinned StrategyDetail сделки.
     */
    StrategyDetail strategyDetail;

    /**
     * Действие, для которого считаются параметры.
     */
    StrategyAction action;

    /**
     * Внешние правила инструмента (tick/lot/min size, ctVal, max leverage, state).
     */
    InstrumentExternalRules instrumentExternalRules;

    /**
     * Runtime-цены (REST ticker перед расчётом).
     */
    MarketPriceData marketPriceData;

    /**
     * Готовые значения индикаторов стратегии.
     */
    List<IndicatorValue> indicatorValues;

    /**
     * Готовые структуры рынка стратегии.
     */
    List<MarketStructure> marketStructures;

    /**
     * Актуальная фаза рынка, если нужна.
     */
    MarketPhase marketPhase;

    /**
     * Persisted snapshot баланса для sizing и подготовки risk-policy.
     */
    BalanceContainer balanceContainer;

    /**
     * Активная позиция, если открыта.
     */
    Position activePosition;

    /**
     * Entry order, если уже создан.
     */
    Order entryOrder;

    /**
     * Направление стратегии (LONG/SHORT).
     */
    StrategyTradeDirection strategyDirection;

    /**
     * Каталог настроек индикаторов стратегии (резолв значения по ключу).
     */
    List<StrategyIndicatorSetting> indicatorSettings;

    /**
     * Каталог настроек структуры рынка стратегии (резолв структуры по ключу).
     */
    List<StrategyMarketStructureSetting> marketStructureSettings;

    /**
     * Готовое значение индикатора по «мягкому» ключу настройки; null — нет.
     */
    public IndicatorValue findIndicatorValueByKey(String key) {
        if (isBlank(key) || isEmpty(indicatorSettings) || isEmpty(indicatorValues)) {
            return null;
        }
        Long settingId = indicatorSettings.stream()
                                          .filter(setting -> Objects.equals(setting.getKey(), key))
                                          .map(StrategyIndicatorSetting::getId)
                                          .findFirst()
                                          .orElse(null);
        if (isNull(settingId)) {
            return null;
        }
        return indicatorValues.stream()
                              .filter(value -> Objects.equals(value.getStrategyIndicatorSettingId(), settingId))
                              .findFirst()
                              .orElse(null);
    }

    /**
     * Готовая структура рынка по «мягкому» ключу настройки; null — нет.
     */
    public MarketStructure findMarketStructureByKey(String key) {
        if (isBlank(key) || isEmpty(marketStructureSettings) || isEmpty(marketStructures)) {
            return null;
        }
        Long settingId = marketStructureSettings.stream()
                                                .filter(setting -> Objects.equals(setting.getKey(), key))
                                                .map(StrategyMarketStructureSetting::getId)
                                                .findFirst()
                                                .orElse(null);
        if (isNull(settingId)) {
            return null;
        }
        return marketStructures.stream()
                               .filter(structure -> Objects.equals(structure.getStrategyMarketStructureSettingId(),
                                                                   settingId))
                               .findFirst()
                               .orElse(null);
    }
}
