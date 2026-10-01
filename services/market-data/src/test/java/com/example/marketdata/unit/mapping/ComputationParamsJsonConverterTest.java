package com.example.marketdata.unit.mapping;

import static com.example.testsupport.StrategyOverlayCopyContract.INDICATOR_PARAMS_KEYS;
import static com.example.testsupport.StrategyOverlayCopyContract.MARKET_STRUCTURE_PARAMS_KEYS;
import static com.example.testsupport.StrategyOverlayCopyContract.indicatorParamsOfEverySubtype;
import static com.example.testsupport.StrategyOverlayCopyContract.marketStructureParamsWithEveryField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketdata.mapping.ComputationParamsJsonConverter;
import com.example.testsupport.JsonbOverlayProbe;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.AtrParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MacdParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Параметры вычисления: каноническая форма против хранимой.
 *
 * <p>Кейсы — группа `U9` и клетки `U11.7`, `U13.5`
 * (.claude/tests/cases/jsonb-overlay-roundtrip.md). Каноническая форма —
 * операнд ключа уникальности реестра идентичностей, и сравнивается она
 * ДОСЛОВНО, строка со строкой: разойдись она с собой, одна идентичность
 * завела бы в реестре две строки.
 *
 * <p><b>Литеральные перечни ключей `U13.5` берутся у формы, а не
 * переписываются здесь:</b> параметры индикатора и структуры рынка пишет и
 * конвертер дерева стратегии, и у перечня одной формы один носитель —
 * {@code StrategyOverlayCopyContract} (.claude/rules/carrier-levels.md).
 */
class ComputationParamsJsonConverterTest extends JsonbOverlayProbe {

    private final ComputationParamsJsonConverter converter =
            new ComputationParamsJsonConverter(beanAssemblyMapper());

    @Test
    @DisplayName("U9.1 — разный порядок присваиваний даёт дословно совпадающую каноническую форму")
    void u9_1_aDifferentAssignmentOrderYieldsTheSameCanonicalForm() {
        AtrParams straight = new AtrParams();
        straight.setTimeframe(TimeFrame.ONE_HOUR);
        straight.setWarmup(50);
        straight.setPeriod(14);

        AtrParams reversed = new AtrParams();
        reversed.setPeriod(14);
        reversed.setWarmup(50);
        reversed.setTimeframe(TimeFrame.ONE_HOUR);

        assertThat(converter.paramsToCanonical(reversed))
                .isEqualTo(converter.paramsToCanonical(straight));
    }

    /**
     * Вторая половина клетки — таймфрейм — пришла сюда с перенесённой
     * пробой идентичности вычисления: она проверяла то же свойство на
     * СВЕЖЕМ маппере, то есть на сборке, которой у конвертера в проде нет
     * (§«Существующий набор предмета»). Свойство сохранено, сборка
     * переякорена.
     */
    @Test
    @DisplayName("U9.2 — параметры, отличающиеся одним операндом, дают разные канонические формы")
    void u9_2_paramsDifferingInOneOperandYieldDifferentCanonicalForms() {
        AtrParams shorter = atrParams();
        shorter.setPeriod(7);

        AtrParams daily = atrParams();
        daily.setTimeframe(TimeFrame.ONE_DAY);

        assertThat(converter.paramsToCanonical(shorter))
                .isNotEqualTo(converter.paramsToCanonical(atrParams()));
        assertThat(converter.paramsToCanonical(daily))
                .as("тот же расчёт на другом таймфрейме — другая идентичность")
                .isNotEqualTo(converter.paramsToCanonical(atrParams()));
    }

    @Test
    @DisplayName("U9.3 — пустого ключа нет ни в канонической форме, ни в хранимой")
    void u9_3_anEmptyFieldIsAbsentFromBothForms() {
        AtrParams withoutWarmup = atrParams();
        withoutWarmup.setWarmup(null);

        assertThat(keysOf(converter.paramsToCanonical(withoutWarmup))).doesNotContain("warmup");
        assertThat(keysOf(converter.paramsToJson(withoutWarmup))).doesNotContain("warmup");
    }

    @Test
    @DisplayName("U9.4 — формы отвечают на РАЗНЫЕ вопросы, обе разбираются в тождественный объект")
    void u9_4_theTwoFormsAnswerDifferentQuestionsAndBothParseBack() {
        AtrParams params = atrParams();

        String stored = converter.paramsToJson(params);
        String canonical = converter.paramsToCanonical(params);

        assertThat(stored).isNotEmpty();
        assertThat(canonical).isNotEmpty();
        assertThat(converter.jsonToIndicatorParams(stored, IndicatorValue.Type.ATR))
                .usingRecursiveComparison().isEqualTo(params);
        assertThat(converter.jsonToIndicatorParams(canonical, IndicatorValue.Type.ATR))
                .usingRecursiveComparison().isEqualTo(params);
    }

    @Test
    @DisplayName("U9.5 — сырая форма при названном типе даёт объект объявленного подтипа")
    void u9_5_aRawShapeUnderTheNamedTypeYieldsTheDeclaredSubtype() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("timeframe", "ONE_HOUR");
        raw.put("warmup", 50);
        raw.put("period", 14);

        IndicatorParams converted = converter.toIndicatorParams(raw, IndicatorValue.Type.ATR);

        assertThat(converted).isInstanceOf(AtrParams.class)
                .usingRecursiveComparison().isEqualTo(atrParams());
    }

    @Test
    @DisplayName("U9.6 — нечисловая строка в числовом поле: свой класс с именем целевого")
    void u9_6_aNonNumericValueFailsWithTheOwnMessageNamingTheTargetClass() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("period", "четырнадцать");

        assertThatThrownBy(() -> converter.toIndicatorParams(raw, IndicatorValue.Type.ATR))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AtrParams")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Сырая форма — тело требования, и читатель у неё один: поверхность
     * требования. Поле чужого подтипа там не отбрасывается, а отказывает —
     * иначе идентичность завелась бы с пустым операндом, и отказ пришёл бы
     * у тика расчёта (находка {@code F-10} `.claude/tests/cases/market-data.md`).
     */
    @Test
    @DisplayName("U9.7 — неизвестное поле сырой формы отказывает своим классом с именем целевого")
    void u9_7_anUnknownFieldOfTheRawShapeIsRefused() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("timeframe", "ONE_HOUR");
        raw.put("warmup", 50);
        raw.put("period", 14);
        raw.put("smoothing", "wilder");

        assertThatThrownBy(() -> converter.toIndicatorParams(raw, IndicatorValue.Type.ATR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AtrParams");
    }

    @Test
    @DisplayName("U9.8 — четыре формы пустоты дают пустоту без отказа")
    void u9_8_fourShapesOfEmptinessYieldEmptiness() {
        assertThat(converter.toIndicatorParams(null, IndicatorValue.Type.ATR)).isNull();
        assertThat(converter.toIndicatorParams(Map.of(), null)).isNull();
        assertThat(converter.jsonToIndicatorParams(null, IndicatorValue.Type.ATR)).isNull();
        assertThat(converter.jsonToIndicatorParams("{}", null)).isNull();
    }

    @Test
    @DisplayName("U9.9 — у параметров структуры рынка своя пара входов, тип не участвует, чужое поле отказывает")
    void u9_9_marketStructureParamsHaveTheirOwnPairOfEntries() {
        MarketStructureParams params = marketStructureParams();

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("lookbackBars", 120);
        raw.put("minTouches", 3);
        raw.put("swingLookbackBars", 8);

        assertThat(converter.jsonToMarketStructureParams(converter.paramsToJson(params)))
                .usingRecursiveComparison().isEqualTo(params);
        assertThat(converter.toMarketStructureParams(raw))
                .usingRecursiveComparison().isEqualTo(params);

        raw.put("period", 14);
        assertThatThrownBy(() -> converter.toMarketStructureParams(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MarketStructureParams");
    }

    @Test
    @DisplayName("U9.10 — каноническая упорядочивает свойства алфавитом, хранимая — объявлением")
    void u9_10_theCanonicalFormSortsPropertiesAlphabetically() {
        MacdParams params = macdParams();

        assertThat(keysOf(converter.paramsToCanonical(params)))
                .containsExactly("fastPeriod", "signalPeriod", "slowPeriod", "timeframe", "warmup");
        assertThat(keysOf(converter.paramsToJson(params)))
                .as("хранимая форма оставляет порядок объявления — это и есть единственный "
                        + "наблюдаемый операнд канонического маппера")
                .isNotEqualTo(keysOf(converter.paramsToCanonical(params)));
    }

    @Test
    @DisplayName("U11.7 — на строгом источнике строка реестра терпима к лишнему ключу, сырая форма строга")
    void u11_7_onAStrictSourceTheRegistryRowIsTolerantAndTheRawShapeStaysStrict() {
        ObjectMapper strict = beanAssemblyMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        ComputationParamsJsonConverter onStrict = new ComputationParamsJsonConverter(strict);
        String withUnknown = converter.paramsToJson(atrParams())
                .replaceFirst("\\{", "{\"smoothing\":\"wilder\",");

        assertThat(onStrict.jsonToIndicatorParams(withUnknown, IndicatorValue.Type.ATR))
                .as("U11.7: вход %s, источник строгий — терпимость строки реестра запинена "
                        + "конвертером", withUnknown)
                .usingRecursiveComparison().isEqualTo(atrParams());

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("period", 14);
        raw.put("smoothing", "wilder");
        assertThatThrownBy(() -> onStrict.toIndicatorParams(raw, IndicatorValue.Type.ATR))
                .as("U11.7: разбор сырой формы запроса остаётся строгим (U9.7)")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AtrParams");
    }

    @Test
    @DisplayName("U13.5 — хранимая и каноническая формы: множество ключей одно и равно литеральному перечню")
    void u13_5_bothFormsCarryTheSameLiteralKeySet() {
        for (IndicatorParams params : indicatorParamsOfEverySubtype()) {
            List<String> expected = INDICATOR_PARAMS_KEYS.get(params.getClass());
            assertThat(keysOf(converter.paramsToJson(params)))
                    .as("U13.5: хранимая форма, подтип %s", params.getClass().getSimpleName())
                    .containsExactlyInAnyOrderElementsOf(expected);
            assertThat(keysOf(converter.paramsToCanonical(params)))
                    .as("U13.5: каноническая форма, подтип %s", params.getClass().getSimpleName())
                    .containsExactlyInAnyOrderElementsOf(expected);
        }
        MarketStructureParams structure = marketStructureParamsWithEveryField();
        assertThat(keysOf(converter.paramsToJson(structure)))
                .as("U13.5: хранимая форма параметров структуры рынка")
                .containsExactlyInAnyOrderElementsOf(MARKET_STRUCTURE_PARAMS_KEYS);
        assertThat(keysOf(converter.paramsToCanonical(structure)))
                .as("U13.5: каноническая форма параметров структуры рынка")
                .containsExactlyInAnyOrderElementsOf(MARKET_STRUCTURE_PARAMS_KEYS);
    }

    private static AtrParams atrParams() {
        AtrParams params = new AtrParams(14);
        params.setTimeframe(TimeFrame.ONE_HOUR);
        params.setWarmup(50);
        return params;
    }

    private static MacdParams macdParams() {
        MacdParams params = new MacdParams(12, 26, 9);
        params.setTimeframe(TimeFrame.ONE_HOUR);
        params.setWarmup(50);
        return params;
    }

    private static MarketStructureParams marketStructureParams() {
        MarketStructureParams params = new MarketStructureParams();
        params.setLookbackBars(120);
        params.setMinTouches(3);
        params.setSwingLookbackBars(8);
        return params;
    }
}
