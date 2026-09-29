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
 * <p><b>Методы гейта называют ВЕТВИ композиции {@code transitionAllowed},
 * а не одноимённые величины спеки.</b> Ветвь штатного терминала несёт
 * терминальность траншей, доказанное отсутствие риска и величину
 * {@code cleanTerminalContract}; клетки {@code U3.2} и {@code U3.7}
 * предъявляют, что конъюнкты величины при отказе ветви держатся.
 *
 * <p><b>Ветвь аварийного терминала мерит только отсутствие риска</b>:
 * величину {@code emergencyTerminalContract} держат писатели числа, и спека
 * называет их поимённо. Клетка {@code U3.11} пинит это ограничение: число,
 * которого не писал ни один писатель, ветвь пропускает — провенанса у
 * числа в данных нет.
 */
class DealTerminalContractsTest {

    private final DealTransitionGate gate = new DealTransitionGate(new DealTerminalGate());

    @Test
    @DisplayName("U3.1 — базовая сборка: штатный контракт выполнен")
    void u3_1_theBaseStateSatisfiesTheCleanContract() {
        assertThat(gate.cleanTerminalAllowed(baseContext())).isTrue();
    }

    @Test
    @DisplayName("U3.2 — один транш не терминален: метод гейта отказывает, величина спеки держится")
    void u3_2_aLiveTrancheRefusesTheGateWhileTheSpecValueHolds() {
        DealContext context = contextOf(withResult(deal(Deal.Status.EXIT_PENDING,
                closedTrancheWithEntryFill(), tranche(SECOND_TRANCHE_ID, DealTranche.Status.MANAGING))));

        assertThat(gate.cleanTerminalAllowed(context)).isFalse();
        assertThat(context.getDeal().getResultProfit()).isNotNull();
        assertThat(context.getDeal().getResultProfitCurrency()).isNotBlank();
    }

    @Test
    @DisplayName("U3.3 — число результата пусто: число обязательно всегда")
    void u3_3_anAbsentResultRefusesTheCleanContract() {
        DealContext context = baseContext();
        context.getDeal().setResultProfit(null);

        assertThat(gate.cleanTerminalAllowed(context)).isFalse();
    }

    @Test
    @DisplayName("U3.4 — валюта пуста при состоявшемся входе: контракт не выполнен")
    void u3_4_anAbsentCurrencyRefusesOnADealThatEntered() {
        DealContext context = baseContext();
        context.getDeal().setResultProfitCurrency(null);

        assertThat(context.getDeal().positionObserved()).isTrue();
        assertThat(gate.cleanTerminalAllowed(context)).isFalse();
    }

    @Test
    @DisplayName("U3.5 — валюта пуста, входа НЕ было: смягчение адресует ровно эту популяцию")
    void u3_5_anAbsentCurrencyIsForgivenOnADealThatNeverEntered() {
        Deal dealWithoutEntry = withResult(deal(Deal.Status.EXIT_PENDING,
                tranche(TRANCHE_ID, DealTranche.Status.CLOSED)));
        dealWithoutEntry.setResultProfitCurrency(null);
        DealContext context = contextOf(dealWithoutEntry);

        assertThat(dealWithoutEntry.positionObserved()).isFalse();
        assertThat(gate.cleanTerminalAllowed(context)).isTrue();
    }

    @Test
    @DisplayName("U3.6 — валюта пустой строкой: пустая строка читается как отсутствие значения")
    void u3_6_aBlankCurrencyReadsAsAnAbsentValue() {
        DealContext context = baseContext();
        context.getDeal().setResultProfitCurrency("");

        assertThat(gate.cleanTerminalAllowed(context)).isFalse();
    }

    @Test
    @DisplayName("U3.7 — риск доказанно НЕ отсутствует: то же разведение имени")
    void u3_7_aLiveOrderRefusesTheGateWhileTheSpecValueHolds() {
        DealContext context = baseContext();
        context.getDeal().getTranches().getFirst().getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        assertThat(gate.cleanTerminalAllowed(context)).isFalse();
        assertThat(context.getDeal().getResultProfit()).isNotNull();
        assertThat(context.getDeal().getResultProfitCurrency()).isNotBlank();
    }

    @Test
    @DisplayName("U3.8 — базовая сборка, аварийный контракт: выполнен третьим дизъюнктом")
    void u3_8_theBaseStateSatisfiesTheEmergencyContract() {
        assertThat(gate.emergencyTerminalAllowed(baseContext())).isTrue();
    }

    @Test
    @DisplayName("U3.9 — один транш не терминален: аварийное ребро терминальности траншей не гейтит")
    void u3_9_theEmergencyContractIgnoresLiveTrancheRows() {
        DealContext context = contextOf(withResult(deal(Deal.Status.ERROR,
                closedTrancheWithEntryFill(), tranche(SECOND_TRANCHE_ID, DealTranche.Status.MANAGING))));

        assertThat(gate.emergencyTerminalAllowed(context)).isTrue();
    }

    @Test
    @DisplayName("U3.10 — риск доказанно не отсутствует: единственный конъюнкт, который мерит ветвь")
    void u3_10_theEmergencyContractRefusesOnLiveRisk() {
        DealContext context = baseContext();
        context.getDeal().getTranches().getFirst().getOrders().add(liveEntryLeg(30L, TRANCHE_ID));

        assertThat(gate.emergencyTerminalAllowed(context)).isFalse();
    }

    @Test
    @DisplayName("U3.11 — число стоит, итог на проходе неисчислим: ветвь провенанса не читает — его держат писатели")
    void u3_11_theEmergencyBranchLeavesTheResultProvenanceToItsWriters() {
        DealContext context = contextBuilder(withResult(deal(Deal.Status.ERROR, closedTrancheWithEntryFill())))
                .computationAllowed(Boolean.FALSE)
                .build();

        assertThat(gate.emergencyTerminalAllowed(context))
                .as("второй конъюнкт аварийного контракта держат писатели числа (docs/spec/deal-lifecycle.json)")
                .isTrue();
    }

    @Test
    @DisplayName("U3.12 — число пусто при отсутствии риска: пустое число законно и означает «неисчислимо»")
    void u3_12_anAbsentResultSatisfiesTheEmergencyContract() {
        DealContext context = baseContext();
        context.getDeal().setResultProfit(null);

        assertThat(gate.emergencyTerminalAllowed(context)).isTrue();
    }

    @Test
    @DisplayName("U3.13 — число доступно к расчёту на этом проходе: выполнен вторым дизъюнктом")
    void u3_13_aComputableResultSatisfiesTheEmergencyContract() {
        DealContext context = contextBuilder(withResult(deal(Deal.Status.ERROR, closedTrancheWithEntryFill())))
                .computationAllowed(Boolean.TRUE)
                .build();

        assertThat(gate.emergencyTerminalAllowed(context)).isTrue();
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
