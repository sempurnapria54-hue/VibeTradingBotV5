package com.example.strategies.box;

import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Группа {@code B11} документа кейсов: обязательный вход не предъявлен — по
 * единице (.claude/tests/cases/strategies.md).
 *
 * <p><b>Случай — строка документа, и имя случая несёт её метку.</b> Отказ у
 * всех строк производит контейнер — биндинг и валидация аргумента, раньше
 * тела обработчика, — поэтому группа есть один параметризованный тест на
 * форму вызова, а красный прогон называет единицу, потерявшую охрану.
 *
 * <p><b>Тело строки создания — МУТАЦИЯ входа, который контейнер принимает, и
 * обе стороны проверяются в одном случае.</b> Сперва подаётся вход без
 * опущения, и его отказ классом контейнера роняет случай как непоставленное
 * предусловие; затем — тот же вход без названной единицы. Без первой
 * половины ожидание «отказ контейнера» было бы истинно и на входе, который
 * контейнер отвергает целиком по чужой причине: строка, дописавшая узел
 * ({@code MACD}, {@code OBV}, размещение цены), мерила бы опечатку в
 * дописанном, а не единицу. Первая половина проходит дальше контейнера и
 * заводит определение, поэтому отрицания второй — разностью от неё.
 *
 * <p><b>У строк перехода и чтения первой половины нет, и это не пропуск.</b>
 * Вход без опущения там — штатный переход и штатное чтение, их мерят
 * группы {@code B2}-{@code B5}; поданный здесь, переход сдвинул бы статус, о
 * неизменности которого строка и утверждает.
 *
 * <p><b>Строк {@code B11.23}-{@code B11.25} здесь нет: их предусловие
 * поставить нечем.</b> Объявление {@code STOCHASTIC} контейнер отвергает
 * целиком: у полей {@code kPeriod} и {@code dPeriod} Lombok под капитализацией
 * {@code beanspec} печатает {@code setkPeriod}/{@code getkPeriod}, а
 * сериализатор провода (Jackson 3) аксессора со строчной буквой после
 * префикса не опознаёт — оба поля остаются пустыми при любом теле, и
 * {@code @NotNull} отвергает вход без опущения тем же классом. Строка на
 * таком входе была бы зелена тавтологически; клетки гейтятся находкой
 * документа кейсов (.claude/tests/cases/strategies.md §«Находки владельцам»,
 * F-11).
 *
 * <p><b>У {@code B11.12} и {@code B11.13} рубежей два, и по коду первым
 * отвечает разбор тела.</b> Тип объявления индикатора есть внешнее свойство
 * подтипа {@code params}: без типа разбор отказывает «Missing external type
 * id property», без параметров при объявленном типе — «Missing property … for
 * external type id» ({@code ExternalTypeHandler#complete}, умолчание
 * {@code FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY} включено). Оба отказа —
 * нечитаемое тело, тот же класс {@code REQUEST_NOT_ACCEPTED}, поэтому
 * ожидание строки не меняется: она мерит, что отсутствие отсекается, а не
 * какой рубеж ответил.
 */
@DisplayName("B11 — Обязательный вход не предъявлен: по единице")
class RequiredInputBoxTest extends SharedStrategiesBox {

    /** Класс отказа контейнера: непредъявленная обязательная часть вызова. */
    private static final String NOT_ACCEPTED = "REQUEST_NOT_ACCEPTED";

    /**
     * Идентичность определения, которого нет: путь чтения одного годен по
     * форме, и утрата охраны заголовка ответила бы ненайденностью, а не
     * отказом контейнера.
     */
    private static final String ABSENT_DEFINITION = "00000000-0000-4000-8000-000000000000";

    /** Указатель на корень тела: узел полей команды создания. */
    private static final String ROOT = "";

    /** Первое правило условия первой клаузы классификации фазы. */
    private static final String PHASE_RULE = "/marketPhaseSetting/phaseRules/0/condition/rules/0";

    /** Цель строк перехода: годное тело, которое без охраны дошло бы до соседа. */
    private static final String ACTIVATION = "{\"status\": \"ACTIVE\"}";

    /** Полное объявление {@code MACD}: дописывается в эталон, в котором его нет. */
    private static final String MACD = indicator("MACD",
            "\"fastPeriod\": 12, \"slowPeriod\": 26, \"signalPeriod\": 9");

    /** Полное объявление {@code OBV}. */
    private static final String OBV = indicator("OBV", "\"enabled\": true");

    /** Полное объявление {@code BOLLINGER_BANDS}. */
    private static final String BOLLINGER_BANDS = indicator("BOLLINGER_BANDS",
            "\"period\": 20, \"deviationMultiplier\": 2.0");

    /** Полное объявление {@code EFFICIENCY_RATIO}. */
    private static final String EFFICIENCY_RATIO = indicator("EFFICIENCY_RATIO", "\"period\": 10");

    /** Блок размещения цены входного действия: структурная база эталонной структуры. */
    private static final String PLACEMENT = """
            {"baseType": "RANGE_LOW", "structureKey": "phase_structure_1h", "offsetSide": "ABOVE", "percents": 0.1}
            """;

    /** Тот же блок без базы. */
    private static final String PLACEMENT_WITHOUT_BASE = """
            {"structureKey": "phase_structure_1h", "offsetSide": "ABOVE", "percents": 0.1}
            """;

    @MethodSource("creationUnits")
    @ParameterizedTest(name = "{0}")
    void b11_aCreationWithoutAMandatoryUnitIsRefusedByTheContainer(String unit, String accepted, String omitted) {
        peerResolvesEverything();
        Answer whole = post(STRATEGIES, TENANT, accepted);
        assertThat(refusalClassOf(whole))
                .as("%s: вход без опущения проходит контейнер — иначе отказ не принадлежит единице; ответ %s %s",
                        unit, whole.status(), whole.body())
                .isNotEqualTo(NOT_ACCEPTED);
        Long definitions = rows.count(STRATEGIES_TABLE);
        Long outbox = rows.count(OUTBOX_TABLE);
        Integer peerRequests = peer.count();

        Answer answer = post(STRATEGIES, TENANT, omitted);

        assertRefusedByTheContainer(unit, answer, definitions, outbox, peerRequests);
    }

    @MethodSource("transitionUnits")
    @ParameterizedTest(name = "{0}")
    void b11_aTransitionWithoutAMandatoryUnitIsRefusedByTheContainer(String unit, String tenantHeader, String body) {
        peerResolvesEverything();
        String internalId = given(TENANT);
        Long definitions = rows.count(STRATEGIES_TABLE);
        Long outbox = rows.count(OUTBOX_TABLE);
        Integer peerRequests = peer.count();
        String path = STRATEGIES + "/" + internalId + "/status";

        Answer answer = Objects.isNull(tenantHeader)
                ? putWithoutTenant(path, body)
                : put(path, tenantHeader, body);

        assertRefusedByTheContainer(unit, answer, definitions, outbox, peerRequests);
        assertThat(statusOf(internalId, TENANT)).as("%s: статус определения прежний", unit).isEqualTo("CREATED");
    }

    @MethodSource("readUnits")
    @ParameterizedTest(name = "{0}")
    void b11_aReadWithoutTheContextHeaderIsRefusedByTheContainer(String unit, String path, String tenantHeader) {
        Long definitions = rows.count(STRATEGIES_TABLE);
        Long outbox = rows.count(OUTBOX_TABLE);
        Integer peerRequests = peer.count();

        Answer answer = Objects.isNull(tenantHeader) ? getWithoutTenant(path) : get(path, tenantHeader);

        assertRefusedByTheContainer(unit, answer, definitions, outbox, peerRequests);
    }

    /**
     * Строки создания: метка с единицей, вход без опущения и вход без
     * единицы. Узлы, которые строка называет «первыми», берутся указателем
     * на первый элемент; входное действие и его шаг — признаком рода ордера.
     */
    static Stream<Arguments> creationUnits() {
        String reference = Bodies.reference();
        return Stream.of(
                Arguments.of("B11.1 — CreateStrategyApiRequest.exchangeAccountInternalId", reference,
                        Bodies.referenceWithout(at(ROOT), "exchangeAccountInternalId")),
                Arguments.of("B11.2 — CreateStrategyApiRequest.instrumentInternalId", reference,
                        Bodies.referenceWithout(at(ROOT), "instrumentInternalId")),
                Arguments.of("B11.3 — CreateStrategyApiRequest.name", reference,
                        Bodies.referenceWithout(at(ROOT), "name")),
                Arguments.of("B11.4 — CreateStrategyApiRequest.marketPhaseSetting", reference,
                        Bodies.referenceWithout(at(ROOT), "marketPhaseSetting")),
                Arguments.of("B11.5 — CreateStrategyApiRequest.details", reference,
                        Bodies.referenceWithout(at(ROOT), "details")),
                Arguments.of("B11.6 — StrategyMarketPhaseSettingApiModel.phaseRules", reference,
                        Bodies.referenceWithout(at("/marketPhaseSetting"), "phaseRules")),
                Arguments.of("B11.7 — StrategyMarketPhaseRuleApiModel.type", reference,
                        Bodies.referenceWithout(at("/marketPhaseSetting/phaseRules/0"), "type")),
                Arguments.of("B11.8 — StrategyConditionRuleApiModel.level", reference,
                        Bodies.referenceWithout(at(PHASE_RULE), "level")),
                Arguments.of("B11.9 — StrategyConditionRuleApiModel.ruleType", reference,
                        Bodies.referenceWithout(at(PHASE_RULE), "ruleType")),
                Arguments.of("B11.10 — StrategyConditionOperandApiModel.sourceType", reference,
                        Bodies.referenceWithout(at(PHASE_RULE + "/leftOperand"), "sourceType")),
                Arguments.of("B11.11 — StrategyIndicatorSettingApiModel.key", reference,
                        Bodies.referenceWithout(at("/indicatorSettings/0"), "key")),
                Arguments.of("B11.12 — StrategyIndicatorSettingApiModel.indicatorType", reference,
                        Bodies.referenceWithout(at("/indicatorSettings/0"), "indicatorType")),
                Arguments.of("B11.13 — StrategyIndicatorSettingApiModel.params", reference,
                        Bodies.referenceWithout(at("/indicatorSettings/0"), "params")),
                Arguments.of("B11.14 — StrategyIndicatorSettingApiModel.destiny", reference,
                        Bodies.referenceWithout(at("/indicatorSettings/0"), "destiny")),
                Arguments.of("B11.15 — IndicatorParamsApiModel.timeframe", reference,
                        Bodies.referenceWithout(at("/indicatorSettings/0/params"), "timeframe")),
                Arguments.of("B11.16 — EmaParamsApiModel.period", reference,
                        Bodies.referenceWithout(paramsOf("EMA"), "period")),
                Arguments.of("B11.17 — RsiParamsApiModel.period", reference,
                        Bodies.referenceWithout(paramsOf("RSI"), "period")),
                Arguments.of("B11.18 — AtrParamsApiModel.period", reference,
                        Bodies.referenceWithout(paramsOf("ATR"), "period")),
                Arguments.of("B11.19 — MacdParamsApiModel.fastPeriod", withIndicator(MACD),
                        withIndicator(indicator("MACD", "\"slowPeriod\": 26, \"signalPeriod\": 9"))),
                Arguments.of("B11.20 — MacdParamsApiModel.slowPeriod", withIndicator(MACD),
                        withIndicator(indicator("MACD", "\"fastPeriod\": 12, \"signalPeriod\": 9"))),
                Arguments.of("B11.21 — MacdParamsApiModel.signalPeriod", withIndicator(MACD),
                        withIndicator(indicator("MACD", "\"fastPeriod\": 12, \"slowPeriod\": 26"))),
                Arguments.of("B11.22 — ObvParamsApiModel.enabled", withIndicator(OBV),
                        withIndicator(indicator("OBV", ""))),
                Arguments.of("B11.26 — BollingerBandsParamsApiModel.period", withIndicator(BOLLINGER_BANDS),
                        withIndicator(indicator("BOLLINGER_BANDS", "\"deviationMultiplier\": 2.0"))),
                Arguments.of("B11.27 — BollingerBandsParamsApiModel.deviationMultiplier",
                        withIndicator(BOLLINGER_BANDS),
                        withIndicator(indicator("BOLLINGER_BANDS", "\"period\": 20"))),
                Arguments.of("B11.28 — EfficiencyRatioParamsApiModel.period", withIndicator(EFFICIENCY_RATIO),
                        withIndicator(indicator("EFFICIENCY_RATIO", ""))),
                Arguments.of("B11.29 — StrategyMarketStructureSettingApiModel.key", reference,
                        Bodies.referenceWithout(at("/marketStructureSettings/0"), "key")),
                Arguments.of("B11.30 — StrategyMarketStructureSettingApiModel.timeframe", reference,
                        Bodies.referenceWithout(at("/marketStructureSettings/0"), "timeframe")),
                Arguments.of("B11.31 — StrategyMarketStructureSettingApiModel.params", reference,
                        Bodies.referenceWithout(at("/marketStructureSettings/0"), "params")),
                Arguments.of("B11.32 — StrategyMarketStructureSettingApiModel.destiny", reference,
                        Bodies.referenceWithout(at("/marketStructureSettings/0"), "destiny")),
                Arguments.of("B11.33 — StrategyDetailApiModel.marketPhaseType", reference,
                        Bodies.referenceWithout(at("/details/0"), "marketPhaseType")),
                Arguments.of("B11.34 — StrategyDetailApiModel.phaseEntryPolicy", reference,
                        Bodies.referenceWithout(at("/details/0"), "phaseEntryPolicy")),
                Arguments.of("B11.35 — StrategyTrancheApiModel.key", reference,
                        Bodies.referenceWithout(at("/details/0/tranches/0"), "key")),
                Arguments.of("B11.36 — StrategyStepApiModel.stepType", reference,
                        Bodies.referenceWithout(Bodies::entryStep, "stepType")),
                Arguments.of("B11.37 — StrategyStepApiModel.marketDataExpiredSetting", reference,
                        Bodies.referenceWithout(Bodies::entryStep, "marketDataExpiredSetting")),
                Arguments.of("B11.38 — StrategyMarketDataExpiredSettingApiModel.protectedPositionAction",
                        reference, Bodies.referenceWithout(entryStepExpiredSetting(), "protectedPositionAction")),
                Arguments.of("B11.39 — StrategyMarketDataExpiredSettingApiModel.unprotectedPositionAction",
                        reference, Bodies.referenceWithout(entryStepExpiredSetting(), "unprotectedPositionAction")),
                Arguments.of("B11.40 — StrategyActionApiModel.key", reference,
                        Bodies.referenceWithout(Bodies::entryAction, "key")),
                Arguments.of("B11.41 — StrategyActionApiModel.actionType", reference,
                        Bodies.referenceWithout(Bodies::entryAction, "actionType")),
                Arguments.of("B11.42 — StrategyOrderActionApiModel.orderType", reference,
                        Bodies.referenceWithout(Bodies::entryAction, "orderType")),
                Arguments.of("B11.43 — StrategyOrderActionApiModel.direction", reference,
                        Bodies.referenceWithout(Bodies::entryAction, "direction")),
                Arguments.of("B11.44 — StrategyPricePlacementApiModel.baseType",
                        Bodies.referenceWithField(Bodies::entryAction, "placement", PLACEMENT),
                        Bodies.referenceWithField(Bodies::entryAction, "placement", PLACEMENT_WITHOUT_BASE)),
                Arguments.of("B11.45 — StrategyAttachedProtectionSettingsApiModel.attachedType", reference,
                        Bodies.referenceWithout(attachedProtection(), "attachedType")),
                Arguments.of("B11.46 — StrategyAttachedProtectionSettingsApiModel.stopLossSettings", reference,
                        Bodies.referenceWithout(attachedProtection(), "stopLossSettings")),
                Arguments.of("B11.47 — StopLossSettingsApiModel.calculationType", reference,
                        Bodies.referenceWithout(attachedStopLoss(), "calculationType")),
                Arguments.of("B11.48 — StopLossSettingsApiModel.triggerPriceType", reference,
                        Bodies.referenceWithout(attachedStopLoss(), "triggerPriceType")),
                Arguments.of("B11.49 — StrategyAlgoOrderActionApiModel.conditionType", reference,
                        Bodies.referenceWithout(tree -> Bodies.firstAction(tree,
                                action -> Objects.equals("ALGO_ORDER", action.path("actionKind").asText())),
                                "conditionType")),
                Arguments.of("B11.50 — TrailingSettingsApiModel.callbackPercents", reference,
                        Bodies.referenceWithout(tree -> Bodies.firstAction(tree,
                                action -> action.hasNonNull("trailingSettings")).get("trailingSettings"),
                                "callbackPercents")),
                Arguments.of("B11.52 — тело точки создания", reference, ""));
    }

    /**
     * Строки перехода: метка с единицей, заголовок контекста ({@code null} —
     * не предъявлен) и тело.
     */
    static Stream<Arguments> transitionUnits() {
        return Stream.of(
                Arguments.of("B11.51 — UpdateStrategyStatusApiRequest.status", TENANT, "{}"),
                Arguments.of("B11.53 — тело точки перехода", TENANT, ""),
                Arguments.of("B11.58 — заголовок X-Tenant-Id перехода — присутствие", null, ACTIVATION),
                Arguments.of("B11.59 — заголовок X-Tenant-Id перехода — непустота", "", ACTIVATION));
    }

    /**
     * Строки чтения: метка с единицей, путь и заголовок контекста
     * ({@code null} — не предъявлен).
     */
    static Stream<Arguments> readUnits() {
        String one = STRATEGIES + "/" + ABSENT_DEFINITION;
        return Stream.of(
                Arguments.of("B11.54 — заголовок X-Tenant-Id перечня — присутствие", STRATEGIES, null),
                Arguments.of("B11.55 — заголовок X-Tenant-Id перечня — непустота", STRATEGIES, ""),
                Arguments.of("B11.56 — заголовок X-Tenant-Id чтения одного — присутствие", one, null),
                Arguments.of("B11.57 — заголовок X-Tenant-Id чтения одного — непустота", one, ""));
    }

    /**
     * Ожидание всех строк группы: отказ контейнера единым error-DTO и ни
     * одного следа — ни строки определения, ни строки outbox, ни запроса к
     * соседу.
     */
    private void assertRefusedByTheContainer(String unit, Answer answer, Long definitions, Long outbox,
                                             Integer peerRequests) {
        assertThat(answer.status())
                .as("%s: число ставит контейнер; ответ %s", unit, answer.body())
                .isEqualTo(400);
        assertThat(answer.carriesErrorDto()).as("%s: тело — единый error-DTO; ответ %s", unit, answer.body()).isTrue();
        assertThat(answer.errorCode())
                .as("%s: отказ контейнера — не валидатор создания, не сосед и не непредусмотренное", unit)
                .isEqualTo(NOT_ACCEPTED);
        assertThat(rows.count(STRATEGIES_TABLE)).as("%s: строк определения не прибавилось", unit)
                .isEqualTo(definitions);
        assertThat(rows.count(OUTBOX_TABLE)).as("%s: строк outbox не прибавилось", unit).isEqualTo(outbox);
        assertThat(peer.count()).as("%s: к ядру не ушло ни одного запроса", unit).isEqualTo(peerRequests);
    }

    /** Класс отказа ответа; пусто — ответ не отказ единым error-DTO. */
    private static String refusalClassOf(Answer answer) {
        return isTrue(answer.carriesErrorDto()) ? answer.errorCode() : null;
    }

    /** Эталон с дописанным объявлением индикатора. */
    private static String withIndicator(String declaration) {
        return Bodies.referenceWithElement(at("/indicatorSettings"), declaration);
    }

    /**
     * Объявление индикатора, которого в эталоне нет: таймфрейм и назначение
     * штатные, параметры подтипа — как названы.
     *
     * @param indicatorType тип индикатора — он же внешний тег подтипа параметров
     * @param ownParams     собственные параметры подтипа, без таймфрейма; пусто — их нет
     */
    private static String indicator(String indicatorType, String ownParams) {
        String params = ownParams.isEmpty() ? "" : ", " + ownParams;
        return """
                {"key": "%s_probe", "indicatorType": "%s",
                 "params": {"timeframe": "FIFTEEN_MINUTES"%s},
                 "destiny": "ENTRY_CONDITION", "expirationDuration": "PT30M"}
                """.formatted(indicatorType.toLowerCase(Locale.ROOT), indicatorType, params);
    }

    /** Локатор узла по указателю JSON. */
    private static Function<ObjectNode, JsonNode> at(String pointer) {
        return tree -> tree.at(pointer);
    }

    /** Параметры первого объявления индикатора названного типа. */
    private static Function<ObjectNode, JsonNode> paramsOf(String indicatorType) {
        return tree -> {
            for (JsonNode declaration : tree.path("indicatorSettings")) {
                if (Objects.equals(indicatorType, declaration.path("indicatorType").asText())) {
                    return declaration.get("params");
                }
            }
            throw new IllegalStateException("В эталоне нет объявления " + indicatorType);
        };
    }

    /** Настройка устаревших данных входного шага. */
    private static Function<ObjectNode, JsonNode> entryStepExpiredSetting() {
        return tree -> Bodies.entryStep(tree).get("marketDataExpiredSetting");
    }

    /** Встроенная защита входного действия. */
    private static Function<ObjectNode, JsonNode> attachedProtection() {
        return tree -> Bodies.entryAction(tree).get("attachedProtection");
    }

    /** Настройки стопа встроенной защиты входного действия. */
    private static Function<ObjectNode, JsonNode> attachedStopLoss() {
        return tree -> Bodies.entryAction(tree).get("attachedProtection").get("stopLossSettings");
    }
}
