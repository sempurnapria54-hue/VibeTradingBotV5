package com.example.tradingcore.domain.calc;

import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.CalculationError;
import com.example.strategy.engine.calc.CalculationException;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingcore.domain.command.DealActionState;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.TargetEntityType;
import com.example.tradingcore.domain.market.MarketFeatureService;
import com.example.tradingcore.domain.market.MarketFeatures;
import com.example.tradingcore.persistence.service.InstrumentExternalRulesDataService;
import com.example.tradingcore.persistence.service.StrategyDataService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Собирает свежий контекст расчёта на ОДНО действие
 * (docs/components/CalculationContextFactory.md).
 *
 * <p><b>Живёт в ядре, а не в общем артефакте, и это следствие критерия
 * самого артефакта.</b> Все входы фабрики — живое состояние счёта (база
 * риска), граф сделки, гидрированный навес инструмента и свежие рыночные
 * данные, то есть персистентность ядра и вызов к соседу; библиотека к базе
 * не ходит и получает операнды вызовом
 * (docs/architecture/services.md §«Что в библиотеку НЕ уезжает»).
 * Бэктест собирает тот же контекст из своего состояния — совпадать обязан
 * <b>расчёт</b>, а не тропа сборки, и ровно расчёт и лежит в библиотеке.
 *
 * <p><b>Одно рассчитываемое действие — один свежий контекст.</b> Общий
 * контекст на шаг или проход не собирается: после каждого исполненного
 * действия меняются заявки, позиция и цены
 * (docs/components/models/CalculationContext.md §«Scope сборки»). Отсюда
 * и повторное чтение фич: связка, снятая при сборке контекста прохода,
 * к моменту второго действия уже не свежая.
 *
 * <p><b>Тяжёлого фабрика не считает</b> — читает готовые результаты.
 */
@Service
@RequiredArgsConstructor
public class CalculationContextFactory {

    private static final String MISSING_CALCULATION_INPUT = "MISSING_CALCULATION_INPUT";

    private final StrategyDataService strategyDataService;
    private final MarketFeatureService marketFeatureService;
    private final InstrumentExternalRulesDataService rulesDataService;

    /**
     * Контекст расчёта одного действия транша.
     *
     * <p>Структурно неполный вход — <b>контролируемая</b> ошибка расчёта,
     * а не контекст с пустотами: калькулятор на пустом обязательном
     * операнде дал бы неожиданное исключение там, где потребителю
     * причитается названный код
     * (docs/components/models/CalculationError.md). Пустоту отдельных
     * операндов фабрика при этом не лечит — каждый из них отвергает свой
     * калькулятор своим кодом.
     */
    public CalculationContext build(DealContext dealContext, StrategyAction action, DealTranche tranche) {
        requirePresent(dealContext, "deal context");
        requirePresent(action, "strategy action");
        requirePresent(dealContext.getDeal(), "deal");
        requirePresent(dealContext.getInstrument(), "instrument");
        MarketFeatures features = freshFeatures(dealContext);
        return CalculationContext.builder()
                .deal(dealContext.getDeal())
                .dealTranche(tranche)
                .instrument(dealContext.getInstrument())
                .strategyDetail(dealContext.getStrategyDetail())
                .action(action)
                .instrumentExternalRules(rules(dealContext))
                .marketPriceData(features.getMarketPriceData())
                .indicatorValues(features.getLatestIndicators())
                .marketStructures(features.getStructures())
                .marketPhase(features.getMarketPhase())
                .riskBase(dealContext.riskBase())
                .ladderPreviousStepsTotal(ladderPreviousStepsTotal(dealContext, action, tranche))
                .activePosition(dealContext.getDeal().livePosition())
                .entryOrder(isNull(tranche) ? null : tranche.entryOrder())
                .strategyDirection(dealContext.getDeal().getDirection())
                .build();
    }

    /**
     * Свежие фичи момента: снимаются заново на каждое действие, а не
     * берутся из контекста прохода — контекст расчёта обязан быть собран
     * максимально близко ко времени создания команды. У сделки без
     * закреплённой детали фич не бывает: объявлений, по которым их
     * читать, у неё нет.
     */
    private MarketFeatures freshFeatures(DealContext dealContext) {
        StrategyDetail detail = dealContext.getStrategyDetail();
        if (isNull(detail)) {
            return MarketFeatures.builder().build();
        }
        Strategy owner = strategyDataService.findOwnerOfDetailWithSettings(detail.getId()).orElse(null);
        return isNull(owner)
                ? MarketFeatures.builder().build()
                : marketFeatureService.readForCalculation(owner, dealContext.getInstrument());
    }

    /**
     * Справочные правила инструмента с гидрированной ставкой комиссии;
     * пусто — проекции нет либо навес не материализован, и действие
     * отвергает калькулятор своим кодом.
     */
    private InstrumentExternalRules rules(DealContext dealContext) {
        return rulesDataService.findByInstrumentId(dealContext.getInstrument().getId(),
                        dealContext.getDeal().getExchangeAccountId())
                .orElse(null);
    }

    /**
     * Сумма размеров уже поставленных ступеней защитного набора этого
     * шага (docs/spec/order-sizing.json, операнд
     * {@code ladder.previousStepsTotal}).
     *
     * <p><b>Набор — защитные создающие действия ШАГА</b>, а сумма читается
     * по строкам их исполнения: строка несёт цель, а цель — размер
     * (docs/rules/live-risk-protection.md §«Размер ступени лестницы»).
     *
     * <p><b>Пусто у набора из одной ступени, и это не то же, что ноль.</b>
     * Одной ступени вся экспозиция причитается по построению, и операнд ей
     * не нужен; у лестницы из нескольких пустота читалась бы нулём и
     * отдала бы последней ступени всю экспозицию — благоприятное
     * умолчание, запрещённое docs/rules/absent-value-semantics.md.
     * Поэтому здесь пусто ровно у одиночной, а у лестницы возвращается
     * посчитанная сумма, в том числе <b>ноль</b>, когда ни одна ступень
     * ещё не поставлена.
     */
    private BigDecimal ladderPreviousStepsTotal(DealContext dealContext, StrategyAction action,
                                                DealTranche tranche) {
        StrategyDetail detail = dealContext.getStrategyDetail();
        if (isNull(detail)) {
            return null;
        }
        StrategyStep step = detail.stepOf(action);
        if (isNull(step)) {
            return null;
        }
        List<StrategyAlgoOrderAction> ladder = step.protectionLadderSteps();
        if (ladder.size() < 2) {
            return null;
        }
        return ladder.stream()
                .filter(declared -> isFalse(Objects.equals(declared.getId(), action.getId())))
                .map(declared -> placedSize(dealContext, declared, tranche))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Размер уже поставленной ступени: строка исполнения объявления несёт
     * цель, а целевая условная заявка — размер. Пусто — ступень ещё не
     * ставилась, и её вклад в сумму нулевой.
     */
    private BigDecimal placedSize(DealContext dealContext, StrategyAlgoOrderAction declared, DealTranche tranche) {
        DealActionState state = dealContext.actionState(declared.getId(), tranche).orElse(null);
        if (isNull(state) || isFalse(Objects.equals(TargetEntityType.ALGO_ORDER, state.getTargetEntityType()))) {
            return null;
        }
        List<AlgoOrder> placed = isNull(tranche) ? List.of() : tranche.getAlgoOrders();
        return emptyIfNull(placed).stream()
                .filter(algoOrder -> Objects.equals(algoOrder.getId(), state.getTargetEntityId()))
                .map(AlgoOrder::getSize)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void requirePresent(Object operand, String name) {
        if (isNull(operand)) {
            throw new CalculationException(CalculationError.permanent(MISSING_CALCULATION_INPUT,
                    "Calculation context cannot be assembled: " + name + " is absent"));
        }
    }
}
