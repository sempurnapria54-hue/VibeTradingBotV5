package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.CODE;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.safety.HoldRung;
import com.example.tradingcore.domain.safety.HoldScope;
import com.example.tradingcore.domain.safety.HoldSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Фабрики сигнала и предикат судьбы принятого риска — группа `U2`
 * документа `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/components/models/HoldSignal.md §Фабрики; причина выхода
 * из штатного ведения — docs/lifecycles/Deal.md §«Причина выхода из
 * штатного ведения»).
 *
 * <p><b>Базовая сборка:</b> фабричный вызов с машинным кодом;
 * коллабораторов нет вовсе — значение неизменяемо и собирается на месте.
 */
class HoldSignalFactoryTest {

    /** Жёсткая инструментная фабрика: пара, код и истинный предикат. */
    @Test
    @DisplayName("U2.1 — жёсткая фабрика инструмента: пара (INSTRUMENT, HARD), предикат истинен")
    void u2_1_theHardInstrumentFactory() {
        HoldSignal signal = HoldSignal.instrument(CODE);

        assertThat(signal.getScope()).isEqualTo(HoldScope.INSTRUMENT);
        assertThat(signal.getRung()).isEqualTo(HoldRung.HARD);
        assertThat(signal.getCode()).as("код донесён").isEqualTo(CODE);
        assertThat(signal.tearsDownRisk()).as("жёсткая ступень снимает принятый риск").isTrue();
    }

    /** Мягкая инструментная фабрика: та же ось, другая ступень. */
    @Test
    @DisplayName("U2.2 — мягкая фабрика инструмента: пара (INSTRUMENT, SOFT), предикат ложен")
    void u2_2_theSoftInstrumentFactory() {
        HoldSignal signal = HoldSignal.instrumentSoft(CODE);

        assertThat(signal.getScope()).isEqualTo(HoldScope.INSTRUMENT);
        assertThat(signal.getRung()).isEqualTo(HoldRung.SOFT);
        assertThat(signal.tearsDownRisk()).as("мягкая ступень принятый риск не трогает").isFalse();
    }

    /** Жёсткая счётная фабрика. */
    @Test
    @DisplayName("U2.3 — жёсткая фабрика счёта: пара (EXCHANGE_ACCOUNT, HARD), предикат истинен")
    void u2_3_theHardAccountFactory() {
        HoldSignal signal = HoldSignal.exchangeAccount(CODE);

        assertThat(signal.getScope()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT);
        assertThat(signal.getRung()).isEqualTo(HoldRung.HARD);
        assertThat(signal.tearsDownRisk()).isTrue();
    }

    /** Мягкая счётная фабрика. */
    @Test
    @DisplayName("U2.4 — мягкая фабрика счёта: пара (EXCHANGE_ACCOUNT, SOFT), предикат ложен")
    void u2_4_theSoftAccountFactory() {
        HoldSignal signal = HoldSignal.exchangeAccountSoft(CODE);

        assertThat(signal.getScope()).isEqualTo(HoldScope.EXCHANGE_ACCOUNT);
        assertThat(signal.getRung()).isEqualTo(HoldRung.SOFT);
        assertThat(signal.tearsDownRisk()).isFalse();
    }

    /**
     * Журнальная фабрика повторяет кортеж мягкой, и различает их только
     * имя вызова — названное ограничение, а не дефект.
     */
    @Test
    @DisplayName("U2.5 — журнальная фабрика инструмента повторяет кортеж мягкой инструментной")
    void u2_5_theInstrumentJournalFactoryRepeatsTheSoftTuple() {
        assertThat(HoldSignal.instrumentJournal(CODE))
                .as("значения неразличимы; различает только имя вызова")
                .isEqualTo(HoldSignal.instrumentSoft(CODE));
    }

    /** То же у счётного радиуса. */
    @Test
    @DisplayName("U2.6 — журнальная фабрика счёта повторяет кортеж мягкой счётной")
    void u2_6_theAccountJournalFactoryRepeatsTheSoftTuple() {
        assertThat(HoldSignal.exchangeAccountJournal(CODE))
                .as("то же названное ограничение")
                .isEqualTo(HoldSignal.exchangeAccountSoft(CODE));
    }

    /** Радиус пары присваивает уводимой сделке причину риск-политики. */
    @Test
    @DisplayName("U2.7 — радиус пары: причина выхода — риск-политика")
    void u2_7_thePairScopeAssignsTheRiskPolicyReason() {
        assertThat(HoldScope.INSTRUMENT.getShutdownReason())
                .isEqualTo(Deal.ShutdownReason.RISK_POLICY);
    }

    /** Радиус счёта присваивает биржевое сворачивание. */
    @Test
    @DisplayName("U2.8 — радиус биржевого счёта: причина выхода — биржевое сворачивание")
    void u2_8_theAccountScopeAssignsTheExchangeHoldReason() {
        assertThat(HoldScope.EXCHANGE_ACCOUNT.getShutdownReason())
                .isEqualTo(Deal.ShutdownReason.EXCHANGE_HOLD);
    }

    /** У ступени ось одна, и значений ровно два. */
    @Test
    @DisplayName("U2.9 — перечень ступени: ровно два значения, второй оси в модели нет")
    void u2_9_theRungEnumHasExactlyTwoValues() {
        assertThat(HoldRung.values())
                .as("судьба принятого риска — единственная ось ступени")
                .containsExactly(HoldRung.SOFT, HoldRung.HARD);
    }

    /** У радиуса значений тоже два; группового значения нет. */
    @Test
    @DisplayName("U2.10 — перечень радиуса: ровно два значения, группового нет")
    void u2_10_theScopeEnumHasExactlyTwoValues() {
        assertThat(HoldScope.values())
                .as("групповой радиус выражается набором строк, а не значением перечня")
                .containsExactly(HoldScope.INSTRUMENT, HoldScope.EXCHANGE_ACCOUNT);
    }
}
