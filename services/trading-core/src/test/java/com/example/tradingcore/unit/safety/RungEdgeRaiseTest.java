package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_INTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.ACTOR;
import static com.example.tradingcore.unit.safety.SafetyFixture.CODE;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.INSTRUMENT_INTERNAL_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.TENANT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.account;
import static com.example.tradingcore.unit.safety.SafetyFixture.accountContext;
import static com.example.tradingcore.unit.safety.SafetyFixture.instrument;
import static com.example.tradingcore.unit.safety.SafetyFixture.pairContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.security.ActorProvider;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.HoldRungEdgeService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.integration.internal.event.CoreEventWriter;
import com.example.tradingcore.persistence.service.AccountInstrumentStateDataService;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Ступень объекта выводится из пары «радиус × судьба принятого риска» —
 * группа `U1` документа `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/components/HoldService.md §«Что делает вызов»; лестницы —
 * docs/rules/instrument-hold.md §Правило, docs/rules/exchange-hold.md
 * §Правило).
 *
 * <p><b>Базовая сборка:</b> ребро подъёма; контекст объекта несёт
 * биржевой счёт и инструмент; обе службы персистентности отвечают
 * «переход применился»; поставщик актора отдаёт названного принципала;
 * писатель фактов принимает вызов.
 */
class RungEdgeRaiseTest {

    private final AccountInstrumentStateDataService pairStates =
            mock(AccountInstrumentStateDataService.class);
    private final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);
    private final ActorProvider actorProvider = mock(ActorProvider.class);
    private final CoreEventWriter coreEventWriter = mock(CoreEventWriter.class);

    private HoldRungEdgeService edge;

    @BeforeEach
    void setUp() {
        edge = new HoldRungEdgeService(pairStates, accounts, actorProvider, coreEventWriter);
        when(pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(true);
        when(accounts.raiseRung(anyLong(), any())).thenReturn(true);
        when(actorProvider.currentActor()).thenReturn(ACTOR);
    }

    /** Жёсткая пара инструментного радиуса даёт сворачивание лестницы пары. */
    @Test
    @DisplayName("U1.1 — сигнал (INSTRUMENT, HARD): служба пары получает сворачивание, служба счёта не позвана")
    void u1_1_hardInstrumentSignalRaisesThePairLadder() {
        Boolean applied = edge.raise(HoldSignal.instrument(CODE), pairContext());

        verify(pairStates).raiseRung(ACCOUNT_ID, INSTRUMENT_ID, Instrument.SafetyRung.TRADE_BLOCKED);
        verify(accounts, never()).raiseRung(anyLong(), any());
        verify(coreEventWriter).holdRaised(eq(TENANT_ID), any(), eq(ACCOUNT_INTERNAL_ID),
                eq(INSTRUMENT_INTERNAL_ID), eq(ACTOR));
        assertThat(applied).as("переход применился").isTrue();
    }

    /** Мягкая пара того же радиуса даёт запрет входов, а не сворачивание. */
    @Test
    @DisplayName("U1.2 — сигнал (INSTRUMENT, SOFT): служба пары получает запрет входов")
    void u1_2_softInstrumentSignalBlocksEntriesOnly() {
        edge.raise(HoldSignal.instrumentSoft(CODE), pairContext());

        verify(pairStates).raiseRung(ACCOUNT_ID, INSTRUMENT_ID, Instrument.SafetyRung.ENTRY_BLOCKED);
    }

    /** Жёсткая пара счётного радиуса адресует лестницу счёта. */
    @Test
    @DisplayName("U1.3 — сигнал (EXCHANGE_ACCOUNT, HARD): служба счёта получает сворачивание, служба пары не позвана")
    void u1_3_hardAccountSignalRaisesTheAccountLadder() {
        edge.raise(HoldSignal.exchangeAccount(CODE), pairContext());

        verify(accounts).raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.TRADE_BLOCKED);
        verify(pairStates, never()).raiseRung(anyLong(), anyLong(), any());
    }

    /** Мягкая пара того же радиуса даёт мягкий холд счёта. */
    @Test
    @DisplayName("U1.4 — сигнал (EXCHANGE_ACCOUNT, SOFT): служба счёта получает мягкий холд")
    void u1_4_softAccountSignalRaisesTheSoftAccountRung() {
        edge.raise(HoldSignal.exchangeAccountSoft(CODE), pairContext());

        verify(accounts).raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.HOLD);
    }

    /**
     * Инструмент содержимого читается по радиусу: счётный сигнал
     * инструмента не несёт, даже когда контекст его знает — иначе радиус
     * факта объявил бы пару там, где ступень стои́т на всём счёте.
     */
    @Test
    @DisplayName("U1.5 — счётный сигнал в контексте с инструментом: в содержимом факта инструмента нет")
    void u1_5_theAccountScopeFactCarriesNoInstrument() {
        edge.raise(HoldSignal.exchangeAccount(CODE), pairContext());

        verify(coreEventWriter).holdRaised(eq(TENANT_ID), any(), eq(ACCOUNT_INTERNAL_ID),
                eq(null), eq(ACTOR));
    }

    /** Инструментный сигнал несёт межсервисную идентичность инструмента, а не числовую. */
    @Test
    @DisplayName("U1.6 — инструментный сигнал: в содержимом факта межсервисная идентичность инструмента")
    void u1_6_theInstrumentScopeFactCarriesTheCrossServiceIdentity() {
        edge.raise(HoldSignal.instrument(CODE), pairContext());

        verify(coreEventWriter).holdRaised(eq(TENANT_ID), any(), eq(ACCOUNT_INTERNAL_ID),
                eq(INSTRUMENT_INTERNAL_ID), eq(ACTOR));
    }

    /** Поглощённый сигнал события не производит: писатель стои́т на переходе. */
    @Test
    @DisplayName("U1.7 — ступень уже стои́т: возврат «не переставилась», писатель фактов не позван")
    void u1_7_anAbsorbedSignalProducesNoFact() {
        when(pairStates.raiseRung(anyLong(), anyLong(), any())).thenReturn(false);

        Boolean applied = edge.raise(HoldSignal.instrument(CODE), pairContext());

        assertThat(applied).as("перехода не было").isFalse();
        verifyNoInteractions(coreEventWriter);
    }

    /**
     * Отказ записи факта уходит вызывающему. <b>Откат самой ступени
     * держит объявленная транзакция ребра</b> — на уровне 2 она не
     * поднимается, и наблюдаемая здесь половина ожидания — что
     * исключение не подавлено: применённого на шаге нет.
     */
    @Test
    @DisplayName("U1.8 — писатель фактов бросает: исключение уходит вызывающему")
    void u1_8_aFailingFactWriterFailsTheEdge() {
        doThrow(new IllegalStateException("outbox is down"))
                .when(coreEventWriter).holdRaised(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> edge.raise(HoldSignal.instrument(CODE), pairContext()))
                .as("U1.8: отказ писателя факта подавлению не подлежит")
                .isInstanceOf(IllegalStateException.class);
    }

    /** Актор — принципал контекста хода, а не аргумент вызова. */
    @Test
    @DisplayName("U1.9 — актор факта взят у поставщика контекста хода")
    void u1_9_theActorComesFromTheContextProvider() {
        when(actorProvider.currentActor()).thenReturn("another@example");

        edge.raise(HoldSignal.instrument(CODE), pairContext());

        verify(coreEventWriter).holdRaised(any(), any(), any(), any(), eq("another@example"));
    }

    /** Тенант конверта берётся со счёта контекста. */
    @Test
    @DisplayName("U1.10 — тенант содержимого взят со счёта контекста")
    void u1_10_theTenantComesFromTheContextAccount() {
        edge.raise(HoldSignal.instrument(CODE), pairContext());

        verify(coreEventWriter).holdRaised(eq(TENANT_ID), any(), any(), any(), any());
    }

    /** У счётной тропы инструмент не разыменовывается вовсе. */
    @Test
    @DisplayName("U1.11 — счётный сигнал без инструмента в контексте: тот же исход, что у U1.3")
    void u1_11_theAccountPathNeverDereferencesTheInstrument() {
        edge.raise(HoldSignal.exchangeAccount(CODE), accountContext());

        verify(accounts).raiseRung(ACCOUNT_ID, ExchangeAccount.SafetyRung.TRADE_BLOCKED);
        verify(pairStates, never()).raiseRung(anyLong(), anyLong(), any());
        verify(coreEventWriter).holdRaised(eq(TENANT_ID), any(), eq(ACCOUNT_INTERNAL_ID),
                eq(null), eq(ACTOR));
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b>
     * Доминирование биржевых ступеней над инструментными реакциями
     * объявлено лестницей (docs/rules/exchange-hold.md §«Границы и
     * эскалация»), а ребро подъёма на инструментном радиусе читает только
     * строку пары — счётная ступень его операндом не является нигде.
     * Красный прогон и есть предъявление находки `S-2`
     * (`.claude/work/backlog.md` §«Доминирование биржевых ступеней над
     * инструментными реакциями энфорсера не имеет»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U1.12 — счёт свёрнут, приходит инструментный сигнал: ступень пары не переставляется, факта нет")
    void u1_12_theAccountRungDominatesTheInstrumentReaction() {
        DealContext context = DealContext.builder()
                .exchangeAccount(account(ExchangeAccount.SafetyRung.TRADE_BLOCKED))
                .instrument(instrument())
                .build();

        edge.raise(HoldSignal.instrument(CODE), context);

        verify(pairStates, never()).raiseRung(anyLong(), anyLong(), any());
        verifyNoInteractions(coreEventWriter);
    }
}
