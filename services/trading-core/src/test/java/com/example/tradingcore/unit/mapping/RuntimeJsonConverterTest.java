package com.example.tradingcore.unit.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testsupport.JsonbOverlayProbe;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trailing;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingcore.domain.command.RetryError;
import com.example.tradingcore.domain.command.RuntimeErrorCode;
import com.example.tradingcore.mapping.RuntimeJsonConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Навес runtime-строк ядра: дерево условия отдельной условной заявки,
 * последняя ошибка строки исполнения и перечень внешних идентификаторов.
 *
 * <p>Кейсы — группы `U1`, `U2`, `U3`, а также `U11` (строка, записанная
 * прежней редакцией формы) и `U12` (отсутствие выходов), которые документ
 * ставит на любой из семи конвертеров
 * (.claude/tests/cases/jsonb-overlay-roundtrip.md).
 */
class RuntimeJsonConverterTest extends JsonbOverlayProbe {

    private final RuntimeJsonConverter converter = new RuntimeJsonConverter(beanAssemblyMapper());

    // --- U1: условие отдельной условной заявки ---------------------------

    @Test
    @DisplayName("U1.1 — триггерная ветка переживает запись, ключа трейлинга в строке нет")
    void u1_1_theTriggerBranchSurvivesAndTheTrailingKeyIsAbsent() {
        Condition condition = triggerCondition();

        String json = converter.conditionToJson(condition);

        assertThat(keysOf(json)).containsExactlyInAnyOrder("type", "trigger");
        Condition read = converter.jsonToCondition(json);
        assertThat(read).usingRecursiveComparison().isEqualTo(condition);
        assertThat(read.getTrailing()).isNull();
    }

    @Test
    @DisplayName("U1.2 — трейлинговая ветка без наблюдённого уровня: ключа уровня нет")
    void u1_2_theTrailingBranchWithoutAnObservedLevelCarriesNoLevelKey() {
        Condition condition = trailingCondition();

        String json = converter.conditionToJson(condition);

        assertThat(keysOf(json)).containsExactlyInAnyOrder("type", "trailing");
        assertThat(readTree(json).get("trailing").has("externalPrice")).isFalse();
        Condition read = converter.jsonToCondition(json);
        assertThat(read).usingRecursiveComparison().isEqualTo(condition);
        assertThat(read.getTrigger()).isNull();
        assertThat(read.getTrailing().getExternalPrice()).isNull();
    }

    @Test
    @DisplayName("U1.3 — наблюдённый уровень после активации переживает перезапись")
    void u1_3_anObservedLevelSurvivesTheRewrite() {
        Condition condition = trailingCondition();
        condition.getTrailing().setExternalPrice(new BigDecimal("31500.5"));

        Condition read = converter.jsonToCondition(converter.conditionToJson(condition));

        assertThat(read).usingRecursiveComparison().isEqualTo(condition);
        assertThat(read.getTrailing().getExternalPrice()).isEqualByComparingTo("31500.5");
    }

    @Test
    @DisplayName("U1.4 — пусто на записи даёт пустоту, а не строку и не отказ")
    void u1_4_anEmptyValueOnWriteYieldsEmptiness() {
        assertThat(converter.conditionToJson(null)).isNull();
    }

    @Test
    @DisplayName("U1.5 — пусто на чтении даёт пустоту")
    void u1_5_anEmptyValueOnReadYieldsEmptiness() {
        assertThat(converter.jsonToCondition(null)).isNull();
    }

    @Test
    @DisplayName("U1.6 — неразбираемая строка: свой класс, сторона в сообщении, причина сохранена")
    void u1_6_anUnparseableStringFailsWithTheOwnClassAndKeepsTheCause() {
        assertThatThrownBy(() -> converter.jsonToCondition("{\"type\":\"STOP_LOSS\""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Condition")
                .hasMessageContaining("deserialization")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("U1.7 — неизвестное поле отброшено, остальные тождественны")
    void u1_7_anUnknownFieldIsDroppedAndTheRestIsIdentical() {
        Condition condition = triggerCondition();
        String withUnknown = converter.conditionToJson(condition)
                .replaceFirst("\\{", "{\"algoClOrdId\":\"vtb1\",");

        assertThat(converter.jsonToCondition(withUnknown))
                .usingRecursiveComparison().isEqualTo(condition);
    }

    @Test
    @DisplayName("U1.8 — имя типа условия вне перечня отказывает с сохранённой причиной")
    void u1_8_anUnknownConditionTypeNameFails() {
        assertThatThrownBy(() -> converter.jsonToCondition("{\"type\":\"OCO_PARTIAL\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("U1.9 — навес инварианта «ровно один механизм» НЕ охраняет")
    void u1_9_theOverlayDoesNotGuardTheSingleMechanismInvariant() {
        Condition both = triggerCondition();
        both.setTrailing(trailingCondition().getTrailing());

        Condition read = converter.jsonToCondition(converter.conditionToJson(both));

        assertThat(read).usingRecursiveComparison().isEqualTo(both);
        assertThat(read.getTrigger()).isNotNull();
        assertThat(read.getTrailing())
                .as("охрана исключительности живёт ровно в одном месте — у модели, не у навеса")
                .isNotNull();
    }

    // --- U2: последняя ошибка строки исполнения --------------------------

    @Test
    @DisplayName("U2.1 — три компонента записи переживают запись, классификация уезжает именем")
    void u2_1_allThreeComponentsSurviveAndTheClassificationTravelsByName() {
        RetryError error = new RetryError("51008", "Insufficient balance",
                RuntimeErrorCode.EXCHANGE_ERROR);

        String json = converter.retryErrorToJson(error);

        assertThat(json).contains("\"EXCHANGE_ERROR\"");
        assertThat(converter.jsonToRetryError(json))
                .usingRecursiveComparison().isEqualTo(error);
    }

    @Test
    @DisplayName("U2.2 — пустой компонент сообщения в строку не пишется")
    void u2_2_anEmptyMessageComponentIsNotWritten() {
        RetryError error = new RetryError("51008", null, RuntimeErrorCode.EXCHANGE_ERROR);

        String json = converter.retryErrorToJson(error);

        assertThat(keysOf(json)).containsExactlyInAnyOrder("code", "type");
        assertThat(converter.jsonToRetryError(json)).usingRecursiveComparison().isEqualTo(error);
    }

    @Test
    @DisplayName("U2.3 — канонический конструктор записи принимает пустой компонент кода")
    void u2_3_theCanonicalRecordConstructorAcceptsAnEmptyCodeComponent() {
        RetryError read = converter.jsonToRetryError(
                "{\"message\":\"timeout\",\"type\":\"INTERNAL_ERROR\"}");

        assertThat(read.code()).isNull();
        assertThat(read.message()).isEqualTo("timeout");
        assertThat(read.type()).isEqualTo(RuntimeErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("U2.4 — имя классификации вне перечня: отказ на тропе ПОВТОРА")
    void u2_4_anUnknownClassificationNameFailsOnTheRetryPath() {
        assertThatThrownBy(() -> converter.jsonToRetryError(
                "{\"code\":\"1\",\"type\":\"THROTTLED\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Retry error")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("U2.5 — отсутствие компонента записи отказом не является")
    void u2_5_aMissingRecordComponentIsNotAFailure() {
        RetryError read = converter.jsonToRetryError("{\"code\":\"1\",\"message\":\"m\"}");

        assertThat(read.type()).isNull();
    }

    @Test
    @DisplayName("U2.6 — пустота зеркальна у обеих сторон записи")
    void u2_6_emptinessIsMirroredForTheRecordToo() {
        assertThat(converter.retryErrorToJson(null)).isNull();
        assertThat(converter.jsonToRetryError(null)).isNull();
    }

    // --- U3: перечень внешних идентификаторов ----------------------------

    @Test
    @DisplayName("U3.1 — порядок перечня переживает запись")
    void u3_1_theOrderOfTheListSurvivesTheWrite() {
        List<String> ids = List.of("vtb1", "vtb2");

        assertThat(converter.jsonToStringList(converter.stringListToJson(ids)))
                .containsExactly("vtb1", "vtb2");
    }

    @Test
    @DisplayName("U3.2 — пустой перечень остаётся пустым перечнем, а не пустотой")
    void u3_2_anEmptyListStaysAnEmptyListAndNotEmptiness() {
        String json = converter.stringListToJson(List.of());

        assertThat(json).isEqualTo("[]");
        assertThat(converter.jsonToStringList(json)).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("U3.3 — пустота и пустой перечень различимы, и различие переживает round-trip")
    void u3_3_emptinessAndAnEmptyListAreDistinguishable() {
        assertThat(converter.stringListToJson(null)).isNull();
        assertThat(converter.jsonToStringList(null)).isNull();
    }

    @Test
    @DisplayName("U3.4 — пустой массив строки читается пустым перечнем")
    void u3_4_anEmptyArrayIsReadAsAnEmptyList() {
        assertThat(converter.jsonToStringList("[]")).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("U3.5 — число вместо строки приводится умолчанием библиотеки")
    void u3_5_aNumberInsteadOfAStringIsCoercedByTheLibraryDefault() {
        assertThat(converter.jsonToStringList("[\"vtb1\",7]")).containsExactly("vtb1", "7");
    }

    @Test
    @DisplayName("U3.6 — объект вместо массива отказывает с сохранённой причиной")
    void u3_6_anObjectInsteadOfAnArrayFails() {
        assertThatThrownBy(() -> converter.jsonToStringList("{\"a\":1}"))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    // --- U11: строка, записанная прежней редакцией формы -----------------

    @Test
    @DisplayName("U11.1 — поле, снятое из формы, отброшено; читатель не падает")
    void u11_1_aFieldRemovedFromTheFormIsDroppedAndTheReaderDoesNotFail() {
        assertThat(converter.jsonToCondition(
                "{\"type\":\"STOP_LOSS\",\"orderPx\":\"-1\"}").getType())
                .isEqualTo(AlgoOrder.ConditionType.STOP_LOSS);
    }

    @Test
    @DisplayName("U11.2 — поля, добавленного в форму, строка не несёт: оно пусто, отказа нет")
    void u11_2_aFieldAddedToTheFormComesBackEmpty() {
        Condition read = converter.jsonToCondition("{\"type\":\"STOP_LOSS\"}");

        assertThat(read.getTrigger()).isNull();
        assertThat(read.getTrailing()).isNull();
    }

    @Test
    @DisplayName("U11.3 — переименование аксессора теряет записанное МОЛЧА")
    void u11_3_anAccessorRenameLosesTheWrittenValueSilently() {
        Condition read = converter.jsonToCondition(
                "{\"conditionType\":\"STOP_LOSS\",\"trigger\":{\"stopLoss\":{\"value\":10}}}");

        assertThat(read.getTrigger().getStopLoss().getValue()).isEqualByComparingTo("10");
        assertThat(read.getType())
                .as("прежнее имя ключа не совпало с нынешним аксессором: значение потеряно, "
                        + "и ни сборка, ни один прогон корпуса этого не видят")
                .isNull();
    }

    @Test
    @DisplayName("U11.4 — литерал пустоты читается как отсутствие значения")
    void u11_4_aNullLiteralIsReadAsAnAbsentValue() {
        assertThat(converter.jsonToCondition("null")).isNull();
    }

    @Test
    @DisplayName("U11.5 — пустая строка и пустая колонка — РАЗНЫЕ состояния у читателя")
    void u11_5_anEmptyStringIsNotTheSameAsAnEmptyColumn() {
        assertThatThrownBy(() -> converter.jsonToCondition(""))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("U11.6 — смена типа поля — единственная редакция, чей отказ читатель видит")
    void u11_6_aChangedFieldTypeIsTheOnlyRevisionTheReaderSees() {
        assertThatThrownBy(() -> converter.jsonToCondition(
                "{\"type\":\"STOP_LOSS\",\"trigger\":42}"))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    // --- U12: чего предмет не делает -------------------------------------

    @Test
    @DisplayName("U12.1 — у предмета нет ни базы, ни брокера, ни сети, ни часов, ни лога")
    void u12_1_thereIsNoIoInTheConverterSources() throws IOException {
        assertNoRuntimeIo(
                converterSource("RuntimeJsonConverter"),
                converterSource("StrategyJsonConverter"),
                converterSource("InstrumentExternalRulesJsonConverter"));
    }

    @Test
    @DisplayName("U12.2 — на отказе разбора значение по умолчанию не подставляется")
    void u12_2_noDefaultIsSubstitutedOnAParseFailure() {
        assertThatThrownBy(() -> converter.jsonToCondition("{\"type\":\"OCO_PARTIAL\"}"))
                .as("конвертер отказ ПОДНИМАЕТ: контраст — единственный писатель навеса вне "
                        + "предмета его поглощает и пишет лог")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("U12.3 — запись пустого значения даёт пустоту колонки, а не пустой объект")
    void u12_3_writingAnEmptyValueYieldsAnEmptyColumnNotAnEmptyObject() {
        assertThat(converter.conditionToJson(null)).isNull();
        assertThat(converter.retryErrorToJson(null)).isNull();
        assertThat(converter.stringListToJson(null)).isNull();
    }

    @Test
    @DisplayName("U12.4 — доменных инвариантов владельца конвертер не охраняет")
    void u12_4_theConverterGuardsNoDomainInvariantOfTheOwner() {
        Condition both = triggerCondition();
        both.setTrailing(trailingCondition().getTrailing());

        assertThat(converter.conditionToJson(both)).isNotNull();
        assertThat(converter.jsonToCondition("{}"))
                .as("ни одного поля не требуется: полноту формы держит владелец")
                .isNotNull();
    }

    // --- материал кейсов --------------------------------------------------

    private static Path converterSource(String simpleName) {
        return Path.of("src", "main", "java", "com", "example", "tradingcore", "mapping",
                simpleName + ".java");
    }

    private static Condition triggerCondition() {
        TriggerPrice stopLoss = new TriggerPrice();
        stopLoss.setType(AlgoOrder.TriggerPriceType.LAST);
        stopLoss.setValue(new BigDecimal("30000"));

        TriggerPrice takeProfit = new TriggerPrice();
        takeProfit.setType(AlgoOrder.TriggerPriceType.MARK);
        takeProfit.setValue(new BigDecimal("36000"));

        Condition condition = new Condition();
        condition.setType(AlgoOrder.ConditionType.OCO_FULL);
        condition.setTrigger(new Trigger(stopLoss, takeProfit));
        return condition;
    }

    private static Condition trailingCondition() {
        TriggerPrice activation = new TriggerPrice();
        activation.setType(AlgoOrder.TriggerPriceType.LAST);
        activation.setValue(new BigDecimal("33000"));

        Trailing trailing = new Trailing();
        trailing.setTrailingPercents(new BigDecimal("1.5"));
        trailing.setActivationPrice(activation);

        Condition condition = new Condition();
        condition.setType(AlgoOrder.ConditionType.TRAILING_PERCENTS);
        condition.setTrailing(trailing);
        return condition;
    }
}
