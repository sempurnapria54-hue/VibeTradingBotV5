package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.systemAction;
import static com.example.tradingcore.unit.risk.RiskFixture.trailingAction;
import static com.example.tradingcore.unit.risk.RiskFixture.transferAction;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Сторона первичного уровня остановки убытка — группа {@code U6}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/stop-distance.json, величина {@code primaryStopOnLossSide};
 * область — docs/rules/risk-policy.md §«Пол дистанции стопа»).
 *
 * <p><b>Базовая сборка</b> — U1.1: направление сделки длинное, якорь —
 * плановая цена действия 3000. Область ограничена двумя осями — ролью
 * постановки и источником уровня, — и каждая проверяется своей клеткой.
 */
class StopLossSideTest {

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
        assertThat(codes(harness.validate(entryAction("10", null, "3100"), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U6.9 — исходного объявления нет (акт системный): роль и источник не читаются")
    void u6_9_aSystemActWithoutASourceActionIsOutsideTheCheckArea() {
        assertThat(codes(harness.validate(systemAction("3100"), workingContext()))).isEmpty();
    }

    /** Контекст базовой сборки на короткой сделке. */
    private static DealContext shortContext() {
        Deal deal = emptyDeal();
        deal.setDirection(StrategyTradeDirection.SHORT);
        return context(deal);
    }
}
