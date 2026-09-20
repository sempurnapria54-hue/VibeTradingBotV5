package com.example.tradingcore.unit.fsm;

import static com.example.tradingcore.unit.fsm.FsmFixture.SECOND_TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.fsm.FsmFixture.contextBuilder;
import static com.example.tradingcore.unit.fsm.FsmFixture.deal;
import static com.example.tradingcore.unit.fsm.FsmFixture.fills;
import static com.example.tradingcore.unit.fsm.FsmFixture.liveEntryLeg;
import static com.example.tradingcore.unit.fsm.FsmFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.deal.DealTerminalGate;
import com.example.tradingcore.domain.fsm.DealTransitionGate;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Контракты двух терминалов сделки — группа {@code U3} документа
 * `.claude/tests/cases/trading-core-fsm.md` (дом —
 * docs/spec/deal-lifecycle.json, величины {@code cleanTerminalContract} и
 * {@code emergencyTerminalContract}; прозой — docs/lifecycles/Deal.md
 * §«Терминальный контракт финализации»).
 *
 * <p><b>Базовая сборка.</b> Состояние {@code U2.1} — риск доказанно
 * отсутствует; все транши терминальны; число результата {@code 10},
 * валюта {@code USDT}; вход состоялся — у транша есть налив входной ноги.
 *
 * <p><b>Имя {@code cleanTerminalContract} у кода и у спеки называет
 * РАЗНЫЕ конъюнкции</b> (находка {@code F-8}): величина спеки — только
 * число и валюта, а метод гейта несёт ещё терминальность траншей и
 * доказанное отсутствие риска, то есть ветвь композиции. Клетки
 * {@code U3.2} и {@code U3.7} предъявляют разведение: метод отвечает «не
 * выполнен», а конъюнкты величины спеки при этом держатся.
 *
 * <p><b>{@code @Tag("debt")}: {@code U3.11} предъявляет находку
 * {@code F-1}</b> — спека объявляет аварийный контракт двумя конъюнктами,
 * а гейт мерит один. Провенанс числа гейт не читает вовсе, поэтому
 * подставленное число он от посчитанного не отличает.
 */
class DealTerminalContractsTest {

    private final DealTransitionGate gate = new DealTransitionGate(new DealTerminalGate());

    @Test
    @DisplayName("U3.1 — базовая сборка: штатный контракт выполнен")
    void u3_1_theBaseStateSatisfiesTheCleanContract() {
        assertThat(gate.cleanTerminalContract(baseContext())).isTrue();
    }

    @Test
    @DisplayName("U3.2 — один транш не терминален: метод гейта отказывает, величина спеки держится")
    void u3_2_aLiveTrancheRefusesTheGateWhileTheSpecValueHolds() {
        DealContext context = contextOf(withResult(deal(Deal.Status.EXIT_PENDING,
                closedTrancheWithEntryFill(), tranche(SECOND_TRANCHE_ID, DealTranche.Status.MANAGING))));

        assertThat(gate.cleanTerminalContract(context)).isFalse();
        assertThat(context.getDeal().getResultProfit()).isNotNull();
        assertThat(context.getDeal().getResultProfitCurrency()).isNotBlank();
    }

    @Test
    @DisplayName("U3.3 — число результата пусто: число обязательно всегда")
    void u3_3_anAbsentResultRefusesTheCleanContract() {
        DealContext context = baseContext();
        context.getDeal().setResultProfit(null);

        assertThat(gate.cleanTerminalContract(context)).isFalse();
    }

    @Test
    @DisplayName("U3.4 — валюта пуста при состоявшемся входе: контракт не выполнен")
    void u3_4_anAbsentCurrencyRefusesOnADealThatEntered() {
        DealContext context = baseContext();
        context.getDeal().setResultProfitCurrency(null);

        assertThat(context.getDeal().positionObserved()).isTrue();
        assertThat(gate.cleanTerminalContract(context)).isFalse();
    }

    @Test
    @DisplayName("U3.5 — валюта пуста, входа НЕ было: смягчение адресует ровно эту популяцию")
    void u3_5_anAbsentCurrencyIsForgivenOnADealThatNeverEntered() {
        Deal dealWithoutEntry = withResult(deal(Deal.Status.EXIT_PENDING,
                tranche(TRANCHE_ID, DealTranche.Status.CLOSED)));
        dealWithoutEntry.setResultProfitCurrency(null);
        DealContext context = contextOf(dealWithoutEntry);

        assertThat(dealWithoutEntry.positionObserved()).isFalse();
        assertThat(gate.cleanTerminalContract(context)).isTrue();
    }

    @Test
    @DisplayName("U3.6 — валюта пустой строкой: пустая строка читается как отсутствие значения")
    void u3_6_aBlankCurrencyReadsAsAnAbsentValue() {
        DealContext context = baseContext();
        context.getDeal().setResultProfitCurrency("");

        assertThat(gate.cleanTerminalContract(context)).isFalse();
    }

    @Test
    @DisplayName("U3.7 — риск доказанно НЕ отсутствует: то же разведение имени")
    void u3_7_aLiveOrderRefusesTheGateWhileTheSpecValueHolds() {
        DealContext context = baseContext();
        context.getDeal().getTranches().getFirst().getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        assertThat(gate.cleanTerminalContract(context)).isFalse();
        assertThat(context.getDeal().getResultProfit()).isNotNull();
        assertThat(context.getDeal().getResultProfitCurrency()).isNotBlank();
    }

    @Test
    @DisplayName("U3.8 — базовая сборка, аварийный контракт: выполнен третьим дизъюнктом")
    void u3_8_theBaseStateSatisfiesTheEmergencyContract() {
        assertThat(gate.emergencyTerminalContract(baseContext())).isTrue();
    }

    @Test
    @DisplayName("U3.9 — один транш не терминален: аварийное ребро терминальности траншей не гейтит")
    void u3_9_theEmergencyContractIgnoresLiveTrancheRows() {
        DealContext context = contextOf(withResult(deal(Deal.Status.ERROR,
                closedTrancheWithEntryFill(), tranche(SECOND_TRANCHE_ID, DealTranche.Status.MANAGING))));

        assertThat(gate.emergencyTerminalContract(context)).isTrue();
    }

    @Test
    @DisplayName("U3.10 — риск доказанно не отсутствует: единственный конъюнкт, который контракт мерит")
    void u3_10_theEmergencyContractRefusesOnLiveRisk() {
        DealContext context = baseContext();
        context.getDeal().getTranches().getFirst().getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        assertThat(gate.emergencyTerminalContract(context)).isFalse();
    }

    @Test
    @Tag("debt")
    @DisplayName("U3.11 — число ПОДСТАВЛЕНО: спека требует посчитанного, гейт провенанса не читает")
    void u3_11_theEmergencyContractDoesNotTellASubstitutedResultFromAComputedOne() {
        DealContext context = contextBuilder(withResult(deal(Deal.Status.ERROR, closedTrancheWithEntryFill())))
                .computationAllowed(Boolean.FALSE)
                .build();

        assertThat(gate.emergencyTerminalContract(context)).isFalse();
    }

    @Test
    @DisplayName("U3.12 — число пусто при отсутствии риска: пустое число законно и означает «неисчислимо»")
    void u3_12_anAbsentResultSatisfiesTheEmergencyContract() {
        DealContext context = baseContext();
        context.getDeal().setResultProfit(null);

        assertThat(gate.emergencyTerminalContract(context)).isTrue();
    }

    @Test
    @DisplayName("U3.13 — число доступно к расчёту на этом проходе: выполнен вторым дизъюнктом")
    void u3_13_aComputableResultSatisfiesTheEmergencyContract() {
        DealContext context = contextBuilder(withResult(deal(Deal.Status.ERROR, closedTrancheWithEntryFill())))
                .computationAllowed(Boolean.TRUE)
                .build();

        assertThat(gate.emergencyTerminalContract(context)).isTrue();
    }

    /** Базовая сборка группы поверх сделки в координированном выходе. */
    private DealContext baseContext() {
        return contextOf(withResult(deal(Deal.Status.EXIT_PENDING, closedTrancheWithEntryFill())));
    }

    private DealContext contextOf(Deal deal) {
        return contextBuilder(deal).build();
    }

    /** Терминальный транш, чей вход состоялся и был закрыт своим выходом. */
    private DealTranche closedTrancheWithEntryFill() {
        return fills(tranche(TRANCHE_ID, DealTranche.Status.CLOSED), "5", "5");
    }

    /** Число и валюта результата на сделке. */
    private Deal withResult(Deal deal) {
        deal.setResultProfit(new BigDecimal("10"));
        deal.setResultProfitCurrency("USDT");
        return deal;
    }
}
