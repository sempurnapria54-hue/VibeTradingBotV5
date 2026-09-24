package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_EXTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.deal;
import static com.example.tradingcore.unit.safety.SafetyFixture.deals;
import static com.example.tradingcore.unit.safety.SafetyFixture.pairContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.ServiceCommandExecutionResult;
import com.example.tradingcore.domain.deal.DealContextService;
import com.example.tradingcore.domain.safety.KillSwitchExecutor;
import com.example.tradingcore.domain.safety.KillSwitchService;
import com.example.tradingcore.persistence.service.DealDataService;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Консервативная агрегация каскадного снятия риска — группа `U8`
 * документа `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/components/KillSwitchService.md §«Два радиуса»).
 *
 * <p><b>Базовая сборка:</b> триггер снятия риска; служба сделок отдаёт
 * нетерминальные сделки счёта либо пары; сборщик контекста строит контекст
 * по каждой; исполнитель снятия риска подменён и отвечает по сделке и по
 * риску радиуса вне графа сделок — там по умолчанию «подтверждено».
 *
 * <p><b>Сам ход снятия риска предметом не является</b> — у него
 * ввод-вывод к площадке по построению, и его дом — чёрный ящик
 * (`.claude/tests/cases/trading-core.md` §«B5 — Ступени защиты:
 * автоматика, каскад, снятие риска»). Здесь снятие риска есть операнд:
 * подтверждено либо нет.
 */
class KillSwitchAggregationTest {

    private final DealDataService deals = mock(DealDataService.class);
    private final DealContextService contexts = mock(DealContextService.class);
    private final KillSwitchExecutor executor = mock(KillSwitchExecutor.class);

    private KillSwitchService killSwitchService;

    @BeforeEach
    void setUp() {
        killSwitchService = new KillSwitchService(deals, contexts, executor);
        when(contexts.build(any())).thenReturn(pairContext());
        when(executor.execute(any())).thenReturn(ServiceCommandExecutionResult.ok());
        when(executor.closePositionsOutsideDeals(anyLong(), any(), any())).thenReturn(true);
    }

    private void nonTerminal(List<Deal> population) {
        when(deals.findNonTerminalByExchangeAccountId(anyLong())).thenReturn(population);
    }

    /** Все сделки подтверждены — подтверждён и каскад; исполнитель позван по разу на сделку. */
    @Test
    @DisplayName("U8.1 — счётный радиус, все сделки подтверждены: исход подтверждён, по вызову на сделку")
    void u8_1_allConfirmedDealsConfirmTheCascade() {
        nonTerminal(deals(deal(51L), deal(52L)));

        assertThat(killSwitchService.fireExchangeAccount(ACCOUNT_ID)).isTrue();
        verify(executor, times(2)).execute(any());
    }

    /** Агрегация консервативна: одна неподтверждённая сделка делает неподтверждённым весь каскад. */
    @Test
    @DisplayName("U8.2 — одна сделка не подтверждена: исход не подтверждён, обход пройден до конца")
    void u8_2_oneUnconfirmedDealFailsTheWholeCascade() {
        nonTerminal(deals(deal(53L), deal(54L)));
        when(executor.execute(any())).thenReturn(
                ServiceCommandExecutionResult.notCompleted("residual size"),
                ServiceCommandExecutionResult.ok());

        assertThat(killSwitchService.fireExchangeAccount(ACCOUNT_ID)).isFalse();
        verify(executor, times(2)).execute(any());
    }

    /** Сбой одной сделки каскад не срывает — иначе первая же сорвала бы снятие по остальным. */
    @Test
    @DisplayName("U8.3 — исполнитель бросает на первой сделке: обход продолжен, исход не подтверждён, отказ в логе")
    void u8_3_aThrowingExecutorLeavesTheRestOfTheCascade() {
        nonTerminal(deals(deal(55L), deal(56L)));
        when(executor.execute(any()))
                .thenThrow(new IllegalStateException("exchange is down"))
                .thenReturn(ServiceCommandExecutionResult.ok());

        try (SafetyLogCapture log = SafetyLogCapture.attach(KillSwitchService.class)) {
            assertThat(killSwitchService.fireExchangeAccount(ACCOUNT_ID)).isFalse();

            assertThat(log.messages())
                    .anyMatch(message -> message.contains("Kill-switch failed on a deal"));
        }
        verify(executor, times(2)).execute(any());
    }

    /** Снимать нечего — подтверждено; исполнитель не позван ни разу. */
    @Test
    @DisplayName("U8.4 — нетерминальных сделок нет: исход подтверждён, исполнитель не позван")
    void u8_4_anEmptyAccountIsConfirmedFlat() {
        nonTerminal(deals());

        assertThat(killSwitchService.fireExchangeAccount(ACCOUNT_ID)).isTrue();
        verify(executor, never()).execute(any());
    }

    /** Порядок обхода на агрегацию не влияет. */
    @Test
    @DisplayName("U8.5 — подтверждена только последняя сделка: исход не подтверждён")
    void u8_5_theTraversalOrderDoesNotChangeTheAggregate() {
        nonTerminal(deals(deal(57L), deal(58L)));
        when(executor.execute(any())).thenReturn(
                ServiceCommandExecutionResult.ok(),
                ServiceCommandExecutionResult.notCompleted("residual size"));

        assertThat(killSwitchService.fireExchangeAccount(ACCOUNT_ID)).isFalse();
    }

    /** Инструментный радиус идёт по сделкам пары; триггерная — своим контекстом прохода. */
    @Test
    @DisplayName("U8.6 — инструментный радиус с триггерной сделкой: снятие по её контексту, сборщик и обход счёта не позваны")
    void u8_6_theInstrumentScopeTearsTheTriggerDealDownInItsOwnContext() {
        DealContext trigger = pairContext(deal(61L));
        when(deals.findNonTerminalOnPair(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(deals(deal(61L)));

        assertThat(killSwitchService.fireInstrument(trigger)).isTrue();

        verify(executor, times(1)).execute(trigger);
        verify(contexts, never()).build(any());
        verify(deals, never()).findNonTerminalByExchangeAccountId(anyLong());
    }

    /** Отказ сборки контекста одной сделки остальных не задевает. */
    @Test
    @DisplayName("U8.7 — сборщик контекста бросает на одной сделке: остальные пройдены, исход не подтверждён")
    void u8_7_aThrowingContextBuilderLeavesTheRestOfTheCascade() {
        nonTerminal(deals(deal(59L), deal(60L)));
        when(contexts.build(any()))
                .thenThrow(new IllegalStateException("graph load failed"))
                .thenReturn(pairContext());

        assertThat(killSwitchService.fireExchangeAccount(ACCOUNT_ID)).isFalse();
        verify(executor, times(1)).execute(any());
    }

    /** Ручной вызов триггерной сделки не несёт: сделка пары берётся популяцией. */
    @Test
    @DisplayName("U8.8 — инструментный радиус без сделки в контексте: сделка пары снята собранным контекстом")
    void u8_8_aManualPairCallTearsThePairDealDownByItsBuiltContext() {
        DealContext built = pairContext();
        when(deals.findNonTerminalOnPair(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(deals(deal(62L)));
        when(contexts.build(any())).thenReturn(built);

        assertThat(killSwitchService.fireInstrument(pairContext())).isTrue();

        verify(contexts, times(1)).build(argThat(deal -> Objects.equals(62L, deal.getId())));
        verify(executor, times(1)).execute(built);
    }

    /** Риск вне графа сделок входит в агрегат наравне со сделкой. */
    @Test
    @DisplayName("U8.9 — сделки подтверждены, риск вне графа сделок нет: исход не подтверждён")
    void u8_9_anUnconfirmedOutsideDealsRiskFailsTheCascade() {
        nonTerminal(deals(deal(63L)));
        when(executor.closePositionsOutsideDeals(anyLong(), any(), any())).thenReturn(false);

        assertThat(killSwitchService.fireExchangeAccount(ACCOUNT_ID)).isFalse();
        verify(executor, times(1)).execute(any());
    }

    /** Снятию вне графа отдаются радиус и его популяция: позиции сделок оно не трогает. */
    @Test
    @DisplayName("U8.10 — снятие вне графа получает радиус и популяцию: у счёта инструмента нет, у пары — инструмент пары")
    void u8_10_theOutsideDealsTeardownReceivesTheScopeAndItsPopulation() {
        List<Deal> accountPopulation = deals(deal(64L));
        List<Deal> pairPopulation = deals(deal(65L));
        nonTerminal(accountPopulation);
        when(deals.findNonTerminalOnPair(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(pairPopulation);

        killSwitchService.fireExchangeAccount(ACCOUNT_ID);
        killSwitchService.fireInstrument(pairContext());

        verify(executor).closePositionsOutsideDeals(eq(ACCOUNT_ID), isNull(), eq(accountPopulation));
        verify(executor).closePositionsOutsideDeals(ACCOUNT_ID, INSTRUMENT_EXTERNAL_ID, pairPopulation);
    }
}
