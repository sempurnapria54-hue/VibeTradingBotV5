package com.example.tradingcore.box;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.PhaseEntryPolicy;
import com.example.tradingbot.domain.model.aggregate.strategy.Strategy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStep;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyStepType;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyActionType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossCalculationType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.AtrParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.Destiny;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EfficiencyRatioParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyIndicatorSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketStructureSetting;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import com.example.tradingbot.message.StrategyActivatedMessage;
import com.example.tradingbot.message.StrategyLifecycleMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Определения стратегий — вход ящика со стороны владельца определений.
 *
 * <p><b>Собирается ДОМЕННОЕ дерево, а не зеркало формы провода.</b>
 * Содержимое события активации несёт саму доменную модель
 * ({@code StrategyActivatedMessage}), и второго описания у неё нет
 * (.claude/rules/carrier-levels.md); кейс, собравший тело руками, мерил
 * бы собственную выдумку вместо формы, которую кладёт производитель.
 *
 * <p><b>Сериализация идёт тем же способом, что у производителя:</b>
 * снимок пишется целиком, моменты — строками ISO, а не числами. Разбор на
 * другой стороне делает сервис, и расхождение способов было бы
 * расхождением НАШИМ, а не его.
 *
 * <p><b>Объявления каталога несут пустую идентичность вычисления
 * намеренно.</b> Её пишет потребитель — то есть ядро, тиком объявления
 * потребности (docs/architecture/market-data-collection.md §«Как
 * потребность доходит до сбора»); заполненная автором, она отняла бы у
 * группы {@code B10} её предмет.
 */
final class Definitions {

    /** Ключ индикаторного объявления, на который ссылается структура как на ATR-вход. */
    static final String ATR_KEY = "atr";

    /** Ключ индикаторного объявления, на который ссылается структура как на ER-вход. */
    static final String EFFICIENCY_RATIO_KEY = "er";

    /** Ключ структурного объявления. */
    static final String STRUCTURE_KEY = "ms";

    /** Ключ входного объявления детали: оно у торгуемой детали одно. */
    static final String ENTRY_TRANCHE_KEY = "entry";

    /** Ключ ВТОРОГО действия входного пакета: им наблюдается остановка пакета. */
    static final String SECOND_ACTION_KEY = "entry-order-second";

    /**
     * Рабочая дистанция встроенного стопа, процент цены входа: уровень
     * дальше round-trip комиссии, и преконтроль его не отвергает.
     */
    static final String WORKING_STOP_PERCENTS = "2";

    /**
     * Авторское имя операнда, которого владелец данных не отдаст: объявления
     * каталога под него у определения нет вовсе.
     */
    static final String UNCOVERED_KEY = "never-declared";

    /**
     * Срок свежести рассчитанного значения.
     *
     * <p>Назван у каждого объявления намеренно: колонка обязательна по
     * существу — объявление без срока годности сделало бы гейт свежести
     * шага невыводимым.
     */
    private static final Duration EXPIRATION = Duration.ofMinutes(5);

    /** Актор перехода: у активации есть ручная тропа. */
    private static final String ACTOR = "USER";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Definitions() {
    }

    /**
     * Определение с полным набором объявлений каталога: два индикатора и
     * структура, ссылающаяся на оба своими входами.
     *
     * @param internalId   идентичность определения
     * @param accountId    идентичность биржевого счёта
     * @param instrumentId идентичность инструмента
     */
    static Strategy withComputations(String internalId, String accountId, String instrumentId) {
        Strategy strategy = root(internalId, accountId, instrumentId);
        strategy.setIndicatorSettings(new ArrayList<>(List.of(
                indicator(ATR_KEY, IndicatorValue.Type.ATR, atrParams(TimeFrame.ONE_HOUR, 100)),
                indicator(EFFICIENCY_RATIO_KEY, IndicatorValue.Type.EFFICIENCY_RATIO,
                        efficiencyRatioParams(TimeFrame.ONE_HOUR, 50)))));
        strategy.setMarketStructureSettings(new ArrayList<>(List.of(structure(TimeFrame.ONE_HOUR))));
        return strategy;
    }

    /**
     * Определение, у которого ТОЛЬКО индикаторные объявления: два одного
     * таймфрейма с разной объявленной глубиной и одно без объявленной
     * глубины вовсе.
     *
     * <p>Структуры здесь нет намеренно: она объявляет свою глубину на том
     * же таймфрейме, и сведение глубин перестало бы быть наблюдаемым по
     * двум названным объявлениям.
     */
    static Strategy withSeriesDepths(String internalId, String accountId, String instrumentId) {
        Strategy strategy = root(internalId, accountId, instrumentId);
        strategy.setIndicatorSettings(new ArrayList<>(List.of(
                indicator("shallow", IndicatorValue.Type.ATR, atrParams(TimeFrame.ONE_HOUR, 100)),
                indicator("deep", IndicatorValue.Type.EFFICIENCY_RATIO,
                        efficiencyRatioParams(TimeFrame.ONE_HOUR, 300)),
                indicator("undeclared", IndicatorValue.Type.ATR, atrParams(TimeFrame.FOUR_HOURS, null)))));
        return strategy;
    }

    /**
     * Определение с одной ДЕТАЛЬЮ фазы и без объявлений каталога.
     *
     * <p>Деталь нужна там, где предмет клетки — закрепление узла дерева у
     * живой сделки: сделка ссылается числовым ключом именно на деталь, и
     * переписанное дерево эту ссылку оборвало бы.
     *
     * <p><b>Поддерева у детали нет намеренно:</b> транши и шаги предмет
     * приёма не меняют, а собранные здесь заводили бы у кейса второе
     * описание дерева, которого требует уровень 2.
     *
     * @param internalId   идентичность определения
     * @param accountId    идентичность биржевого счёта
     * @param instrumentId идентичность инструмента
     */
    static Strategy withDetail(String internalId, String accountId, String instrumentId) {
        Strategy strategy = root(internalId, accountId, instrumentId);
        StrategyDetail detail = new StrategyDetail();
        detail.setMarketPhaseType(MarketPhase.Type.BULL_TREND);
        detail.setPhaseEntryPolicy(PhaseEntryPolicy.FOLLOW_PHASE);
        strategy.setDetails(new ArrayList<>(List.of(detail)));
        return strategy;
    }

    /**
     * Определение ОТБОРА ВХОДА: деталь названной фазы с единственным
     * входным шагом, чьё условие спрашивает тип фазы.
     *
     * <p><b>Почему условие именно фазовое.</b> Фаза приезжает той же
     * связкой фич, что и раскладки операндов, и потому покрыта ровно
     * тогда, когда владелец её отдал: условие на ней отделяет «условие
     * ложно» от «операнд недоступен», а условие на индикаторе смешало бы
     * эти два исхода в один.
     *
     * <p><b>Клаузы классификации фазы объявлены, и это не украшение:</b>
     * фазу владелец не классифицирует, пока её не спросили, а спрашивает
     * её ядро ровно по объявленным клаузам
     * ({@code MarketFeatureService#readForEntry}). Определение без клауз
     * получало бы фазу от стаба даром — то есть кейс предъявлял бы ядру
     * ответ, которого сосед в проде не дал бы.
     *
     * @param internalId    идентичность определения
     * @param accountId     идентичность биржевого счёта
     * @param instrumentId  идентичность инструмента
     * @param detailPhase   фаза, под которую объявлена деталь
     * @param requiredPhase фаза, которой условие входа требует
     */
    static Strategy withEntryOnPhase(String internalId, String accountId, String instrumentId,
                                     MarketPhase.Type detailPhase, MarketPhase.Type requiredPhase) {
        return withEntrySteps(internalId, accountId, instrumentId, detailPhase,
                List.of(entryStep("entry-order", StrategyTradeDirection.LONG,
                        phaseCondition(requiredPhase))));
    }

    /**
     * То же определение с ДВУМЯ входными шагами: первый спрашивает
     * индикаторный операнд, которого в раскладке нет, второй — фазу.
     *
     * <p><b>Направления у действий РАЗНЫЕ, и это носитель ожидания.</b>
     * Снаружи процесса «шаг пропущен» наблюдается только тем, чьё
     * действие завело сделку, а различает действия ровно направление:
     * непокрытый шаг объявляет {@code SHORT}, покрытый — {@code LONG}.
     * Одинаковые направления сделали бы клетку зелёной и в том случае,
     * когда вход открыл первый шаг.
     *
     * @param internalId   идентичность определения
     * @param accountId    идентичность биржевого счёта
     * @param instrumentId идентичность инструмента
     * @param phase        фаза детали, она же требуемая вторым шагом
     */
    static Strategy withUncoveredThenPhaseEntry(String internalId, String accountId, String instrumentId,
                                                MarketPhase.Type phase) {
        return withEntrySteps(internalId, accountId, instrumentId, phase,
                List.of(entryStep("uncovered-order", StrategyTradeDirection.SHORT, indicatorCondition()),
                        entryStep("entry-order", StrategyTradeDirection.LONG, phaseCondition(phase))));
    }

    /**
     * То же определение отбора входа с НАЗВАННЫМ числом уровней сетки:
     * материализация эагерна, и уровней у сделки будет ровно столько.
     *
     * <p>Больше одного уровня нужно клеткам, чьё ожидание утверждает о
     * траншах во множественном числе («статусы траншей не меняются»): на
     * единственном транше такое ожидание было бы утверждением об одном
     * объекте, а предмет его — популяция.
     *
     * @param internalId   идентичность определения
     * @param accountId    идентичность биржевого счёта
     * @param instrumentId идентичность инструмента
     * @param phase        фаза детали, она же требуемая условием входа
     * @param levelCount   сколько уровней объявляет входное объявление
     */
    static Strategy withEntryLevelsOnPhase(String internalId, String accountId, String instrumentId,
                                           MarketPhase.Type phase, Integer levelCount) {
        return withEntrySteps(internalId, accountId, instrumentId, phase,
                List.of(entryStep("entry-order", StrategyTradeDirection.LONG, phaseCondition(phase))),
                levelCount);
    }

    /**
     * Определение, чей входной шаг доходит до КОМАНДЫ ПЛОЩАДКЕ: та же
     * фазовая деталь отбора входа, но действие объявлено так, что расчёт
     * параметров и преконтроль риска его не отвергают.
     *
     * <p><b>Три объявления здесь несущие, и каждое снимает свой отказ.</b>
     * Встроенная защита даёт уровень остановки, без которого сайзинг
     * отказывает {@code MISSING_STOP_PRICE_FOR_SIZING}: вход без
     * известного худшего выхода не сайзится долей аллокации вовсе
     * (docs/concept.md П1). Доля аллокации — второй кандидат размера, и
     * без неё отказ {@code MISSING_ALLOCATION}. Четыре числа потолков на
     * детали — операнды преконтроля, и незаявленное каждое из них даёт
     * реджект {@code RISK_APPETITE_NOT_CONFIGURED}
     * (docs/rules/risk-policy.md).
     *
     * <p><b>Числа выбраны так, чтобы связывающим оказалась ДОЛЯ, а не
     * потолок риска.</b> Иначе размер зависел бы от ставки комиссии и
     * дистанции стопа, и клетка о команде платила бы красным за правку
     * соседнего операнда.
     *
     * @param internalId   идентичность определения
     * @param accountId    идентичность биржевого счёта
     * @param instrumentId идентичность инструмента
     * @param phase        фаза детали, она же требуемая условием входа
     */
    static Strategy withEntryCommandOnPhase(String internalId, String accountId, String instrumentId,
                                            MarketPhase.Type phase) {
        return withEntryCommandOnPhase(internalId, accountId, instrumentId, phase, WORKING_STOP_PERCENTS);
    }

    /**
     * То же определение с НАЗВАННОЙ дистанцией встроенного стопа.
     *
     * <p><b>Дистанция — операнд преконтроля, а не украшение.</b> Уровень
     * ближе якоря, чем round-trip комиссия, отвергается БЕССРОЧНЫМ кодом
     * {@code STOP_DISTANCE_BELOW_FLOOR} (docs/spec/stop-distance.json), и
     * это единственный операнд группы, которым бессрочный вердикт
     * достигается ОДНИМ кодом: потолки риска связаны между собой
     * множителями, и занижение любого из них роняет вместе с бессрочным
     * кодом временный — а бессрочность вердикта есть конъюнкция по его
     * кодам (docs/components/RiskBlockResolver.md).
     *
     * @param stopDistancePercents дистанция встроенного стопа, процент
     *                             цены входа
     */
    static Strategy withEntryCommandOnPhase(String internalId, String accountId, String instrumentId,
                                            MarketPhase.Type phase, String stopDistancePercents) {
        Strategy strategy = withEntrySteps(internalId, accountId, instrumentId, phase,
                List.of(protectedEntryStep(phaseCondition(phase), stopDistancePercents, "entry-order")), 1);
        declareRiskCeilings(strategy.getDetails().getFirst());
        return strategy;
    }

    /**
     * То же определение, чей входной шаг несёт ДВА действия.
     *
     * <p>Пакет шага исполняется по действию за проход
     * (docs/rules/strategy-step-once-per-episode.md §«Пакет исполняется по
     * действию за проход»), и второе действие здесь — наблюдатель того,
     * дошёл ли пакет до него: своя строка исполнения заводится у всякого
     * начатого действия ДО преконтроля
     * ({@code StrategyActionOrchestrator#plan}), поэтому «пакет
     * остановился» и «пакет пошёл дальше» различаются числом строк.
     */
    static Strategy withTwoActionEntryCommandOnPhase(String internalId, String accountId, String instrumentId,
                                                     MarketPhase.Type phase) {
        Strategy strategy = withEntrySteps(internalId, accountId, instrumentId, phase,
                List.of(protectedEntryStep(phaseCondition(phase), WORKING_STOP_PERCENTS,
                        "entry-order", SECOND_ACTION_KEY)), 1);
        declareRiskCeilings(strategy.getDetails().getFirst());
        return strategy;
    }

    /**
     * Потолки риска на закреплённой детали: все четыре, которых требует
     * преконтроль. Незаявленный потолок он отвергает, а не пропускает
     * (docs/processes/risk-evaluation.md).
     */
    private static void declareRiskCeilings(StrategyDetail detail) {
        detail.setRiskPerActionPercent(new BigDecimal("1"));
        detail.setCumulativeRiskPerDealMultiplier(new BigDecimal("3"));
        detail.setStrategySimultaneousRiskPerDealPercent(new BigDecimal("2"));
        detail.setStrategyCatastrophicRiskPerDealMultiplier(new BigDecimal("10"));
        detail.setTargetRiskRewardRatio(new BigDecimal("2"));
    }

    /**
     * Входной шаг, чьи действия создают ногу входа СО встроенной защитой:
     * уровень остановки объявлен процентом от цены входа, триггер — по
     * последней цене.
     *
     * @param condition            условие шага
     * @param stopDistancePercents дистанция встроенного стопа
     * @param actionKeys           ключи действий пакета в порядке объявления
     */
    private static StrategyStep protectedEntryStep(StrategyCondition condition, String stopDistancePercents,
                                                   String... actionKeys) {
        List<StrategyAction> actions = new ArrayList<>();
        for (String actionKey : actionKeys) {
            actions.add(protectedEntryAction(actionKey, stopDistancePercents));
        }
        StrategyStep step = new StrategyStep();
        step.setStepType(StrategyStepType.ENTRY);
        step.setCondition(condition);
        step.setMarketDataExpiredSetting(expiredSetting());
        step.setActions(actions);
        return step;
    }

    /** Одно действие пакета: нога входа со встроенной защитой. */
    private static StrategyAction protectedEntryAction(String actionKey, String stopDistancePercents) {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setKey(actionKey);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setOrderType(Order.Type.ENTRY_ATTACHED_STOP_LOSS);
        action.setDirection(StrategyTradeDirection.LONG);
        action.setAllocationPercents(new BigDecimal("10"));
        // Намерение reduce-only объявлено ЯВНЫМ отрицанием, а не пустотой:
        // инвариант пары требует его непустым у всякой ноги
        // (docs/models/domain/core/Order.md), а предикат транша, ищущий
        // свою ногу входа, читает пустоту как «не отрицание» и ногу
        // отбрасывает — транш тогда не получает своего входного ребра
        // вовсе (находка F3 захода).
        action.setPositionReducingOnly(Boolean.FALSE);
        action.setAttachedProtection(attachedStopLoss(stopDistancePercents));
        return action;
    }

    /** Встроенная защита ноги входа: процент от цены входа по последней цене. */
    private static StrategyAttachedProtectionSettings attachedStopLoss(String distancePercents) {
        StopLossSettings stopLoss = new StopLossSettings();
        stopLoss.setCalculationType(StopLossCalculationType.ENTRY_PRICE_PERCENT);
        stopLoss.setDistancePercents(new BigDecimal(distancePercents));
        stopLoss.setTriggerPriceType(AlgoOrder.TriggerPriceType.LAST);
        return new StrategyAttachedProtectionSettings(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS, stopLoss);
    }

    /** Тело события активации: снимок определения плюс три идентичности радиуса. */
    static String activated(Strategy definition) {
        return write(new StrategyActivatedMessage(definition.getInternalId(),
                definition.getExchangeAccountInternalId(), definition.getInstrumentInternalId(),
                ACTOR, definition));
    }

    /** Тело события деактивации либо удаления: дерева в нём нет. */
    static String lifecycle(Strategy definition) {
        return write(new StrategyLifecycleMessage(definition.getInternalId(),
                definition.getExchangeAccountInternalId(), definition.getInstrumentInternalId(), ACTOR));
    }

    /** Содержимое произвольной формы: вход клеток о неразбираемом теле. */
    static String write(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception failure) {
            throw new IllegalStateException("Содержимое сообщения не собралось", failure);
        }
    }

    /**
     * Определение с одной деталью, одним входным объявлением и названными
     * входными шагами.
     *
     * <p>Уровень у объявления один: сетка входов — предмет уровня 2, а
     * здесь лишние транши добавили бы строк, о которых клетки не
     * утверждают ничего.
     */
    private static Strategy withEntrySteps(String internalId, String accountId, String instrumentId,
                                           MarketPhase.Type detailPhase, List<StrategyStep> entrySteps) {
        return withEntrySteps(internalId, accountId, instrumentId, detailPhase, entrySteps, 1);
    }

    private static Strategy withEntrySteps(String internalId, String accountId, String instrumentId,
                                           MarketPhase.Type detailPhase, List<StrategyStep> entrySteps,
                                           Integer levelCount) {
        Strategy strategy = root(internalId, accountId, instrumentId);
        strategy.setMarketPhaseSetting(phaseSetting(detailPhase));
        strategy.setDetails(new ArrayList<>(List.of(entryDetail(detailPhase, entrySteps, levelCount))));
        return strategy;
    }

    /** Деталь фазы с единственным входным объявлением. */
    private static StrategyDetail entryDetail(MarketPhase.Type phase, List<StrategyStep> entrySteps,
                                              Integer levelCount) {
        StrategyTranche declaration = new StrategyTranche();
        declaration.setKey(ENTRY_TRANCHE_KEY);
        declaration.setLevelCount(levelCount);
        // Шаг сетки объявляется тогда и только тогда, когда уровней больше
        // одного: инвариант схемы читает ровно эту эквивалентность.
        declaration.setLevelStep(levelCount > 1 ? new BigDecimal("0.5") : null);
        Map<DealTranche.Status, List<StrategyStep>> byStatus = new LinkedHashMap<>();
        byStatus.put(DealTranche.Status.PRECHECK, new ArrayList<>(entrySteps));
        declaration.setStepsByStatus(byStatus);
        StrategyDetail detail = new StrategyDetail();
        detail.setMarketPhaseType(phase);
        detail.setPhaseEntryPolicy(policyFor(phase));
        detail.setTranches(new ArrayList<>(List.of(declaration)));
        return detail;
    }

    /**
     * Политика, допускающая вход в этой фазе: деталь, чья политика фазу не
     * допускает, до оценки условий не доходит вовсе
     * ({@code StrategyDetail#allowsEntryFor}).
     */
    private static PhaseEntryPolicy policyFor(MarketPhase.Type phase) {
        return MarketPhase.Type.RANGE.equals(phase) ? PhaseEntryPolicy.GRID : PhaseEntryPolicy.FOLLOW_PHASE;
    }

    /** Входной шаг с условием и одним действием создания входной заявки. */
    private static StrategyStep entryStep(String actionKey, StrategyTradeDirection direction,
                                          StrategyCondition condition) {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setKey(actionKey);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        action.setOrderType(Order.Type.ENTRY);
        action.setDirection(direction);
        StrategyStep step = new StrategyStep();
        step.setStepType(StrategyStepType.ENTRY);
        step.setCondition(condition);
        step.setMarketDataExpiredSetting(expiredSetting());
        step.setActions(new ArrayList<>(List.<StrategyAction>of(action)));
        return step;
    }

    /** Условие «фаза рынка равна названной». */
    private static StrategyCondition phaseCondition(MarketPhase.Type phase) {
        StrategyConditionOperand declared = new StrategyConditionOperand();
        declared.setSourceType(StrategyConditionSourceType.CONSTANT);
        declared.setValueType(ConstantValueType.ENUM);
        declared.setValue(phase.name());
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(StrategyConditionRuleType.MARKET_PHASE_IS);
        rule.setRightOperand(declared);
        return new StrategyCondition(new ArrayList<>(List.of(rule)));
    }

    /**
     * Условие на индикаторном операнде, которого в раскладке фич нет:
     * объявления каталога под этот ключ у определения нет вовсе, и
     * владелец данных такого ключа не отдаст.
     */
    private static StrategyCondition indicatorCondition() {
        StrategyConditionOperand left = new StrategyConditionOperand();
        left.setSourceType(StrategyConditionSourceType.INDICATOR);
        left.setIndicatorKey(UNCOVERED_KEY);
        StrategyConditionOperand right = new StrategyConditionOperand();
        right.setSourceType(StrategyConditionSourceType.CONSTANT);
        right.setValueType(ConstantValueType.NUMBER);
        right.setValue("0");
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setRuleType(StrategyConditionRuleType.INDICATOR_COMPARE);
        rule.setLeftOperand(left);
        rule.setRightOperand(right);
        return new StrategyCondition(new ArrayList<>(List.of(rule)));
    }

    /** Клаузы классификации фазы: одна — на ту фазу, под которую деталь. */
    private static StrategyMarketPhaseSetting phaseSetting(MarketPhase.Type phase) {
        StrategyMarketPhaseRule rule = new StrategyMarketPhaseRule();
        rule.setType(phase);
        rule.setCondition(phaseCondition(phase));
        StrategyMarketPhaseSetting setting = new StrategyMarketPhaseSetting();
        setting.setPhaseRules(new ArrayList<>(List.of(rule)));
        return setting;
    }

    /** Реакция шага на устаревшие данные: колонка обязательна по существу. */
    private static StrategyMarketDataExpiredSetting expiredSetting() {
        StrategyMarketDataExpiredSetting setting = new StrategyMarketDataExpiredSetting();
        setting.setProtectedPositionAction(MarketDataExpiredAction.WAIT);
        setting.setUnprotectedPositionAction(MarketDataExpiredAction.WAIT);
        return setting;
    }

    private static Strategy root(String internalId, String accountId, String instrumentId) {
        Strategy strategy = new Strategy();
        strategy.setInternalId(internalId);
        strategy.setExchangeAccountInternalId(accountId);
        strategy.setInstrumentInternalId(instrumentId);
        strategy.setName("box definition " + internalId);
        strategy.setStatus(Strategy.Status.ACTIVE);
        return strategy;
    }

    private static StrategyIndicatorSetting indicator(String key, IndicatorValue.Type type,
                                                     IndicatorParams params) {
        StrategyIndicatorSetting setting = new StrategyIndicatorSetting();
        setting.setKey(key);
        setting.setIndicatorType(type);
        setting.setParams(params);
        setting.setDestiny(Destiny.ENTRY_CONDITION);
        setting.setExpirationDuration(EXPIRATION);
        return setting;
    }

    private static StrategyMarketStructureSetting structure(TimeFrame timeframe) {
        MarketStructureParams params = new MarketStructureParams();
        params.setLookbackBars(20);
        StrategyMarketStructureSetting setting = new StrategyMarketStructureSetting();
        setting.setKey(STRUCTURE_KEY);
        setting.setTimeframe(timeframe);
        setting.setEfficiencyRatioKey(EFFICIENCY_RATIO_KEY);
        setting.setAtrKey(ATR_KEY);
        setting.setParams(params);
        setting.setDestiny(Destiny.ENTRY_CONDITION);
        setting.setExpirationDuration(EXPIRATION);
        return setting;
    }

    private static IndicatorParams atrParams(TimeFrame timeframe, Integer warmup) {
        AtrParams params = new AtrParams();
        params.setTimeframe(timeframe);
        params.setWarmup(warmup);
        params.setPeriod(14);
        return params;
    }

    private static IndicatorParams efficiencyRatioParams(TimeFrame timeframe, Integer warmup) {
        EfficiencyRatioParams params = new EfficiencyRatioParams();
        params.setTimeframe(timeframe);
        params.setWarmup(warmup);
        params.setPeriod(20);
        return params;
    }
}
