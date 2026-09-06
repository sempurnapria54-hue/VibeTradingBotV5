package com.example.tradingcore;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.risk.RiskBlockResolver;
import com.example.tradingcore.domain.command.risk.RiskCheckResult;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import com.example.tradingcore.domain.command.risk.RiskValidationResult.RiskDecision;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Карта «вердикт → действие»: что решает стадия, что решает код и как
 * сворачивается многокодовый вердикт
 * (docs/components/RiskBlockResolver.md).
 *
 * <p><b>Живой риск не подставляется признаком</b> — он вытекает из
 * настоящего состояния: эпизода позиции, стадии транша, живых ног и
 * условных заявок в его графе (.claude/rules/codestyle.md §«Тесты доменных
 * моделей»).
 */
class RiskReactionMapTest {

    private final RiskBlockResolver resolver = new RiskBlockResolver();

    // --- разрешающие реакции ------------------------------------------------

    /** Разрешённый вердикт продолжает действие и причины не несёт. */
    @Test
    void allowedVerdictContinues() {
        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK,
                verdict(RiskDecision.ALLOWED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.CONTINUE);
        assertThat(action.getRiskCode()).isNull();
    }

    /** Предупреждение действие не блокирует. */
    @Test
    void warningVerdictContinuesWithWarning() {
        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK,
                verdict(RiskDecision.WARNING));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.CONTINUE_WITH_WARNING);
    }

    // --- неполнота графа стадию не делит ------------------------------------

    /** До живого риска отказ по неполноте графа ведёт в ошибку сделки, а не в терминал транша. */
    @Test
    void incompleteGraphGoesToErrorBeforeLiveRisk() {
        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK,
                blocked(RiskCheckCode.DEAL_GRAPH_INCOMPLETE));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
        assertThat(action.getRiskCode()).isEqualTo(RiskCheckCode.DEAL_GRAPH_INCOMPLETE);
        assertThat(action.getCloseReason()).isNull();
    }

    /**
     * И при живом риске — та же реакция: причина закрытия {@code RISK_CONTROL}
     * подписала бы дефект предъявления контекста риск-контролем и лишила бы
     * разбор по данным этого различия.
     */
    @Test
    void incompleteGraphGoesToErrorWithLiveRiskToo() {
        RiskBlockAction action = resolver.resolve(context(dealWithLiveEpisode()),
                DealTranche.Status.MANAGING, blocked(RiskCheckCode.DEAL_GRAPH_INCOMPLETE));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    // --- стадия до живого риска ---------------------------------------------

    /** Бессрочный вердикт до живого риска закрывает кандидатный транш причиной риск-контроля. */
    @Test
    void permanentVerdictBeforeLiveRiskClosesTheCandidate() {
        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK,
                blocked(RiskCheckCode.STOP_LOSS_INVALID_SIDE));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL);
        assertThat(action.getCloseReason()).isEqualTo(Deal.CloseReason.RISK_CONTROL);
        assertThat(action.getRiskCode()).isEqualTo(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
    }

    /**
     * Временный вердикт до живого риска действие ПРОПУСКАЕТ: занятый бюджет
     * освободится выходом соседнего транша, а прочтение «терминал» потеряло
     * бы уровень сетки навсегда.
     */
    @Test
    void temporaryVerdictBeforeLiveRiskSkipsTheAction() {
        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK,
                blocked(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
        assertThat(action.getCloseReason()).isNull();
    }

    /**
     * Бессрочность вердикта — КОНЪЮНКЦИЯ: один временный код делает
     * временным весь вердикт, потому что повтор может пройти.
     */
    @Test
    void oneTemporaryCodeMakesTheWholeVerdictTemporary() {
        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK,
                blocked(RiskCheckCode.STOP_LOSS_INVALID_SIDE, RiskCheckCode.FEE_RATE_UNAVAILABLE));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
    }

    /**
     * Вердикт {@code BLOCKED} без единого блокирующего кода — рассогласование
     * самого валидатора: терминал по нему был бы решением по недобытому
     * факту.
     */
    @Test
    void blockedVerdictWithoutCodesNeverCloses() {
        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK,
                verdict(RiskDecision.BLOCKED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
        assertThat(action.getRiskCode()).isNull();
    }

    /** Недобытый операнд до живого риска откладывает вердикт, а не выносит его. */
    @Test
    void staleBalanceBeforeLiveRiskRequestsRefresh() {
        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK,
                blocked(RiskCheckCode.BALANCE_NOT_FRESH));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.REQUEST_REFRESH);
        assertThat(action.getRiskCode()).isNull();
    }

    /**
     * При ЖИВОМ риске тот же код ведёт в ошибку: несвежий снимок там —
     * рассогласование учёта, а не отложенный вердикт
     * (docs/processes/risk-evaluation.md §«Карв-аут исчерпанного бюджета
     * сделки»).
     */
    @Test
    void staleBalanceWithLiveRiskGoesToError() {
        RiskBlockAction action = resolver.resolve(context(dealWithLiveEpisode()),
                DealTranche.Status.MANAGING, blocked(RiskCheckCode.BALANCE_NOT_FRESH));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    // --- карв-аут при живом риске -------------------------------------------

    /** Вердикт целиком из карв-аута действие не исполняет: сделка доживает под своей защитой. */
    @Test
    void carvedOutVerdictWithLiveRiskSkipsTheAction() {
        RiskBlockAction action = resolver.resolve(context(dealWithLiveEpisode()),
                DealTranche.Status.MANAGING, blocked(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED,
                        RiskCheckCode.INSTRUMENT_SAFETY_HOLD));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
        assertThat(action.getRiskCode()).isEqualTo(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED);
    }

    /**
     * Членство в карв-ауте — КОНЪЮНКЦИЯ: один код вне перечня возвращает
     * вердикт на аварийную тропу. «Мягкая» свёртка маскировала бы настоящее
     * рассогласование соседним ожидаемым отказом.
     */
    @Test
    void oneCodeOutsideTheCarveOutReturnsTheVerdictToError() {
        RiskBlockAction action = resolver.resolve(context(dealWithLiveEpisode()),
                DealTranche.Status.MANAGING, blocked(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED,
                        RiskCheckCode.CALCULATED_ACTION_INVALID));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    /** Код вне карв-аута при живом риске уводит сделку в ошибку. */
    @Test
    void nonCarvedCodeWithLiveRiskGoesToError() {
        RiskBlockAction action = resolver.resolve(context(dealWithLiveEpisode()),
                DealTranche.Status.MANAGING, blocked(RiskCheckCode.RISK_PER_ACTION_EXCEEDED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
        assertThat(action.getRiskCode()).isEqualTo(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
    }

    /**
     * Неделимый лот и расхождение расчёта разведены ЗНАЧЕНИЕМ, и карв-аут на
     * них расходится: без разведения сделка без живого риска уходила бы в
     * аварийный контур по ожидаемому отказу.
     */
    @Test
    void indivisibleLotIsCarvedOutWhileCalculationMismatchIsNot() {
        RiskBlockAction carved = resolver.resolve(context(dealWithLiveEpisode()),
                DealTranche.Status.MANAGING, blocked(RiskCheckCode.SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET));
        RiskBlockAction notCarved = resolver.resolve(context(dealWithLiveEpisode()),
                DealTranche.Status.MANAGING, blocked(RiskCheckCode.RISK_PER_ACTION_EXCEEDED));

        assertThat(carved.getType()).isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
        assertThat(notCarved.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    // --- откуда берётся живой риск ------------------------------------------

    /** Живой риск виден по стадии транша даже без эпизода позиции. */
    @Test
    void stageAloneMarksLiveRisk() {
        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.ENTRY_SUBMITTED,
                blocked(RiskCheckCode.RISK_PER_ACTION_EXCEEDED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    /**
     * Живая нога транша тоже означает живой риск, и собирается она ОБХОДОМ
     * ТРАНШЕЙ: поля {@code Deal.orders} в целевой модели нет.
     */
    @Test
    void liveLegOfATrancheMarksLiveRisk() {
        Deal deal = emptyDeal();
        DealTranche tranche = tranche();
        Order leg = new Order();
        leg.setId(101L);
        leg.setStatus(Order.Status.ACTIVE);
        tranche.setOrders(List.of(leg));
        deal.setTranches(List.of(tranche));

        RiskBlockAction action = resolver.resolve(context(deal), DealTranche.Status.PRECHECK,
                blocked(RiskCheckCode.RISK_PER_ACTION_EXCEEDED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    /** Живая условная заявка транша — тот же признак. */
    @Test
    void liveAlgoOrderOfATrancheMarksLiveRisk() {
        Deal deal = emptyDeal();
        DealTranche tranche = tranche();
        AlgoOrder algoOrder = new AlgoOrder();
        algoOrder.setId(55L);
        algoOrder.setStatus(AlgoOrder.Status.ACTIVE);
        tranche.setAlgoOrders(List.of(algoOrder));
        deal.setTranches(List.of(tranche));

        RiskBlockAction action = resolver.resolve(context(deal), DealTranche.Status.PRECHECK,
                blocked(RiskCheckCode.RISK_PER_ACTION_EXCEEDED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    // --- свёртка причины ----------------------------------------------------

    /**
     * Причина — СТАРШИЙ код перечня. Все коды риска несут одну причину
     * закрытия, поэтому решает второе правило старшинства: первый по порядку
     * проверок валидатора, то есть первый в накопленном перечне.
     */
    @Test
    void reasonIsTheFirstCodeInTheAccumulationOrder() {
        RiskBlockAction action = resolver.resolve(context(dealWithLiveEpisode()),
                DealTranche.Status.MANAGING, blocked(RiskCheckCode.INSTRUMENT_NOT_LIVE,
                        RiskCheckCode.RISK_PER_ACTION_EXCEEDED));

        assertThat(action.getRiskCode()).isEqualTo(RiskCheckCode.INSTRUMENT_NOT_LIVE);
    }

    // --- фикстуры -----------------------------------------------------------

    private static RiskValidationResult verdict(RiskDecision decision) {
        return RiskValidationResult.builder()
                .decision(decision)
                .checks(new ArrayList<>())
                .comment("verdict " + decision)
                .build();
    }

    private static RiskValidationResult blocked(RiskCheckCode... codes) {
        List<RiskCheckResult> checks = Arrays.stream(codes)
                .map(code -> RiskCheckResult.blocked(code, code.name(), null))
                .toList();
        return RiskValidationResult.builder()
                .decision(RiskDecision.BLOCKED)
                .checks(checks)
                .comment("blocked")
                .build();
    }

    private static Deal emptyDeal() {
        Deal deal = new Deal();
        deal.setId(1L);
        deal.setStatus(Deal.Status.ACTIVE);
        deal.setDirection(StrategyTradeDirection.LONG);
        deal.setTranches(new ArrayList<>());
        deal.setPositions(new ArrayList<>());
        return deal;
    }

    private static Deal dealWithLiveEpisode() {
        Deal deal = emptyDeal();
        Position position = new Position();
        position.setId(9L);
        position.setStatus(Position.Status.ACTIVE);
        position.setExternalSize(new BigDecimal("10"));
        deal.setPositions(List.of(position));
        return deal;
    }

    private static DealTranche tranche() {
        DealTranche tranche = new DealTranche();
        tranche.setId(10L);
        tranche.setStatus(DealTranche.Status.PRECHECK);
        return tranche;
    }

    private static DealContext context(Deal deal) {
        ExchangeAccount account = new ExchangeAccount();
        account.setId(3L);
        account.setInternalId("ea-0001");
        Instrument instrument = new Instrument();
        instrument.setId(7L);
        return DealContext.builder()
                .deal(deal)
                .exchangeAccount(account)
                .instrument(instrument)
                .graphComplete(true)
                .build();
    }
}
