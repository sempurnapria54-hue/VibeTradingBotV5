package com.example.strategies.unit.validation;

import com.example.strategies.api.model.request.CreateStrategyApiRequest;
import com.example.strategies.api.model.request.UpdateStrategyStatusApiRequest;
import com.example.strategies.api.model.strategy.IndicatorParamsApiModel;
import com.example.strategies.api.model.strategy.StopLossSettingsApiModel;
import com.example.strategies.api.model.strategy.StrategyActionApiModel;
import com.example.strategies.api.model.strategy.StrategyAlgoOrderActionApiModel;
import com.example.strategies.api.model.strategy.StrategyConditionApiModel;
import com.example.strategies.api.model.strategy.StrategyConditionOperandApiModel;
import com.example.strategies.api.model.strategy.StrategyConditionRuleApiModel;
import com.example.strategies.api.model.strategy.StrategyDetailApiModel;
import com.example.strategies.api.model.strategy.StrategyIndicatorSettingApiModel;
import com.example.strategies.api.model.strategy.StrategyMarketPhaseRuleApiModel;
import com.example.strategies.api.model.strategy.StrategyMarketStructureSettingApiModel;
import com.example.strategies.api.model.strategy.StrategyOrderActionApiModel;
import com.example.strategies.api.model.strategy.StrategyPositionActionApiModel;
import com.example.strategies.api.model.strategy.StrategyPricePlacementApiModel;
import com.example.strategies.api.model.strategy.StrategyStepApiModel;
import com.example.strategies.api.model.strategy.StrategyTrancheApiModel;
import com.example.strategies.api.model.strategy.TrailingSettingsApiModel;
import com.example.strategies.domain.model.TenantRiskAppetite;
import com.example.strategies.domain.validation.StrategyDefinitionValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.server.ResponseStatusException;

/**
 * Общая сборка кейсов предмета {@code strategy-definition-validation}
 * (`.claude/tests/cases/strategy-definition-validation.md`).
 *
 * <p><b>Вход собирается мутацией эталона, а не сборкой с нуля.</b> Дерево
 * определения несёт четыре детали, два уровня объявления шагов и шесть
 * настроек индикаторов; собранное руками, оно проверяло бы валидатор
 * против дерева, которого в проде не существует, а «ось, которую кейс не
 * трогает, молчит» стало бы непроверяемым. Эталон лежит в тестовых
 * ресурсах сервиса, а клетка {@code U1.1} объявляет его проходящим:
 * иначе мутации мерили бы отказ, который стоял и без них.
 *
 * <p><b>Числа тенанта — базовая пара {@code 1} и {@code 100}</b>: самая
 * тесная, которую эталон ещё проходит. Свободнее — и неравенства
 * создания не мерили бы ничего.
 *
 * <p>Моков у предмета нет ни одного, и это исход признака уровня:
 * коллабораторов с вводом-выводом у валидатора нет вовсе — числа
 * приходят значением, дерево аргументом.
 */
final class ValidationFixture {

    /** Эталонное определение репозитория — вход всех кейсов предмета. */
    static final String REFERENCE_DEFINITION = "strategy-examples/trend-following-ema.json";

    /** Потолок одновременного риска тенанта базовой пары, проценты базы. */
    static final String BASE_SIMULTANEOUS = "1";

    /** Предел множителя катастрофического потолка базовой пары. */
    static final String BASE_CATASTROPHIC = "100";

    /** Порядок деталей эталона: индекс — тип фазы. */
    private static final List<String> PHASES = List.of("BULL_TREND", "BEAR_TREND", "RANGE", "UNKNOWN");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final StrategyDefinitionValidator VALIDATOR = new StrategyDefinitionValidator();

    private ValidationFixture() {
    }

    /** Свежее дерево эталона: каждый кейс портит собственную копию. */
    static CreateStrategyApiRequest reference() {
        try (InputStream body = new ClassPathResource(REFERENCE_DEFINITION).getInputStream()) {
            CreateStrategyApiRequest request = MAPPER.readValue(body, CreateStrategyApiRequest.class);
            if (Objects.isNull(request.getDetails()) || request.getDetails().size() != PHASES.size()) {
                throw new IllegalStateException("эталон разобран не в четыре детали — мутировать нечего");
            }
            request.setDetails(new ArrayList<>(request.getDetails()));
            return request;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Числа тенанта: пустое поле означает «держатель числа не назначил». */
    static TenantRiskAppetite appetite(String simultaneous, String catastrophic) {
        return new TenantRiskAppetite(decimal(simultaneous), decimal(catastrophic));
    }

    /** Базовая пара — самая тесная, которую эталон ещё проходит. */
    static TenantRiskAppetite baseAppetite() {
        return appetite(BASE_SIMULTANEOUS, BASE_CATASTROPHIC);
    }

    /**
     * Нарушения создания: пустой список — отказа нет вовсе. Разбор идёт
     * по разделителю склейки, которой валидатор собирает сообщение.
     */
    static List<String> violations(CreateStrategyApiRequest request) {
        return violations(request, baseAppetite());
    }

    static List<String> violations(CreateStrategyApiRequest request, TenantRiskAppetite appetite) {
        try {
            VALIDATOR.validateCreate(request, appetite);
            return List.of();
        } catch (ResponseStatusException rejected) {
            return split(rejected);
        }
    }

    /** Нарушения второй точки входа — пять неравенств на текущих числах. */
    static List<String> inequalityViolations(List<StrategyDetailApiModel> details, TenantRiskAppetite appetite) {
        try {
            VALIDATOR.validateRiskInequalities(details, appetite);
            return List.of();
        } catch (ResponseStatusException rejected) {
            return split(rejected);
        }
    }

    /** Отказ создания как исключение — там, где кейс мерит его статус. */
    static ResponseStatusException rejection(CreateStrategyApiRequest request, TenantRiskAppetite appetite) {
        try {
            VALIDATOR.validateCreate(request, appetite);
            throw new IllegalStateException("отказа нет — кейс мерил бы пустоту");
        } catch (ResponseStatusException rejected) {
            return rejected;
        }
    }

    /** Предмет целиком — там, где кейс зовёт точку входа напрямую. */
    static StrategyDefinitionValidator validator() {
        return VALIDATOR;
    }

    static UpdateStrategyStatusApiRequest statusRequest(String status) {
        UpdateStrategyStatusApiRequest request = new UpdateStrategyStatusApiRequest();
        request.setStatus(status);
        return request;
    }

    /** Нарушения, несущие названный фрагмент, — единица разбора выдачи кейса. */
    static List<String> matching(List<String> violations, String fragment) {
        return violations.stream().filter(violation -> violation.contains(fragment)).toList();
    }

    // --- навигация по дереву эталона --------------------------------------------------

    static StrategyDetailApiModel detail(CreateStrategyApiRequest request, String phaseType) {
        int index = PHASES.indexOf(phaseType);
        if (index < 0) {
            throw new IllegalStateException("эталон детали фазы " + phaseType + " не несёт");
        }
        StrategyDetailApiModel detail = request.getDetails().get(index);
        if (Objects.equals(detail.getMarketPhaseType(), phaseType)) {
            return detail;
        }
        throw new IllegalStateException("порядок деталей эталона сменился: ждали " + phaseType
                + ", встретили " + detail.getMarketPhaseType());
    }

    /** Торгуемая деталь бычьего тренда — предмет большинства мутаций. */
    static StrategyDetailApiModel bull(CreateStrategyApiRequest request) {
        return detail(request, "BULL_TREND");
    }

    static StrategyDetailApiModel bear(CreateStrategyApiRequest request) {
        return detail(request, "BEAR_TREND");
    }

    /** Неторгуемая деталь диапазона: ни объявлений, ни риск-чисел. */
    static StrategyDetailApiModel range(CreateStrategyApiRequest request) {
        return detail(request, "RANGE");
    }

    static StrategyDetailApiModel unknownPhase(CreateStrategyApiRequest request) {
        return detail(request, "UNKNOWN");
    }

    /** Изменяемый список объявлений детали. */
    static List<StrategyTrancheApiModel> tranches(StrategyDetailApiModel detail) {
        List<StrategyTrancheApiModel> declared = detail.getTranches();
        if (Objects.isNull(declared)) {
            detail.setTranches(new ArrayList<>());
        } else if (Objects.isNull(asArrayList(declared))) {
            detail.setTranches(new ArrayList<>(declared));
        }
        return detail.getTranches();
    }

    static StrategyTrancheApiModel tranche(StrategyDetailApiModel detail) {
        List<StrategyTrancheApiModel> declared = tranches(detail);
        if (declared.isEmpty()) {
            throw new IllegalStateException("у детали нет объявлений — мутировать нечего");
        }
        return declared.get(0);
    }

    static List<StrategyStepApiModel> steps(StrategyTrancheApiModel tranche, String status) {
        Map<String, List<StrategyStepApiModel>> byStatus = mutableSteps(tranche.getStepsByStatus());
        tranche.setStepsByStatus(byStatus);
        List<StrategyStepApiModel> steps = byStatus.get(status);
        if (Objects.isNull(steps)) {
            throw new IllegalStateException("у объявления нет шагов статуса " + status);
        }
        return steps;
    }

    /** Карта шагов объявления — изменяемая копия, ключи перекладываются кейсом. */
    static Map<String, List<StrategyStepApiModel>> stepsByStatus(StrategyTrancheApiModel tranche) {
        Map<String, List<StrategyStepApiModel>> byStatus = mutableSteps(tranche.getStepsByStatus());
        tranche.setStepsByStatus(byStatus);
        return byStatus;
    }

    /** Карта агрегатных шагов детали — изменяемая копия. */
    static Map<String, List<StrategyStepApiModel>> dealStepsByStatus(StrategyDetailApiModel detail) {
        Map<String, List<StrategyStepApiModel>> byStatus = mutableSteps(detail.getStepsByStatus());
        detail.setStepsByStatus(byStatus);
        return byStatus;
    }

    /** Входной шаг объявления — предвходовый статус, единственный шаг. */
    static StrategyStepApiModel entryStep(StrategyDetailApiModel detail) {
        return steps(tranche(detail), "PRECHECK").get(0);
    }

    /** Шаг первичной постановки защиты — полный набор покрытия. */
    static StrategyStepApiModel protectionStep(StrategyDetailApiModel detail) {
        return steps(tranche(detail), "ENTRY_FINALIZED").get(0);
    }

    /** Шаг переноса уровня: защитное замещение с названной целью. */
    static StrategyStepApiModel transferStep(StrategyDetailApiModel detail) {
        return steps(tranche(detail), "MANAGING").get(0);
    }

    /** Шаг «снятие плюс трейлинг»: покрытие забирается и возвращается. */
    static StrategyStepApiModel cancelStep(StrategyDetailApiModel detail) {
        return steps(tranche(detail), "MANAGING").get(1);
    }

    /** Агрегатный шаг выхода — единственный шаг уровня сделки. */
    static StrategyStepApiModel exitStep(StrategyDetailApiModel detail) {
        return dealStepsByStatus(detail).get("ACTIVE").get(0);
    }

    /** Входное действие-заявка объявления. */
    static StrategyOrderActionApiModel entryAction(StrategyDetailApiModel detail) {
        return (StrategyOrderActionApiModel) actions(entryStep(detail)).get(0);
    }

    /** Действие детали по ключу — обход обоих уровней объявления. */
    static StrategyAlgoOrderActionApiModel algoAction(StrategyDetailApiModel detail, String key) {
        StrategyActionApiModel action = action(detail, key);
        if (action instanceof StrategyAlgoOrderActionApiModel algo) {
            return algo;
        }
        throw new IllegalStateException("действие " + key + " не условная заявка");
    }

    static StrategyActionApiModel action(StrategyDetailApiModel detail, String key) {
        for (StrategyStepApiModel step : allSteps(detail)) {
            for (StrategyActionApiModel action : actions(step)) {
                if (Objects.equals(action.getKey(), key)) {
                    return action;
                }
            }
        }
        throw new IllegalStateException("у детали нет действия с ключом " + key);
    }

    /** Все шаги детали — потраншевые и агрегатные, в порядке объявления. */
    static List<StrategyStepApiModel> allSteps(StrategyDetailApiModel detail) {
        List<StrategyStepApiModel> steps = new ArrayList<>();
        for (StrategyTrancheApiModel tranche : nullSafe(detail.getTranches())) {
            if (Objects.nonNull(tranche.getStepsByStatus())) {
                tranche.getStepsByStatus().values().forEach(steps::addAll);
            }
        }
        if (Objects.nonNull(detail.getStepsByStatus())) {
            detail.getStepsByStatus().values().forEach(steps::addAll);
        }
        return steps;
    }

    /** Изменяемый пакет действий шага: кейс добавляет и удаляет члены. */
    static List<StrategyActionApiModel> actions(StrategyStepApiModel step) {
        List<StrategyActionApiModel> declared = step.getActions();
        if (Objects.isNull(declared)) {
            step.setActions(new ArrayList<>());
        } else if (Objects.isNull(asArrayList(declared))) {
            step.setActions(new ArrayList<>(declared));
        }
        return step.getActions();
    }

    /** Правила условия шага в объявленном порядке. */
    static List<StrategyConditionRuleApiModel> rules(StrategyStepApiModel step) {
        StrategyConditionApiModel condition = step.getCondition();
        if (Objects.isNull(condition) || Objects.isNull(condition.getRules())) {
            throw new IllegalStateException("у шага нет условия — мутировать нечего");
        }
        if (Objects.isNull(asArrayList(condition.getRules()))) {
            condition.setRules(new ArrayList<>(condition.getRules()));
        }
        return condition.getRules();
    }

    /** Правило классификации фазы по индексу: 0 — диапазон, 1 — бычий, 2 — медвежий. */
    static StrategyMarketPhaseRuleApiModel phaseRule(CreateStrategyApiRequest request, int index) {
        List<StrategyMarketPhaseRuleApiModel> phaseRules = request.getMarketPhaseSetting().getPhaseRules();
        if (Objects.isNull(asArrayList(phaseRules))) {
            request.getMarketPhaseSetting().setPhaseRules(new ArrayList<>(phaseRules));
        }
        return request.getMarketPhaseSetting().getPhaseRules().get(index);
    }

    /** Правило условия классификации фазы — единственное у каждой клаузы. */
    static StrategyConditionRuleApiModel phaseConditionRule(CreateStrategyApiRequest request, int index) {
        StrategyConditionApiModel condition = phaseRule(request, index).getCondition();
        if (Objects.isNull(asArrayList(condition.getRules()))) {
            condition.setRules(new ArrayList<>(condition.getRules()));
        }
        return condition.getRules().get(0);
    }

    static StrategyIndicatorSettingApiModel indicator(CreateStrategyApiRequest request, String key) {
        return indicators(request).stream()
                .filter(setting -> Objects.equals(setting.getKey(), key))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("у эталона нет настройки индикатора " + key));
    }

    static List<StrategyIndicatorSettingApiModel> indicators(CreateStrategyApiRequest request) {
        List<StrategyIndicatorSettingApiModel> declared = request.getIndicatorSettings();
        if (Objects.isNull(declared)) {
            throw new IllegalStateException("у эталона нет каталога индикаторов — мутировать нечего");
        }
        if (Objects.isNull(asArrayList(declared))) {
            request.setIndicatorSettings(new ArrayList<>(declared));
        }
        return request.getIndicatorSettings();
    }

    static StrategyMarketStructureSettingApiModel structure(CreateStrategyApiRequest request, String key) {
        return structures(request).stream()
                .filter(setting -> Objects.equals(setting.getKey(), key))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("у эталона нет настройки структуры " + key));
    }

    static List<StrategyMarketStructureSettingApiModel> structures(CreateStrategyApiRequest request) {
        List<StrategyMarketStructureSettingApiModel> declared = request.getMarketStructureSettings();
        if (Objects.isNull(declared)) {
            throw new IllegalStateException("у эталона нет каталога структур — мутировать нечего");
        }
        if (Objects.isNull(asArrayList(declared))) {
            request.setMarketStructureSettings(new ArrayList<>(declared));
        }
        return request.getMarketStructureSettings();
    }

    // --- сборка новых узлов -----------------------------------------------------------

    static StrategyTrancheApiModel newTranche(String key, Integer levelCount, Boolean reopenAllowed) {
        StrategyTrancheApiModel tranche = new StrategyTrancheApiModel();
        tranche.setKey(key);
        tranche.setLevelCount(levelCount);
        tranche.setPositionReopenAllowed(reopenAllowed);
        return tranche;
    }

    static StrategyStepApiModel newStep(String stepType, StrategyActionApiModel... actions) {
        StrategyStepApiModel step = new StrategyStepApiModel();
        step.setStepType(stepType);
        step.setActions(new ArrayList<>(Arrays.asList(actions)));
        return step;
    }

    static StrategyAlgoOrderActionApiModel newAlgo(String key, String actionType, String conditionType) {
        StrategyAlgoOrderActionApiModel action = new StrategyAlgoOrderActionApiModel();
        action.setKey(key);
        action.setActionType(actionType);
        action.setConditionType(conditionType);
        return action;
    }

    static StrategyOrderActionApiModel newOrder(String key, String orderType, String direction, String allocation) {
        StrategyOrderActionApiModel action = new StrategyOrderActionApiModel();
        action.setKey(key);
        action.setActionType("CREATE_ACTION");
        action.setOrderType(orderType);
        action.setDirection(direction);
        action.setAllocationPercents(decimal(allocation));
        return action;
    }

    static StrategyPositionActionApiModel newPositionAction(String key) {
        StrategyPositionActionApiModel action = new StrategyPositionActionApiModel();
        action.setKey(key);
        action.setActionType("EXIT_ACTION");
        return action;
    }

    /** Настройка индикатора с объявленным назначением — кейс правит её ось. */
    static StrategyIndicatorSettingApiModel newIndicator(String key, String indicatorType,
                                                         IndicatorParamsApiModel params) {
        StrategyIndicatorSettingApiModel setting = new StrategyIndicatorSettingApiModel();
        setting.setKey(key);
        setting.setIndicatorType(indicatorType);
        setting.setDestiny("ENTRY_CONDITION");
        setting.setParams(params);
        return setting;
    }

    /** Настройка структуры рынка с объявленными таймфреймом и назначением. */
    static StrategyMarketStructureSettingApiModel newStructure(String key) {
        StrategyMarketStructureSettingApiModel setting = new StrategyMarketStructureSettingApiModel();
        setting.setKey(key);
        setting.setTimeframe("ONE_HOUR");
        setting.setDestiny("MARKET_PHASE");
        return setting;
    }

    /** Настройки уровня стопа, не требующие ни одной ссылки. */
    static StopLossSettingsApiModel newStopSettings() {
        StopLossSettingsApiModel settings = new StopLossSettingsApiModel();
        settings.setCalculationType("ENTRY_PRICE_PERCENT");
        settings.setTriggerPriceType("MARK");
        return settings;
    }

    /** Блок трейлинга — второй носитель источника уровня. */
    static TrailingSettingsApiModel newTrailingSettings() {
        TrailingSettingsApiModel settings = new TrailingSettingsApiModel();
        settings.setActivationProfitPercents(decimal("2"));
        settings.setCallbackPercents(decimal("1"));
        return settings;
    }

    /** Блок размещения цены с названной базой. */
    static StrategyPricePlacementApiModel newPlacement(String baseType) {
        StrategyPricePlacementApiModel placement = new StrategyPricePlacementApiModel();
        placement.setBaseType(baseType);
        return placement;
    }

    static StrategyConditionOperandApiModel newOperand(String sourceType) {
        StrategyConditionOperandApiModel operand = new StrategyConditionOperandApiModel();
        operand.setSourceType(sourceType);
        return operand;
    }

    static StrategyConditionOperandApiModel constantOperand(String valueType, String value) {
        StrategyConditionOperandApiModel operand = newOperand("CONSTANT");
        operand.setValueType(valueType);
        operand.setValue(value);
        return operand;
    }

    static StrategyConditionOperandApiModel indicatorOperand(String indicatorKey) {
        StrategyConditionOperandApiModel operand = newOperand("INDICATOR");
        operand.setIndicatorKey(indicatorKey);
        return operand;
    }

    static StrategyConditionRuleApiModel newRule(String ruleType) {
        StrategyConditionRuleApiModel rule = new StrategyConditionRuleApiModel();
        rule.setLevel(1);
        rule.setRuleType(ruleType);
        return rule;
    }

    /** Условие шага из названных правил — там, где кейс правило подменяет целиком. */
    static void replaceRules(StrategyStepApiModel step, StrategyConditionRuleApiModel... replacements) {
        StrategyConditionApiModel condition = new StrategyConditionApiModel();
        condition.setRules(new ArrayList<>(Arrays.asList(replacements)));
        step.setCondition(condition);
    }

    /** Глубокая копия торгуемой детали: второй разбор эталона, а не ссылка на первый. */
    static StrategyDetailApiModel bullDetailCopy() {
        return bull(reference());
    }

    /** Текст дерева — точка наблюдения клейма «вход не изменяется». */
    static String asJson(CreateStrategyApiRequest request) {
        try {
            return MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("дерево не сериализуется — наблюдать нечем", failure);
        }
    }

    static BigDecimal decimal(String value) {
        return Objects.isNull(value) ? null : new BigDecimal(value);
    }

    /**
     * Разбор склейки нарушений.
     *
     * <p><b>Разделитель склейки встречается ВНУТРИ члена</b>: текст
     * диапазонного нарушения несёт полуинтервал {@code (0; 100]}, и
     * наивный разрез по разделителю делит одно нарушение на два. Поэтому
     * новый член опознаётся НАЧАЛОМ ПУТИ, а не разделителем: корней у
     * путей валидатора пять, и все пять перечислены ниже. Фрагмент,
     * который ни одним из них не начинается, принадлежит предыдущему
     * члену.
     */
    private static List<String> split(ResponseStatusException rejected) {
        List<String> members = new ArrayList<>();
        for (String fragment : String.valueOf(rejected.getReason()).split("; ")) {
            if (members.isEmpty() || startsAViolation(fragment)) {
                members.add(fragment);
            } else {
                members.set(members.size() - 1, members.get(members.size() - 1) + "; " + fragment);
            }
        }
        return List.copyOf(members);
    }

    /** Корни путей валидатора — по ним опознаётся начало нового нарушения. */
    private static Boolean startsAViolation(String fragment) {
        return VIOLATION_ROOTS.stream().anyMatch(fragment::startsWith);
    }

    private static final List<String> VIOLATION_ROOTS = List.of(
            "details[", "strategy.", "marketPhaseSetting.", "duplicate detail for", "missing detail for");

    /**
     * Карта шагов, уже переведённая в изменяемую форму, возвращается как
     * есть: второй перевод отбросил бы члены, снятые кейсом из списка.
     */
    private static Map<String, List<StrategyStepApiModel>> mutableSteps(
            Map<String, List<StrategyStepApiModel>> byStatus) {
        if (Objects.isNull(byStatus)) {
            throw new IllegalStateException("карты шагов нет — мутировать нечего");
        }
        if (byStatus instanceof MutableSteps already) {
            return already;
        }
        MutableSteps copy = new MutableSteps();
        byStatus.forEach((status, steps) -> copy.put(status, new ArrayList<>(steps)));
        return copy;
    }

    /** Метка уже переведённой карты: признак читается типом, а не содержимым. */
    private static final class MutableSteps extends LinkedHashMap<String, List<StrategyStepApiModel>> {
    }

    /** Список уже изменяем — иначе кейс правил бы неизменяемую копию разбора. */
    private static <E> List<E> asArrayList(List<E> items) {
        return items instanceof ArrayList ? items : null;
    }

    private static <E> List<E> nullSafe(List<E> items) {
        return Objects.isNull(items) ? List.of() : items;
    }
}
