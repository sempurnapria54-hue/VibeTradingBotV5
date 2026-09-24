package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.episodeWithMark;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.transferAction;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Перенос уровня остановки убытка за марк-ценой живой позиции — группа
 * {@code U30} документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/stop-distance.json, величина {@code transferStopBehindMark}).
 *
 * <p><b>Базовая сборка</b> — U1.1 с живым эпизодом единичного размера по
 * средней 3000; безубыток длинного при ставке базовой сборки — 3003.0015.
 * Уровень переноса лежит на прибыльной стороне, поэтому пол дистанции и
 * живое слагаемое потолков молчат, и перечень несёт только предмет группы.
 */
class TransferStopBehindMarkTest {

    private static final String BREAKEVEN_LONG = "3003.0015";

    private static final String BREAKEVEN_SHORT = "2997.0015";

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U30.1 — длинный: цена прошла уровень, перенос в безубыток допустим")
    void u30_1_aLongTransferBehindTheMarkPasses() {
        assertThat(codes(harness.validate(transferAction(BREAKEVEN_LONG),
                episodeContext(StrategyTradeDirection.LONG, "3050")))).isEmpty();
    }

    @Test
    @DisplayName("U30.2 — длинный: цена уровень ещё не прошла, перенос откладывается")
    void u30_2_aLongTransferAheadOfTheMarkIsDeferred() {
        assertThat(codes(harness.validate(transferAction(BREAKEVEN_LONG),
                episodeContext(StrategyTradeDirection.LONG, "3001"))))
                .containsExactly(RiskCheckCode.STOP_LOSS_BEYOND_MARK_PRICE);
    }

    @Test
    @DisplayName("U30.3 — уровень РАВЕН марк-цене: прохода цены нет")
    void u30_3_aTransferExactlyAtTheMarkIsDeferred() {
        assertThat(codes(harness.validate(transferAction(BREAKEVEN_LONG),
                episodeContext(StrategyTradeDirection.LONG, BREAKEVEN_LONG))))
                .containsExactly(RiskCheckCode.STOP_LOSS_BEYOND_MARK_PRICE);
    }

    @Test
    @DisplayName("U30.4 — короткий: цена прошла уровень вниз, перенос допустим")
    void u30_4_aShortTransferBehindTheMarkPasses() {
        assertThat(codes(harness.validate(transferAction(BREAKEVEN_SHORT),
                episodeContext(StrategyTradeDirection.SHORT, "2950")))).isEmpty();
    }

    @Test
    @DisplayName("U30.5 — короткий: цена уровень ещё не прошла, перенос откладывается")
    void u30_5_aShortTransferAheadOfTheMarkIsDeferred() {
        assertThat(codes(harness.validate(transferAction(BREAKEVEN_SHORT),
                episodeContext(StrategyTradeDirection.SHORT, "2999"))))
                .containsExactly(RiskCheckCode.STOP_LOSS_BEYOND_MARK_PRICE);
    }

    @Test
    @DisplayName("U30.6 — марк-цена живой позиции не наблюдена: перенос откладывается, а не пропускается")
    void u30_6_anUnobservedMarkDefersTheTransfer() {
        assertThat(codes(harness.validate(transferAction(BREAKEVEN_LONG),
                episodeContext(StrategyTradeDirection.LONG, null))))
                .containsExactly(RiskCheckCode.STOP_LOSS_BEYOND_MARK_PRICE);
    }

    @Test
    @DisplayName("U30.7 — первичная постановка по ту сторону рынка под охрану переноса не подпадает")
    void u30_7_aPrimaryPlacementIsOutsideTheCheckArea() {
        assertThat(codes(harness.validate(protectionAction("2970"),
                episodeContext(StrategyTradeDirection.LONG, "2960"))))
                .doesNotContain(RiskCheckCode.STOP_LOSS_BEYOND_MARK_PRICE);
    }

    @Test
    @DisplayName("U30.8 — живого эпизода нет: уровню срабатывать не на чем")
    void u30_8_aTransferWithoutALiveEpisodeIsOutsideTheCheckArea() {
        assertThat(codes(harness.validate(transferAction(BREAKEVEN_LONG), context(emptyDeal()))))
                .doesNotContain(RiskCheckCode.STOP_LOSS_BEYOND_MARK_PRICE);
    }

    @Test
    @DisplayName("U30.9 — ужесточение стопа на убыточной стороне, за которым цена уже прошла: не откладывается")
    void u30_9_aLossSideTighteningBeyondTheMarkIsNotDeferred() {
        assertThat(codes(harness.validate(transferAction("2950"),
                episodeContext(StrategyTradeDirection.LONG, "2940"))))
                .doesNotContain(RiskCheckCode.STOP_LOSS_BEYOND_MARK_PRICE);
    }

    /** Контекст со сделкой названного направления и живым эпизодом с названной марк-ценой. */
    private static DealContext episodeContext(StrategyTradeDirection direction, String markPrice) {
        Position episode = episodeWithMark("1", ANCHOR, markPrice);
        Deal deal = emptyDeal();
        deal.setDirection(direction);
        deal.setPositions(List.of(episode));
        return context(deal);
    }
}
