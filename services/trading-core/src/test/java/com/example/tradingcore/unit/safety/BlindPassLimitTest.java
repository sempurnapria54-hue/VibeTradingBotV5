package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.account;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingcore.config.AnomalyJobProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.AnomalyPassGate;
import com.example.tradingcore.domain.safety.AnomalyReportService;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldService;
import com.example.tradingcore.domain.safety.HoldSignal;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Предел слепоты: счёт состоявшихся проходов — группа `U11` документа
 * `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/components/AnomalyJob.md §«Гейт полноты среза»).
 *
 * <p><b>Базовая сборка:</b> гейт полноты прохода; служба счетов
 * подменена и возвращает счёт слепоты после отметки; сервис журнала и
 * сервис блокировки подменены; предел слепоты — объявленное число
 * конфигурации.
 */
class BlindPassLimitTest {

    private final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);
    private final AnomalyReportService reportService = mock(AnomalyReportService.class);
    private final HoldService holdService = mock(HoldService.class);
    private final AnomalyJobProperties properties = new AnomalyJobProperties();

    private AnomalyPassGate gate;

    @BeforeEach
    void setUp() {
        gate = new AnomalyPassGate(accounts, reportService, holdService, properties);
        when(accounts.markPass(anyLong(), any())).thenReturn(0);
    }

    private void blindPasses(Integer count) {
        when(accounts.markPass(anyLong(), any())).thenReturn(count);
    }

    private HoldSignal journalledSignal() {
        ArgumentCaptor<HoldSignal> captor = ArgumentCaptor.forClass(HoldSignal.class);
        verify(reportService).journalState(any(), captor.capture(), any());
        return captor.getValue();
    }

    private HoldSignal raisedSignal() {
        ArgumentCaptor<HoldSignal> captor = ArgumentCaptor.forClass(HoldSignal.class);
        verify(holdService).raise(captor.capture(), any());
        return captor.getValue();
    }

    /** Наблюдённый проход отмечается и ничего больше не производит. */
    @Test
    @DisplayName("U11.1 — проход наблюдён: отметка исходом «наблюдён», ступень не запрошена, строка не пишется")
    void u11_1_anObservedPassOnlyMarksItself() {
        gate.apply(true, account());

        verify(accounts).markPass(ACCOUNT_ID, true);
        verify(holdService, never()).raise(any(), any());
        verify(reportService, never()).journalState(any(), any(), any());
    }

    /**
     * Подпредельный слепой проход пишет журнальную строку счётного
     * радиуса. <b>Дом этой строки — строка `A10` перечня детекторов;</b>
     * §«Гейт полноты среза» того же дока говорит обратное, и расхождение
     * припарковано (`.claude/work/backlog.md` §«Гейт полноты прохода
     * объявлен не зовущим фабрику, которой и пишет подпредельную
     * строку») — прогоном оно не ловится: кортежи фабрик неразличимы.
     */
    @Test
    @DisplayName("U11.2 — проход не наблюдён, счёт ниже предела: некритичная строка счётного радиуса, ступень не запрошена")
    void u11_2_aSubLimitBlindPassWritesAJournalRow() {
        blindPasses(properties.getBlindPassLimit() - 1);

        gate.apply(false, account());

        HoldSignal signal = journalledSignal();
        assertThat(signal.getScope()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT);
        assertThat(signal.getRung()).as("строка некритична").isEqualTo(HoldRung.SOFT);
        assertThat(signal.getCode()).isEqualTo(Constants.Hold.ANOMALY_PASS_INCOMPLETE);
        verify(holdService, never()).raise(any(), any());
    }

    /** Предел достигнут — мягкая счётная ступень тем же кодом. */
    @Test
    @DisplayName("U11.3 — счёт достиг предела: запрошена мягкая счётная ступень, журнальная строка отдельно не пишется")
    void u11_3_theLimitRaisesTheSoftAccountRung() {
        blindPasses(properties.getBlindPassLimit());

        gate.apply(false, account());

        assertThat(raisedSignal())
                .isEqualTo(HoldSignal.exchangeAccountSoft(Constants.Hold.ANOMALY_PASS_INCOMPLETE));
        verify(reportService, never()).journalState(any(), any(), any());
    }

    /** Выше предела — та же ступень: поглощение держит анкер стоящей. */
    @Test
    @DisplayName("U11.4 — счёт выше предела: та же мягкая ступень")
    void u11_4_aboveTheLimitRaisesTheSameRung() {
        blindPasses(properties.getBlindPassLimit() + 5);

        gate.apply(false, account());

        assertThat(raisedSignal())
                .isEqualTo(HoldSignal.exchangeAccountSoft(Constants.Hold.ANOMALY_PASS_INCOMPLETE));
    }

    /** Операнд отметки — исход наблюдения, а не полнота среза. */
    @Test
    @DisplayName("U11.5 — срез добыт целиком, но детекция не отработала: та же слепота")
    void u11_5_theOperandIsTheObservationOutcome() {
        gate.apply(false, account());

        verify(accounts).markPass(ACCOUNT_ID, false);
    }

    /** Ступень мягкая — снятия риска в составе нет. */
    @Test
    @DisplayName("U11.6 — достигнут предел: ступень мягкая, а не жёсткая")
    void u11_6_theLimitRungIsSoft() {
        blindPasses(properties.getBlindPassLimit());

        gate.apply(false, account());

        assertThat(raisedSignal().tearsDownRisk())
                .as("снятия риска в составе слепоты нет")
                .isFalse();
    }

    /** У слепоты инструмента нет: радиус счётный. */
    @Test
    @DisplayName("U11.7 — достигнут предел: контекст реакции несёт только счёт")
    void u11_7_theBlindPassContextCarriesOnlyTheAccount() {
        blindPasses(properties.getBlindPassLimit());

        gate.apply(false, account());

        ArgumentCaptor<DealContext> context = ArgumentCaptor.forClass(DealContext.class);
        verify(holdService).raise(any(), context.capture());
        assertThat(context.getValue().getInstrument()).isNull();
        assertThat(context.getValue().getExchangeAccount().getId()).isEqualTo(ACCOUNT_ID);
    }

    /** Ключ тот же, и обе критичности некритичны — второй строки не заводится. */
    @Test
    @DisplayName("U11.8 — достигнут предел при стоящей строке первого слепого прохода: второй строки не заводится")
    void u11_8_theLimitPathWritesNoSecondRow() {
        blindPasses(properties.getBlindPassLimit());

        gate.apply(false, account());

        verify(reportService, never()).journalState(any(), any(), any());
    }

    /**
     * Своего перехвата у гейта нет: отказ ловит перехват прохода по
     * счёту, и соседних счетов он не задевает
     * (§«Звенья кода, названные один раз»).
     */
    @Test
    @DisplayName("U11.9 — подпредельный проход, запись журнальной строки бросает: исключение уходит вызывающему")
    void u11_9_theGateHasNoCatchOfItsOwn() {
        blindPasses(1);
        when(reportService.journalState(any(), any(), any()))
                .thenThrow(new IllegalStateException("db is down"));

        assertThatThrownBy(() -> gate.apply(false, account()))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Отметка обнуляет счёт; автоматического снятия ступени нет ни у одной ступени. */
    @Test
    @DisplayName("U11.10 — наблюдённый проход после серии слепых: счёт обнулён отметкой, ступень не снимается")
    void u11_10_anObservedPassResetsTheCounterAndClearsNothing() {
        blindPasses(0);

        gate.apply(true, account());

        verify(accounts).markPass(ACCOUNT_ID, true);
        verify(accounts, never()).clearRung(anyLong(), any(), any());
        verify(holdService, never()).raise(any(), any());
    }
}
