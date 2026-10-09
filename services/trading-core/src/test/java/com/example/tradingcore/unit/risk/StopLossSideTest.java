package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.price;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.systemAction;
import static com.example.tradingcore.unit.risk.RiskFixture.trailingAction;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.transferAction;
import static com.example.tradingcore.unit.risk.RiskFixture.withPrice;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Сторона первичного уровня остановки убытка — группа {@code U6}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/stop-distance.json, величина {@code primaryStopOnLossSide};
 * область — docs/rules/stop-level.md §«Пол дистанции стопа»).
 *
 * <p><b>Базовая сборка</b> — U1.1: направление сделки длинное, якорь —
 * плановая цена действия 3000. Область ограничена двумя осями — ролью
 * постановки и источником уровня, — и каждая проверяется своей клеткой.
 *
 * <p><b>Вторая мера стороны — от плановой цены своей ноги</b>
 * (docs/spec/stop-distance.json, величина {@code stopOnOwnLegLossSide}):
 * её клетки собраны на живом эпизоде, где якорь — средняя цена 3000, а нога
 * стоит своей ценой. Меток они не несут — их назначает документ кейсов.
 */
class StopLossSideTest {

    /** Средняя цена живого эпизода — якорь второй меры. */
    private static final BigDecimal EPISODE_AVERAGE = new BigDecimal("3000");

    /** Плановая цена лимитной ноги сетки ниже средней. */
    private static final BigDecimal GRID_LEG_PRICE = new BigDecimal("2900");

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U6.1 — первичная постановка, цена срабатывания ниже якоря: отказа нет")
    void u6_1_aPrimaryStopBelowTheAnchorPasses() {
        assertThat(codes(harness.validate(entryAction("10", ANCHOR, STOP.toPlainString()), workingContext())))
                .isEmpty();
    }

    @Test
    @DisplayName("U6.2 — цена срабатывания выше якоря: уровень на неверной стороне")
    void u6_2_aPrimaryStopAboveTheAnchorIsRejected() {
        assertThat(codes(harness.validate(entryAction("10", ANCHOR, "3100"), workingContext())))
                .containsExactly(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
    }

    @Test
    @DisplayName("U6.3 — цена срабатывания РАВНА якорю: граница не на убыточной стороне")
    void u6_3_aPrimaryStopExactlyAtTheAnchorIsRejected() {
        assertThat(codes(harness.validate(entryAction("10", ANCHOR, "3000"), workingContext())))
                .containsExactly(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
    }

    @Test
    @DisplayName("U6.4 — короткое направление, цена срабатывания выше якоря: отказа нет")
    void u6_4_aShortStopAboveTheAnchorPasses() {
        assertThat(codes(harness.validate(entryAction("10", ANCHOR, "3050"), shortContext()))).isEmpty();
    }

    @Test
    @DisplayName("U6.5 — короткое направление, цена срабатывания ниже якоря: отказ")
    void u6_5_aShortStopBelowTheAnchorIsRejected() {
        assertThat(codes(harness.validate(entryAction("10", ANCHOR, "2950"), shortContext())))
                .containsExactly(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
    }

    @Test
    @DisplayName("U6.6 — роль постановки перенос, уровень выше якоря: перевод в безубыток законен")
    void u6_6_aTransferAboveTheAnchorIsOutsideTheCheckArea() {
        assertThat(codes(harness.validate(transferAction("3100"), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U6.7 — роль первичная, источник наблюдаемый: уровня в момент постановки нет")
    void u6_7_anObservedLevelSourceIsOutsideTheCheckArea() {
        assertThat(codes(harness.validate(trailingAction("3100"), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U6.8 — якорь пуст: сверять не с чем")
    void u6_8_anEmptyAnchorSilencesTheSideCheck() {
        assertThat(codes(harness.validate(entryAction("10", null, "3100"), workingContext())))
                .as("сторону сверять не с чем; отказ пришёл неизмеренными слагаемыми акта")
                .containsExactly(RiskCheckCode.CALCULATED_ACTION_INVALID);
    }

    @Test
    @DisplayName("U6.9 — исходного объявления нет (акт системный): роль и источник не читаются")
    void u6_9_aSystemActWithoutASourceActionIsOutsideTheCheckArea() {
        assertThat(codes(harness.validate(systemAction("3100"), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U6.10 — живой эпизод: стоп входа сетки в полосе над ценой своей ноги — отказ стороной")
    void aGridEntryStopInTheBandAboveItsOwnLegIsRejected() {
        RiskValidationResult result = harness.validate(entryAction("5", GRID_LEG_PRICE, "2901"),
                liveEpisodeContext(StrategyTradeDirection.LONG));

        assertThat(codes(result))
                .as("от якоря 3000 уровень на убыточной стороне — ловит только мера от своей ноги")
                .contains(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
        assertThat(sideComments(result)).anyMatch(comment -> comment.contains("own entry leg"));
    }

    @Test
    @DisplayName("U6.11 — живой эпизод: тот же вход сетки со стопом за ценой своей ноги проходит сторону")
    void aGridEntryStopBehindItsOwnLegPasses() {
        assertThat(codes(harness.validate(entryAction("5", GRID_LEG_PRICE, "2850"),
                liveEpisodeContext(StrategyTradeDirection.LONG))))
                .doesNotContain(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
    }

    @Test
    @DisplayName("U6.12 — живой эпизод, короткая сделка: стоп входа сетки в полосе под ценой своей ноги — отказ")
    void aShortGridEntryStopInTheBandBelowItsOwnLegIsRejected() {
        RiskValidationResult result = harness.validate(entryAction("5", new BigDecimal("3100"), "3099"),
                liveEpisodeContext(StrategyTradeDirection.SHORT));

        assertThat(codes(result)).contains(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
        assertThat(sideComments(result)).anyMatch(comment -> comment.contains("own entry leg"));
    }

    @Test
    @DisplayName("U6.13 — живой эпизод: защитное создание своей ноги не имеет — вторая мера не читается")
    void aProtectiveCreationDoesNotReadTheOwnLegMeasure() {
        assertThat(codes(harness.validate(withPrice(protectionAction("2940"), price(GRID_LEG_PRICE, "2940")),
                liveEpisodeContext(StrategyTradeDirection.LONG))))
                .as("от якоря 3000 уровень 2940 на убыточной стороне; цена действия 2900 — не цена ноги")
                .doesNotContain(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
    }

    /**
     * Сделка с живым эпизодом по средней цене 3000 и стоящей защитой транша:
     * без действующего уровня на всю позицию оценка ликвидации входа не
     * измерена, и её отказ подмешался бы к предмету группы.
     */
    private static DealContext liveEpisodeContext(StrategyTradeDirection direction) {
        Deal deal = emptyDeal();
        deal.setDirection(direction);
        deal.setPositions(List.of(episode("10", EPISODE_AVERAGE)));
        deal.setTranches(List.of(tranche(List.of(), List.of(protection(STOP.toPlainString())))));
        return context(deal);
    }

    /** Пояснения отказов стороной первичного уровня. */
    private static List<String> sideComments(RiskValidationResult result) {
        return result.getChecks().stream()
                .filter(check -> RiskCheckCode.STOP_LOSS_INVALID_SIDE.equals(check.getCode()))
                .map(RiskCheckResult::getComment)
                .toList();
    }

    /** Контекст базовой сборки на короткой сделке. */
    private static DealContext shortContext() {
        Deal deal = emptyDeal();
        deal.setDirection(StrategyTradeDirection.SHORT);
        return context(deal);
    }
}
