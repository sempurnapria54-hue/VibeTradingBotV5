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
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAlgoOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossCalculationType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StopLossSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyAttachedProtectionSettings;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyOrderAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPositionAction;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.aggregate.strategy.action.TrailingSettings;
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

    /** Ключ второго входного объявления: транш, у которого шагов сопровождения нет. */
    static final String SECOND_TRANCHE_KEY = "second";

    /** Ключ ВТОРОГО действия входного пакета: им наблюдается остановка пакета. */
    static final String SECOND_ACTION_KEY = "entry-order-second";

    /**
     * Рабочая дистанция встроенного стопа, процент цены входа: уровень
     * дальше round-trip комиссии, и преконтроль его не отвергает.
     */
    static final String WORKING_STOP_PERCENTS = "2";

    /** Буфер структурного стопа, процент БАЗЫ: отступ от уровня раскладки. */
    private static final String STRUCTURE_BUFFER_PERCENTS = "1";

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
     * То же определение с НАЗВАННЫМ числом уровней сетки: транш на уровень,
     * и вход каждого доходит до команды.
     *
     * <p>Больше одного транша нужно клеткам, чьё предусловие — популяция
     * траншей одной сделки: у одного вход налит, у другого живая входная
     * заявка; выходит один из двух; каждый несёт свою команду.
     *
     * @param levelCount сколько уровней объявляет входное объявление
     */
    static Strategy withEntryCommandLevelsOnPhase(String internalId, String accountId, String instrumentId,
                                                  MarketPhase.Type phase, Integer levelCount) {
        Strategy strategy = withEntrySteps(internalId, accountId, instrumentId, phase,
                List.of(protectedEntryStep(phaseCondition(phase), WORKING_STOP_PERCENTS, "entry-order")),
                levelCount);
        declareRiskCeilings(strategy.getDetails().getFirst());
        return strategy;
    }

    /**
     * То же определение, чей встроенный стоп считается ОТ РЫНОЧНОЙ
     * СТРУКТУРЫ: база — свинг-минимум раскладки, буфер — процент от неё.
     *
     * <p><b>Сторона уровня становится операндом ВХОДА, а не объявления.</b>
     * У стопа процентом от цены входа сторона предрешена построением —
     * уровень всегда ниже якоря у длинной стороны; у структурного стопа
     * её определяет цена, пришедшая от владельца данных, и потому
     * прибыльная сторона выражается раскладкой, а не правкой стратегии
     * ({@code Feed#featuresWithStructure}).
     *
     * <p><b>Объявления каталога названы все три</b> — структура и два её
     * входа, — потому что объявление структуры ссылается на них ключами:
     * определение, просящее структуру без её входов, владелец данных
     * рассчитать не может.
     *
     * @param internalId   идентичность определения
     * @param accountId    идентичность биржевого счёта
     * @param instrumentId идентичность инструмента
     * @param phase        фаза, которую требует условие входа
     */
    static Strategy withStructureStopEntryCommandOnPhase(String internalId, String accountId,
                                                         String instrumentId, MarketPhase.Type phase) {
        Strategy strategy = withEntrySteps(internalId, accountId, instrumentId, phase,
                List.of(protectedEntryStep(phaseCondition(phase), structureStopLoss(), "entry-order")), 1);
        strategy.setIndicatorSettings(new ArrayList<>(List.of(
                indicator(ATR_KEY, IndicatorValue.Type.ATR, atrParams(TimeFrame.ONE_HOUR, 100)),
                indicator(EFFICIENCY_RATIO_KEY, IndicatorValue.Type.EFFICIENCY_RATIO,
                        efficiencyRatioParams(TimeFrame.ONE_HOUR, 50)))));
        strategy.setMarketStructureSettings(new ArrayList<>(
                List.of(structure(TimeFrame.ONE_HOUR, Destiny.PROTECTION))));
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
     * Определение ЖИВОЙ СДЕЛКИ с шагами СОПРОВОЖДЕНИЯ: вход тот же, что у
     * {@link #withEntryCommandOnPhase}, а на статусе {@code MANAGING} у
     * входного объявления стоят названные шаги.
     *
     * <p><b>Шаги сопровождения объявлены у ТРАНША, а не у сделки:</b>
     * сопровождение — статус транша, и обработчик ищет свою работу в
     * объявлении транша по его статусу
     * ({@code StrategyStepSelector#selectTrancheStep}).
     *
     * @param managingSteps шаги статуса сопровождения в порядке объявления
     */
    static Strategy withManagingSteps(String internalId, String accountId, String instrumentId,
                                      MarketPhase.Type phase, List<StrategyStep> managingSteps) {
        Strategy strategy = withEntryCommandOnPhase(internalId, accountId, instrumentId, phase);
        strategy.getDetails().getFirst().getTranches().getFirst().getStepsByStatus()
                .put(DealTranche.Status.MANAGING, new ArrayList<>(managingSteps));
        return strategy;
    }

    /**
     * Определение на ДВУХ входных объявлениях по одному уровню: транши
     * сделки различаются объявлением, и шаги сопровождения стоят только у
     * первого.
     *
     * <p><b>Два объявления, а не два уровня одного:</b> уровни одного
     * объявления делят его шаги, и выход, объявленный у одного уровня,
     * вывел бы оба — клетка о выходе ОДНОГО транша из двух предмета не
     * имела бы.
     *
     * @param managingSteps шаги сопровождения первого объявления
     */
    static Strategy withTwoDeclarationsFirstManaged(String internalId, String accountId, String instrumentId,
                                                    MarketPhase.Type phase, List<StrategyStep> managingSteps) {
        Strategy strategy = withManagingSteps(internalId, accountId, instrumentId, phase, managingSteps);
        StrategyDetail detail = strategy.getDetails().getFirst();
        StrategyTranche second = new StrategyTranche();
        second.setKey(SECOND_TRANCHE_KEY);
        second.setLevelCount(1);
        Map<DealTranche.Status, List<StrategyStep>> byStatus = new LinkedHashMap<>();
        byStatus.put(DealTranche.Status.PRECHECK, new ArrayList<>(List.of(
                protectedEntryStep(phaseCondition(phase), WORKING_STOP_PERCENTS, "second-entry-order"))));
        second.setStepsByStatus(byStatus);
        detail.getTranches().add(second);
        return strategy;
    }

    /**
     * Определение на ДВУХ объявлениях, чьи транши в предвходовой проверке
     * расходятся родом исхода: входное спрашивает фазу, второе — операнд,
     * которого владелец данных не отдаст, и на его отсутствие отвечает
     * управляемым сворачиванием.
     *
     * <p><b>Зачем расхождение.</b> Каскад отдаёт сделке одобренное ребро
     * транша и просьбу о сворачивании ТЕМ ЖЕ проходом только тогда, когда
     * их дают РАЗНЫЕ транши: ребро одного транша есть «каскад действовал»,
     * и сделка без просьбы о сворачивании вышла бы на нём, не дойдя до
     * своего ребра ({@code DealActiveHandler#cascadeReaction}). Ложная фаза
     * закрывает входной транш, непокрытый операнд второго просит выхода.
     *
     * <p><b>Второе объявление НЕ входное, и это несущее.</b> Входной шаг у
     * объявления один ({@code STRATEGY_TRANCHE_ENTRY_NOT_UNIQUE}), а отбор входа
     * читает шаги одного входного объявления из нескольких — какого, решает
     * порядок загрузки копии (находка F-23). Шаг защиты в предвходовой
     * проверке входом не является, и входное объявление у детали остаётся
     * единственным.
     *
     * @param internalId   идентичность определения
     * @param accountId    идентичность биржевого счёта
     * @param instrumentId идентичность инструмента
     * @param phase        фаза детали, она же требуемая входным объявлением
     */
    static Strategy withPhaseAndGracefullyExpiringDeclarations(String internalId, String accountId,
                                                              String instrumentId, MarketPhase.Type phase) {
        Strategy strategy = withEntryLevelsOnPhase(internalId, accountId, instrumentId, phase, 1);
        StrategyStep expiring = new StrategyStep();
        expiring.setStepType(StrategyStepType.MAIN_PROTECTION);
        expiring.setCondition(indicatorCondition());
        StrategyMarketDataExpiredSetting gracefulClose = new StrategyMarketDataExpiredSetting();
        gracefulClose.setProtectedPositionAction(MarketDataExpiredAction.GRACEFUL_CLOSE);
        gracefulClose.setUnprotectedPositionAction(MarketDataExpiredAction.GRACEFUL_CLOSE);
        expiring.setMarketDataExpiredSetting(gracefulClose);
        expiring.setActions(new ArrayList<>(List.<StrategyAction>of(
                stopLossAlgo("second-stop", WORKING_STOP_PERCENTS))));
        StrategyTranche second = new StrategyTranche();
        second.setKey(SECOND_TRANCHE_KEY);
        second.setLevelCount(1);
        Map<DealTranche.Status, List<StrategyStep>> byStatus = new LinkedHashMap<>();
        byStatus.put(DealTranche.Status.PRECHECK, new ArrayList<>(List.of(expiring)));
        second.setStepsByStatus(byStatus);
        strategy.getDetails().getFirst().getTranches().add(second);
        return strategy;
    }

    /**
     * Определение на ДВУХ входных объявлениях, чьи транши на подтверждённом
     * входе расходятся родом работы: у входного объявления там стои́т добор,
     * у второго — шаг на фазе рынка, отвечающий на её отсутствие управляемым
     * сворачиванием.
     *
     * <p><b>Зачем расхождение.</b> Транш на подтверждённом входе операнда
     * статуса сделки не имеет, и набор риска доходит до него только
     * переносом строки исполнения: добор заводит строку тем проходом, на
     * котором соседний транш просит сворачивания, и команды каскада этого
     * прохода не отправляются (docs/rules/exit-teardown-order.md §«Окно
     * сворачивания: нового риска не берёт ни один транш»). Просьбу и строку
     * обязаны дать РАЗНЫЕ транши одним проходом.
     *
     * <p><b>Одним проходом их сводит налив, а не определение.</b> Шаги
     * подтверждённого входа отбираются первым же проходом транша в этом
     * статусе, и оба транша встают в него вместе, когда ноги налиты одним
     * ответом площадки. Шаг второго объявления читает операнд, которого
     * владелец данных не отдаст никогда, — его устаревание наступает на том
     * же проходе. Условие на фазе для этого не годится: правило
     * {@code MARKET_PHASE_IS} операнда-источника фазы не несёт, и гейт
     * свежести зависимым от данных его не считает
     * ({@code StrategyCondition#readsMarketData}).
     *
     * @param internalId   идентичность определения
     * @param accountId    идентичность биржевого счёта
     * @param instrumentId идентичность инструмента
     * @param phase        фаза детали, она же требуемая входом
     * @param addOnKey     ключ действия добора
     */
    static Strategy withAddOnBesideGracefullyExpiringOnEntryFinalized(String internalId, String accountId,
                                                                    String instrumentId, MarketPhase.Type phase,
                                                                    String addOnKey) {
        Strategy strategy = withEntryCommandOnPhase(internalId, accountId, instrumentId, phase);
        StrategyDetail detail = strategy.getDetails().getFirst();
        detail.getTranches().getFirst().getStepsByStatus().put(DealTranche.Status.ENTRY_FINALIZED,
                new ArrayList<>(List.of(managingStep(StrategyStepType.GRID_ENTRY, addOnEntry(addOnKey)))));
        StrategyStep expiring = new StrategyStep();
        expiring.setStepType(StrategyStepType.MAIN_PROTECTION);
        expiring.setCondition(indicatorCondition());
        StrategyMarketDataExpiredSetting gracefulClose = new StrategyMarketDataExpiredSetting();
        gracefulClose.setProtectedPositionAction(MarketDataExpiredAction.GRACEFUL_CLOSE);
        gracefulClose.setUnprotectedPositionAction(MarketDataExpiredAction.GRACEFUL_CLOSE);
        expiring.setMarketDataExpiredSetting(gracefulClose);
        expiring.setActions(new ArrayList<>(List.<StrategyAction>of(
                stopLossAlgo("second-stop", WORKING_STOP_PERCENTS))));
        StrategyTranche second = new StrategyTranche();
        second.setKey(SECOND_TRANCHE_KEY);
        second.setLevelCount(1);
        Map<DealTranche.Status, List<StrategyStep>> byStatus = new LinkedHashMap<>();
        byStatus.put(DealTranche.Status.PRECHECK, new ArrayList<>(List.of(
                protectedEntryStep(phaseCondition(phase), WORKING_STOP_PERCENTS, "second-entry-order"))));
        byStatus.put(DealTranche.Status.ENTRY_FINALIZED, new ArrayList<>(List.of(expiring)));
        second.setStepsByStatus(byStatus);
        detail.getTranches().add(second);
        return strategy;
    }

    /**
     * Шаг сопровождения без условия: пустой перечень клауз истинен всегда
     * ({@code StrategyConditionEvaluator#evaluate}) и рыночных данных не
     * читает, поэтому шаг исполняется первым же проходом сопровождения, а
     * гейт свежести его не касается.
     *
     * @param stepType род шага
     * @param actions  действия пакета в порядке объявления
     */
    static StrategyStep managingStep(StrategyStepType stepType, StrategyAction... actions) {
        StrategyStep step = new StrategyStep();
        step.setStepType(stepType);
        step.setCondition(new StrategyCondition(new ArrayList<>()));
        step.setMarketDataExpiredSetting(expiredSetting());
        step.setActions(new ArrayList<>(List.of(actions)));
        return step;
    }

    /**
     * ОТДЕЛЬНАЯ условная защита: стоп на всю экспозицию транша, уровень —
     * процент от якоря, триггер по последней цене.
     *
     * @param actionKey        ключ действия
     * @param distancePercents дистанция уровня, процент якоря
     */
    static StrategyAlgoOrderAction stopLossAlgo(String actionKey, String distancePercents) {
        StopLossSettings stopLoss = new StopLossSettings();
        stopLoss.setCalculationType(StopLossCalculationType.ENTRY_PRICE_PERCENT);
        stopLoss.setDistancePercents(new BigDecimal(distancePercents));
        stopLoss.setTriggerPriceType(AlgoOrder.TriggerPriceType.LAST);
        StrategyAlgoOrderAction action = algoAction(actionKey, StrategyActionType.CREATE_ACTION,
                AlgoOrder.ConditionType.STOP_LOSS);
        action.setStopLossSettings(stopLoss);
        action.setTriggerPriceType(AlgoOrder.TriggerPriceType.LAST);
        return action;
    }

    /**
     * ТРЕЙЛИНГ: защита, чей уровень ведёт площадка откатом от экстремума
     * цены, на всю экспозицию транша; активируется сразу — порога прибыли
     * не объявлено.
     *
     * @param actionKey        ключ действия
     * @param callbackPercents откат от экстремума, процент
     */
    static StrategyAlgoOrderAction trailingAlgo(String actionKey, String callbackPercents) {
        StrategyAlgoOrderAction action = algoAction(actionKey, StrategyActionType.CREATE_ACTION,
                AlgoOrder.ConditionType.TRAILING_PERCENTS);
        TrailingSettings trailing = new TrailingSettings();
        trailing.setCallbackPercents(new BigDecimal(callbackPercents));
        action.setTrailingSettings(trailing);
        return action;
    }

    /**
     * Уровень ФИКСАЦИИ ПРИБЫЛИ: условная reduce-only заявка на всю
     * экспозицию транша, уровень — процент прибыли от якоря.
     *
     * <p>Риска она не создаёт и контроля не ослабляет: не защитная и не
     * входная (docs/rules/risk-validator-scope.md).
     *
     * @param actionKey      ключ действия
     * @param profitPercents дистанция уровня, процент якоря в прибыльную сторону
     */
    static StrategyAlgoOrderAction takeProfitAlgo(String actionKey, String profitPercents) {
        StrategyAlgoOrderAction action = algoAction(actionKey, StrategyActionType.CREATE_ACTION,
                AlgoOrder.ConditionType.TAKE_PROFIT);
        action.setTriggerProfitPercents(new BigDecimal(profitPercents));
        action.setTriggerPriceType(AlgoOrder.TriggerPriceType.LAST);
        return action;
    }

    /**
     * Снятие отдельной условной защиты, заведённой действием с названным
     * ключом: цель резолвится по строке исполнения того действия
     * ({@code CancelAlgoOrderActionExecutor#targetAlgoOrder}).
     *
     * @param actionKey ключ действия
     * @param targetKey ключ действия, заведшего снимаемую защиту
     */
    static StrategyAlgoOrderAction cancelStopLossAlgo(String actionKey, String targetKey) {
        StrategyAlgoOrderAction action = algoAction(actionKey, StrategyActionType.CANCEL_ACTION,
                AlgoOrder.ConditionType.STOP_LOSS);
        action.setTargetActionKey(targetKey);
        return action;
    }

    /**
     * ДОБОР: создание ещё одной входной ноги той же стороны со встроенной
     * защитой — действие, создающее риск.
     *
     * @param actionKey ключ действия
     */
    static StrategyAction addOnEntry(String actionKey) {
        return protectedEntryAction(actionKey, attachedStopLoss(WORKING_STOP_PERCENTS));
    }

    /**
     * Частичный выход транша: reduce-only заявка названной долей его
     * экспозиции (docs/rules/no-partial-close.md §«Механизм частичного
     * выхода»).
     *
     * @param actionKey ключ действия
     * @param percents  доля экспозиции транша, процент
     */
    static StrategyOrderAction reduceOnlyExit(String actionKey, String percents) {
        StrategyOrderAction action = new StrategyOrderAction();
        action.setKey(actionKey);
        action.setActionType(StrategyActionType.CREATE_ACTION);
        // Рода «выход» у заявки нет: reduce-only несёт признак намерения, а
        // род остаётся родом простой заявки (docs/models/domain/core/Order.md).
        action.setOrderType(Order.Type.ENTRY);
        action.setDirection(StrategyTradeDirection.LONG);
        action.setAllocationPercents(new BigDecimal(percents));
        action.setPositionReducingOnly(Boolean.TRUE);
        return action;
    }

    /**
     * Явное действие ВЫХОДА позицией: полное закрытие нетто-экспозиции
     * командой закрытия (docs/rules/no-partial-close.md §«Две законные
     * формы полного выхода»).
     *
     * @param actionKey ключ действия
     */
    static StrategyPositionAction positionExit(String actionKey) {
        StrategyPositionAction action = new StrategyPositionAction();
        action.setKey(actionKey);
        action.setActionType(StrategyActionType.EXIT_ACTION);
        return action;
    }

    private static StrategyAlgoOrderAction algoAction(String actionKey, StrategyActionType actionType,
                                                      AlgoOrder.ConditionType conditionType) {
        StrategyAlgoOrderAction action = new StrategyAlgoOrderAction();
        action.setKey(actionKey);
        action.setActionType(actionType);
        action.setConditionType(conditionType);
        return action;
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
        return protectedEntryStep(condition, attachedStopLoss(stopDistancePercents), actionKeys);
    }

    /**
     * Тот же шаг с НАЗВАННОЙ встроенной защитой: ею разводятся способы
     * расчёта защитного уровня, а не сама форма шага.
     *
     * @param condition  условие шага
     * @param protection объявление встроенной защиты ноги входа
     * @param actionKeys ключи действий пакета в порядке объявления
     */
    private static StrategyStep protectedEntryStep(StrategyCondition condition,
                                                   StrategyAttachedProtectionSettings protection,
                                                   String... actionKeys) {
        List<StrategyAction> actions = new ArrayList<>();
        for (String actionKey : actionKeys) {
            actions.add(protectedEntryAction(actionKey, protection));
        }
        StrategyStep step = new StrategyStep();
        step.setStepType(StrategyStepType.ENTRY);
        step.setCondition(condition);
        step.setMarketDataExpiredSetting(expiredSetting());
        step.setActions(actions);
        return step;
    }

    /** Одно действие пакета: нога входа со встроенной защитой. */
    private static StrategyAction protectedEntryAction(String actionKey,
                                                       StrategyAttachedProtectionSettings protection) {
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
        action.setAttachedProtection(protection);
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

    /**
     * Та же защита, считаемая ОТ СТРУКТУРЫ: база — уровень раскладки под
     * ключом объявления, буфер — процент от базы.
     *
     * <p><b>Дистанция здесь — процент БАЗЫ, а не цены входа</b>
     * ({@code PriceCalculator#structureStop}), и число её выбрано малым:
     * буфер, сопоставимый с расстоянием от цены до уровня, увёл бы
     * уровень на убыточную сторону при всякой базе — то есть отнял бы у
     * кейса его операнд.
     */
    private static StrategyAttachedProtectionSettings structureStopLoss() {
        StopLossSettings stopLoss = new StopLossSettings();
        stopLoss.setCalculationType(StopLossCalculationType.MARKET_STRUCTURE_BUFFER_PERCENT);
        stopLoss.setStructureKey(STRUCTURE_KEY);
        stopLoss.setDistancePercents(new BigDecimal(STRUCTURE_BUFFER_PERCENTS));
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
        return structure(timeframe, Destiny.ENTRY_CONDITION);
    }

    /**
     * То же объявление с НАЗВАННЫМ назначением: структура, от которой
     * считается защитный уровень, объявляется защитой, а не условием
     * входа (docs/models/domain/aggregate/Strategy.md
     * §StrategyMarketStructureSetting).
     *
     * @param timeframe таймфрейм расчёта
     * @param destiny   для чего считается результат
     */
    private static StrategyMarketStructureSetting structure(TimeFrame timeframe, Destiny destiny) {
        MarketStructureParams params = new MarketStructureParams();
        params.setLookbackBars(20);
        StrategyMarketStructureSetting setting = new StrategyMarketStructureSetting();
        setting.setKey(STRUCTURE_KEY);
        setting.setTimeframe(timeframe);
        setting.setEfficiencyRatioKey(EFFICIENCY_RATIO_KEY);
        setting.setAtrKey(ATR_KEY);
        setting.setParams(params);
        setting.setDestiny(destiny);
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
