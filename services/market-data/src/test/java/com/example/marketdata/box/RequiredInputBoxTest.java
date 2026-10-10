package com.example.marketdata.box;

import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Группа {@code B11} документа кейсов: обязательный вход не предъявлен — по
 * единице (.claude/tests/cases/market-data.md).
 *
 * <p><b>Случай — строка документа, и имя случая несёт её метку.</b> Отказ у
 * всех строк производит контейнер — биндинг и валидация аргумента, раньше
 * тела обработчика, — поэтому группа есть один параметризованный тест на
 * форму вызова, а красный прогон называет единицу, потерявшую охрану.
 *
 * <p><b>Вход строки — МУТАЦИЯ вызова, который контейнер принимает, и обе
 * стороны проверяются в одном случае.</b> Сперва подаётся вызов без
 * опущения, и его отказ классом контейнера роняет случай как непоставленное
 * предусловие; затем — тот же вызов без названной единицы. Без первой
 * половины ожидание «отказ контейнера» было бы истинно и на вызове, который
 * контейнер отвергает целиком по чужой причине (негодный срок, опечатка в
 * имени поля), и строка мерила бы её, а не единицу. Первая половина проходит
 * дальше контейнера — требование индикатора и структуры заводит
 * идентичность вычисления, — поэтому отрицания второй берутся разностью от
 * неё.
 *
 * <p><b>Инструмент и идентичность вычисления годны по форме и неизвестны
 * каталогу.</b> Отказ контейнера состояния базы не видит, а без охраны
 * исполнитель ответил бы на них классом {@code INVALID_REQUEST}: утрата
 * охраны видна классом, и предусловия в каталоге строке не нужны.
 */
@DisplayName("B11 — Обязательный вход не предъявлен: по единице")
class RequiredInputBoxTest extends SharedMarketDataBox {

    /** Класс отказа контейнера: непредъявленная обязательная часть вызова. */
    private static final String NOT_ACCEPTED = "REQUEST_NOT_ACCEPTED";

    /** Инструмент, годный по форме идентичности и неизвестный каталогу. */
    private static final String ABSENT_INSTRUMENT = "00000000-0000-4000-8000-000000000000";

    /** Идентичность вычисления, годная по форме и неизвестная реестру. */
    private static final String ABSENT_CONFIG = "00000000-0000-4000-8000-000000000001";

    /** Параметры индикатора годного требования. */
    private static final String EMA_PARAMS = "{\"period\": 14}";

    /** Срок свежести годного вызова. */
    private static final String TOLERANCE = "PT5M";

    /** Авторское имя привязки фичи. */
    private static final String BINDING_KEY = "быстрый";

    /** Глубина годного требования свечей. */
    private static final Long DEPTH_BARS = 300L;

    /** Путь чтения фич неизвестного инструмента. */
    private static final String FEATURES = INSTRUMENTS + "/" + ABSENT_INSTRUMENT + "/features";

    /** Путь чтения истории свечей неизвестного инструмента. */
    private static final String CANDLES = INSTRUMENTS + "/" + ABSENT_INSTRUMENT + "/candles";

    /** Путь одиночного чтения индикатора неизвестного инструмента. */
    private static final String LATEST_INDICATOR = INSTRUMENTS + "/" + ABSENT_INSTRUMENT
            + "/indicator-values/latest";

    /** Путь одиночного чтения структуры неизвестного инструмента. */
    private static final String LATEST_STRUCTURE = INSTRUMENTS + "/" + ABSENT_INSTRUMENT
            + "/market-structures/latest";

    @MethodSource("bodyUnits")
    @ParameterizedTest(name = "{0}")
    void b11_aBodyWithoutAMandatoryUnitIsRefusedByTheContainer(String unit, String path, String accepted,
                                                               String omitted) {
        Answer whole = post(path, accepted);
        assertAcceptedByTheContainer(unit, whole);
        Map<String, Long> tables = rows.countsByTable();
        Integer connectorRequests = connector.count();

        Answer answer = post(path, omitted);

        assertRefusedByTheContainer(unit, answer, tables, connectorRequests);
    }

    @MethodSource("parameterUnits")
    @ParameterizedTest(name = "{0}")
    void b11_aReadWithoutAMandatoryParameterIsRefusedByTheContainer(String unit, String accepted, String omitted) {
        Answer whole = get(accepted);
        assertAcceptedByTheContainer(unit, whole);
        Map<String, Long> tables = rows.countsByTable();
        Integer connectorRequests = connector.count();

        Answer answer = get(omitted);

        assertRefusedByTheContainer(unit, answer, tables, connectorRequests);
    }

    /** Строки тела: метка с единицей, путь, тело без опущения и тело без единицы. */
    static Stream<Arguments> bodyUnits() {
        String candles = REQUIREMENTS + "/candles";
        String indicators = REQUIREMENTS + "/indicators";
        String structures = REQUIREMENTS + "/market-structures";
        String structureParams = Bodies.structureParams(96);
        String features = Bodies.featureRead(
                Bodies.array(Bodies.binding(BINDING_KEY, ABSENT_CONFIG, TOLERANCE)), "[]", Boolean.FALSE);
        return Stream.of(
                Arguments.of("B11.1 — CandleRequirementApiRequest.instrumentInternalId", candles,
                        Bodies.candleRequirement(ABSENT_INSTRUMENT, HOUR, DEPTH_BARS),
                        "{\"timeframe\": \"%s\", \"depthBars\": %d}".formatted(HOUR, DEPTH_BARS)),
                Arguments.of("B11.2 — CandleRequirementApiRequest.timeframe", candles,
                        Bodies.candleRequirement(ABSENT_INSTRUMENT, HOUR, DEPTH_BARS),
                        "{\"instrumentInternalId\": \"%s\", \"depthBars\": %d}"
                                .formatted(ABSENT_INSTRUMENT, DEPTH_BARS)),
                Arguments.of("B11.3 — IndicatorConfigApiRequest.indicatorType", indicators,
                        Bodies.indicatorRequirement("EMA", HOUR, EMA_PARAMS),
                        "{\"timeframe\": \"%s\", \"params\": %s}".formatted(HOUR, EMA_PARAMS)),
                Arguments.of("B11.4 — IndicatorConfigApiRequest.timeframe", indicators,
                        Bodies.indicatorRequirement("EMA", HOUR, EMA_PARAMS),
                        "{\"indicatorType\": \"EMA\", \"params\": %s}".formatted(EMA_PARAMS)),
                Arguments.of("B11.5 — IndicatorConfigApiRequest.params", indicators,
                        Bodies.indicatorRequirement("EMA", HOUR, EMA_PARAMS),
                        "{\"indicatorType\": \"EMA\", \"timeframe\": \"%s\"}".formatted(HOUR)),
                Arguments.of("B11.6 — MarketStructureConfigApiRequest.timeframe", structures,
                        Bodies.structureRequirement(HOUR, structureParams, null, null),
                        "{\"params\": %s}".formatted(structureParams)),
                Arguments.of("B11.7 — MarketStructureConfigApiRequest.params", structures,
                        Bodies.structureRequirement(HOUR, structureParams, null, null),
                        "{\"timeframe\": \"%s\"}".formatted(HOUR)),
                Arguments.of("B11.8 — FeatureBindingApiRequest.key", FEATURES, features,
                        Bodies.featureRead(Bodies.array("{\"configInternalId\": \"%s\", \"tolerance\": \"%s\"}"
                                .formatted(ABSENT_CONFIG, TOLERANCE)), "[]", Boolean.FALSE)),
                Arguments.of("B11.9 — FeatureBindingApiRequest.configInternalId", FEATURES, features,
                        Bodies.featureRead(Bodies.array("{\"key\": \"%s\", \"tolerance\": \"%s\"}"
                                .formatted(BINDING_KEY, TOLERANCE)), "[]", Boolean.FALSE)),
                Arguments.of("B11.10 — FeatureBindingApiRequest.tolerance", FEATURES, features,
                        Bodies.featureRead(Bodies.array("{\"key\": \"%s\", \"configInternalId\": \"%s\"}"
                                .formatted(BINDING_KEY, ABSENT_CONFIG)), "[]", Boolean.FALSE)));
    }

    /** Строки параметров: метка с единицей, вызов без опущения и вызов без единицы. */
    static Stream<Arguments> parameterUnits() {
        String history = CANDLES + "?timeframe=" + HOUR + "&fromMillis=0&limit=60";
        String indicator = LATEST_INDICATOR + "?configInternalId=" + ABSENT_CONFIG + "&tolerance=" + TOLERANCE;
        String structure = LATEST_STRUCTURE + "?configInternalId=" + ABSENT_CONFIG + "&tolerance=" + TOLERANCE;
        return Stream.of(
                Arguments.of("B11.11 — CandleHistoryApiQuery.timeframe", history,
                        CANDLES + "?fromMillis=0&limit=60"),
                Arguments.of("B11.12 — CandleHistoryApiQuery.fromMillis", history,
                        CANDLES + "?timeframe=" + HOUR + "&limit=60"),
                Arguments.of("B11.13 — параметр configInternalId одиночного чтения индикатора", indicator,
                        LATEST_INDICATOR + "?tolerance=" + TOLERANCE),
                Arguments.of("B11.14 — параметр tolerance одиночного чтения индикатора", indicator,
                        LATEST_INDICATOR + "?configInternalId=" + ABSENT_CONFIG),
                Arguments.of("B11.15 — параметр configInternalId одиночного чтения структуры", structure,
                        LATEST_STRUCTURE + "?tolerance=" + TOLERANCE),
                Arguments.of("B11.16 — параметр tolerance одиночного чтения структуры", structure,
                        LATEST_STRUCTURE + "?configInternalId=" + ABSENT_CONFIG));
    }

    /**
     * Предусловие строки: вызов без опущения контейнер принимает — иначе
     * отказ второй половины не принадлежит единице.
     */
    private static void assertAcceptedByTheContainer(String unit, Answer whole) {
        String refusalClass = isTrue(whole.carriesErrorDto()) ? whole.errorCode() : null;
        assertThat(refusalClass)
                .as("%s: вызов без опущения проходит контейнер; ответ %s %s", unit, whole.status(), whole.body())
                .isNotEqualTo(NOT_ACCEPTED);
    }

    /**
     * Ожидание всех строк группы: отказ контейнера единым error-DTO и ни
     * одного следа — ни строки в таблицах схемы, ни запроса к коннектору.
     */
    private void assertRefusedByTheContainer(String unit, Answer answer, Map<String, Long> tables,
                                             Integer connectorRequests) {
        assertThat(answer.status()).as("%s: число ставит контейнер; ответ %s", unit, answer.body()).isEqualTo(400);
        assertThat(answer.carriesErrorDto()).as("%s: тело — единый error-DTO; ответ %s", unit, answer.body()).isTrue();
        assertThat(answer.errorCode())
                .as("%s: отказ контейнера — не исполнитель и не непредусмотренное", unit)
                .isEqualTo(NOT_ACCEPTED);
        assertThat(rows.countsByTable())
                .as("%s: строк каталога, реестров и единиц сбора не прибавилось", unit)
                .isEqualTo(tables);
        assertThat(connector.count()).as("%s: к коннектору не ушло ни одного запроса", unit)
                .isEqualTo(connectorRequests);
    }
}
