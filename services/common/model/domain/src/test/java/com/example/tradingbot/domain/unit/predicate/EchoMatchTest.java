package com.example.tradingbot.domain.unit.predicate;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.algo_order.Condition;
import com.example.tradingbot.domain.model.core.algo_order.Trailing;
import com.example.tradingbot.domain.model.core.algo_order.Trigger;
import com.example.tradingbot.domain.model.core.algo_order.TriggerPrice;
import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Совпадение эха площадки с нашей строкой — предикат обеих форм защиты:
 * отдельной условной заявки по трём осям (признак «только уменьшать»,
 * сторона, база каждой триггерной ноги) и встроенной защиты по одной — базе
 * (docs/models/mapping/AlgoOrder.md §«Сверка эха»,
 * docs/models/mapping/Order.md).
 *
 * <p><b>Ни один предикат не подменяется:</b> строка и прочитанная копия
 * собираются настоящими полями — объявленным намерением у строки и эхом у
 * копии, — и предикат считается по ним (.claude/rules/codestyle.md
 * §«Тесты доменных моделей»).
 *
 * <p>Клетки — группа `U24` документа
 * `.claude/tests/cases/domain-model-predicates.md`; метка клетки стоит в
 * имени метода и в {@code @DisplayName}.
 */
class EchoMatchTest {

    // --- отдельная условная заявка --------------------------------------

    @Test
    @DisplayName("U24.1 — эхо совпадает по всем трём осям: совпадение")
    void u24_1_anEchoAgreeingOnEveryAxisMatches() {
        AlgoOrder row = ocoRow();

        assertThat(row.matchesEcho(ocoEcho())).isTrue();
    }

    @Test
    @DisplayName("U24.2 — расходится только признак «только уменьшать»: расхождение")
    void u24_2_aDivergentReduceOnlyFlagAloneIsAMismatch() {
        AlgoOrder echo = ocoEcho();
        echo.setPositionReducingOnly(Boolean.FALSE);

        assertThat(ocoRow().matchesEcho(echo)).isFalse();
    }

    @Test
    @DisplayName("U24.3 — расходится только сторона: расхождение")
    void u24_3_aDivergentSideAloneIsAMismatch() {
        AlgoOrder echo = ocoEcho();
        echo.setDirection(AlgoOrder.Direction.BUY);

        assertThat(ocoRow().matchesEcho(echo)).isFalse();
    }

    @Test
    @DisplayName("U24.4 — расходится только база ноги стопа: расхождение")
    void u24_4_aDivergentStopLossBaseAloneIsAMismatch() {
        AlgoOrder echo = ocoEcho();
        echo.getCondition().getTrigger().getStopLoss().setExternalType(AlgoOrder.TriggerPriceType.LAST);

        assertThat(ocoRow().matchesEcho(echo)).isFalse();
    }

    @Test
    @DisplayName("U24.5 — расходится только база ноги тейка: расхождение — ось у каждой ноги своя")
    void u24_5_aDivergentTakeProfitBaseAloneIsAMismatch() {
        AlgoOrder echo = ocoEcho();
        echo.getCondition().getTrigger().getTakeProfit().setExternalType(AlgoOrder.TriggerPriceType.INDEX);

        assertThat(ocoRow().matchesEcho(echo)).isFalse();
    }

    @Test
    @DisplayName("U24.6 — эхо пусто по каждой оси: сверка не запускается")
    void u24_6_anEmptyEchoOnEveryAxisDoesNotTriggerTheCheck() {
        AlgoOrder echo = new AlgoOrder();
        echo.setCondition(new Condition(null, new Trigger(
                leg(null, null, "90"), leg(null, null, "120")), null));

        assertThat(ocoRow().matchesEcho(echo)).isTrue();
    }

    @Test
    @DisplayName("U24.7 — декларация пуста по каждой оси: сверка не запускается")
    void u24_7_anEmptyDeclarationOnEveryAxisDoesNotTriggerTheCheck() {
        AlgoOrder row = new AlgoOrder();
        row.setConditionType(AlgoOrder.ConditionType.OCO_FULL);
        row.setCondition(new Condition(AlgoOrder.ConditionType.OCO_FULL, new Trigger(
                leg(null, "90", null), leg(null, "120", null)), null));

        assertThat(row.matchesEcho(ocoEcho())).isTrue();
    }

    @Test
    @DisplayName("U24.8 — прочитанной копии нет: сверять нечего")
    void u24_8_anAbsentEchoMatches() {
        assertThat(ocoRow().matchesEcho(null)).isTrue();
    }

    @Test
    @DisplayName("U24.9 — трейлинг: оси базы нет, расходящаяся сторона по-прежнему расхождение")
    void u24_9_theTrailingHasNoBaseAxisButKeepsTheOthers() {
        AlgoOrder row = new AlgoOrder();
        row.setConditionType(AlgoOrder.ConditionType.TRAILING_PERCENTS);
        row.setDirection(AlgoOrder.Direction.SELL);
        row.setPositionReducingOnly(Boolean.TRUE);
        row.setCondition(new Condition(AlgoOrder.ConditionType.TRAILING_PERCENTS, null,
                new Trailing(new BigDecimal("1"), null, leg(null, "500", null), null)));
        AlgoOrder echo = new AlgoOrder();
        echo.setDirection(AlgoOrder.Direction.SELL);
        echo.setPositionReducingOnly(Boolean.TRUE);
        echo.setCondition(new Condition(null,
                new Trigger(leg(AlgoOrder.TriggerPriceType.LAST, null, "90"), null),
                new Trailing(null, null, leg(null, null, "500"), new BigDecimal("480"))));

        assertThat(row.matchesEcho(echo)).isTrue();

        echo.setDirection(AlgoOrder.Direction.BUY);

        assertThat(row.matchesEcho(echo)).isFalse();
    }

    // --- встроенная защита ----------------------------------------------

    @Test
    @DisplayName("U24.10 — встроенная защита: эхо базы совпадает с объявленной")
    void u24_10_anAttachedEchoAgreeingOnTheBaseMatches() {
        assertThat(attached(AlgoOrder.TriggerPriceType.MARK)
                .matchesEcho(attached(AlgoOrder.TriggerPriceType.MARK))).isTrue();
    }

    @Test
    @DisplayName("U24.11 — встроенная защита: эхо базы расходится с объявленной")
    void u24_11_anAttachedEchoDivergingOnTheBaseIsAMismatch() {
        assertThat(attached(AlgoOrder.TriggerPriceType.MARK)
                .matchesEcho(attached(AlgoOrder.TriggerPriceType.LAST))).isFalse();
    }

    @Test
    @DisplayName("U24.12 — встроенная защита: пустое эхо, пустая декларация либо нет копии — сверка не запускается")
    void u24_12_anAttachedEmptySideDoesNotTriggerTheCheck() {
        assertThat(attached(AlgoOrder.TriggerPriceType.MARK).matchesEcho(attached(null))).isTrue();
        assertThat(attached(null).matchesEcho(attached(AlgoOrder.TriggerPriceType.LAST))).isTrue();
        assertThat(attached(AlgoOrder.TriggerPriceType.MARK).matchesEcho(null)).isTrue();
    }

    // --- материал кейсов --------------------------------------------------

    /** Наша строка OCO: продажа, только уменьшать, обе ноги объявлены на MARK. */
    private static AlgoOrder ocoRow() {
        AlgoOrder row = new AlgoOrder();
        row.setConditionType(AlgoOrder.ConditionType.OCO_FULL);
        row.setDirection(AlgoOrder.Direction.SELL);
        row.setPositionReducingOnly(Boolean.TRUE);
        row.setCondition(new Condition(AlgoOrder.ConditionType.OCO_FULL, new Trigger(
                leg(AlgoOrder.TriggerPriceType.MARK, "90", null),
                leg(AlgoOrder.TriggerPriceType.MARK, "120", null)), null));
        return row;
    }

    /** Прочитанная копия той же заявки, во всём согласная со строкой. */
    private static AlgoOrder ocoEcho() {
        AlgoOrder echo = new AlgoOrder();
        echo.setDirection(AlgoOrder.Direction.SELL);
        echo.setPositionReducingOnly(Boolean.TRUE);
        echo.setCondition(new Condition(null, new Trigger(
                echoLeg(AlgoOrder.TriggerPriceType.MARK, "90"),
                echoLeg(AlgoOrder.TriggerPriceType.MARK, "120")), null));
        return echo;
    }

    /** Нога копии: объявленных полей у неё нет, только эхо. */
    private static TriggerPrice echoLeg(AlgoOrder.TriggerPriceType echoedBase, String echoedValue) {
        return leg(null, null, echoedValue, echoedBase);
    }

    private static TriggerPrice leg(AlgoOrder.TriggerPriceType declaredBase, String declaredValue,
                                    String echoedValue) {
        return leg(declaredBase, declaredValue, echoedValue, null);
    }

    private static TriggerPrice leg(AlgoOrder.TriggerPriceType declaredBase, String declaredValue,
                                    String echoedValue, AlgoOrder.TriggerPriceType echoedBase) {
        return new TriggerPrice(declaredBase, PredicateFixture.dec(declaredValue), echoedBase,
                PredicateFixture.dec(echoedValue));
    }

    private static AttachedAlgoOrder attached(AlgoOrder.TriggerPriceType base) {
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS);
        protection.setTriggerPriceType(base);
        return protection;
    }
}
