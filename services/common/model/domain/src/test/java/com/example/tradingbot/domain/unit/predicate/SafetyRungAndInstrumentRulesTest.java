package com.example.tradingbot.domain.unit.predicate;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.core.exchange.Exchange;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.InstrumentExternalRules;
import com.example.tradingbot.domain.model.trade.candle.CandleGroup;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Ступени радиусов и справочные правила инструмента — группа `U13`
 * документа `.claude/tests/cases/domain-model-predicates.md`
 * (docs/rules/exchange-hold.md, docs/rules/instrument-hold.md,
 * docs/spec/manual-halt.json {@code rungRankBefore},
 * docs/models/domain/other/InstrumentExternalRules.md,
 * docs/lifecycles/Instrument.md).
 *
 * <p><b>Базовая сборка:</b> биржа, биржевой счёт и инструмент со своими
 * статусами; справочные правила инструмента — с сырыми строковыми
 * значениями площадки.
 *
 * <p><b>Клетка `U13.10` не прогоняется, и это не пропуск.</b> Оба
 * ступенчатых предиката инструмента читают значения перечня, которых ни
 * один сервис не пишет: ступень целевой конструкции стои́т на паре
 * «счёт, инструмент» (находка `D-6`).
 */
class SafetyRungAndInstrumentRulesTest {

    @Test
    @DisplayName("U13.1 — биржа в жёсткой ступени")
    void u13_1_aHardRungBlocksTradeAndEntry() {
        Exchange subject = exchange(Exchange.Status.TRADE_BLOCKED);

        assertThat(subject.isTradeBlocked()).isTrue();
        assertThat(subject.blocksEntry()).isTrue();
    }

    /** Ступени различаются судьбой принятого риска, а не правом набирать новый. */
    @Test
    @DisplayName("U13.2 — биржа в мягкой ступени")
    void u13_2_aSoftRungBlocksEntryOnly() {
        Exchange subject = exchange(Exchange.Status.HOLD);

        assertThat(subject.isTradeBlocked()).isFalse();
        assertThat(subject.blocksEntry()).isTrue();
    }

    @Test
    @DisplayName("U13.3 — биржа в рабочем статусе")
    void u13_3_anActiveExchangeDoesNotBlockEntry() {
        assertThat(exchange(Exchange.Status.ACTIVE).blocksEntry()).isFalse();
    }

    @Test
    @DisplayName("U13.4 — ранг по каждому из трёх статусов счёта порознь")
    void u13_4_theAccountRungScale() {
        assertThat(ExchangeAccount.SafetyRung.TRADE_BLOCKED.rank()).isEqualTo(2);
        assertThat(ExchangeAccount.SafetyRung.HOLD.rank()).isEqualTo(1);
        assertThat(ExchangeAccount.SafetyRung.ACTIVE.rank()).isEqualTo(0);
    }

    /** Ранги двух радиусов сопоставимы между собой. */
    @Test
    @DisplayName("U13.5 — ранг по каждому из трёх ступенчатых статусов инструмента порознь")
    void u13_5_theInstrumentRungScaleIsComparableWithTheAccountOne() {
        assertThat(Instrument.SafetyRung.TRADE_BLOCKED.rank()).isEqualTo(2);
        assertThat(Instrument.SafetyRung.ENTRY_BLOCKED.rank()).isEqualTo(1);
        assertThat(Instrument.SafetyRung.ACTIVE.rank()).isEqualTo(0);
        assertThat(Instrument.SafetyRung.TRADE_BLOCKED.rank())
                .isEqualTo(ExchangeAccount.SafetyRung.TRADE_BLOCKED.rank());
        assertThat(Instrument.SafetyRung.ACTIVE.rank())
                .isEqualTo(ExchangeAccount.SafetyRung.ACTIVE.rank());
    }

    @Test
    @DisplayName("U13.6 — инструмент в статусе загрузки свечей")
    void u13_6_candleLoadingIsRecognised() {
        assertThat(instrument(Instrument.Status.CANDLES_LOADING, List.of()).isCandleLoading()).isTrue();
    }

    @Test
    @DisplayName("U13.7 — у инструмента есть группы свечей и все активны")
    void u13_7_allActiveGroupsMakeItReady() {
        assertThat(instrument(Instrument.Status.SYNC,
                List.of(group(CandleGroup.Status.ACTIVE), group(CandleGroup.Status.ACTIVE)))
                .isReadyForActivation()).isTrue();
    }

    @Test
    @DisplayName("U13.8 — одна группа неактивна")
    void u13_8_oneInactiveGroupBreaksReadiness() {
        assertThat(instrument(Instrument.Status.SYNC,
                List.of(group(CandleGroup.Status.ACTIVE), group(CandleGroup.Status.SYNC)))
                .isReadyForActivation()).isFalse();
    }

    /** Пустой перечень готовности не даёт. */
    @Test
    @DisplayName("U13.9 — групп свечей нет вовсе")
    void u13_9_noGroupsMeansNotReady() {
        assertThat(instrument(Instrument.Status.SYNC, null).isReadyForActivation()).isFalse();
        assertThat(instrument(Instrument.Status.SYNC, List.of()).isReadyForActivation()).isFalse();
    }

    @Test
    @DisplayName("U13.11 — сырые строки спецификаций площадки — валидные десятичные")
    void u13_11_everyAccessorParsesItsRawString() {
        InstrumentExternalRules subject = sizingSpecs("10", "0.1", "1", "0.5");

        assertThat(subject.contractValue()).isEqualByComparingTo("10");
        assertThat(subject.lotSize()).isEqualByComparingTo("0.1");
        assertThat(subject.minSize()).isEqualByComparingTo("1");
        assertThat(subject.tickSize()).isEqualByComparingTo("0.5");
        assertThat(subject.hasSizingSpecs()).isTrue();
    }

    /** Отсутствие спецификации, а не ноль. */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("U13.12 — сырая строка пуста либо состоит из пробелов")
    void u13_12_aBlankRawStringIsEmptiness(String raw) {
        InstrumentExternalRules subject = new InstrumentExternalRules();
        subject.setExternalContractValue(raw);

        assertThat(subject.contractValue()).isNull();
    }

    /** Отказ разбора наружу не выходит. */
    @Test
    @DisplayName("U13.13 — сырая строка нечисловая")
    void u13_13_aNonNumericRawStringIsEmptiness() {
        InstrumentExternalRules subject = new InstrumentExternalRules();
        subject.setExternalContractValue("n/a");

        assertThat(subject.contractValue()).isNull();
    }

    @Test
    @DisplayName("U13.14 — сырая строка с окаймляющими пробелами вокруг числа")
    void u13_14_surroundingSpacesAreTrimmed() {
        InstrumentExternalRules subject = new InstrumentExternalRules();
        subject.setExternalContractValue("  10.5  ");

        assertThat(subject.contractValue()).isEqualByComparingTo("10.5");
    }

    /** Требуется строгая положительность всех трёх. */
    @Test
    @DisplayName("U13.15 — стоимость контракта нулевая при положительных шаге и минимуме")
    void u13_15_aZeroContractValueBreaksTheSizingSpecs() {
        assertThat(sizingSpecs("0", "0.1", "1", "0.5").hasSizingSpecs()).isFalse();
    }

    @Test
    @DisplayName("U13.16 — шаг размера пуст")
    void u13_16_anAbsentLotSizeBreaksTheSizingSpecs() {
        assertThat(sizingSpecs("10", null, "1", "0.5").hasSizingSpecs()).isFalse();
    }

    @Test
    @DisplayName("U13.17 — статус справочных правил — торгуемый")
    void u13_17_liveIsTradable() {
        assertThat(rulesWithStatus(InstrumentExternalRules.Status.LIVE).isLive()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = InstrumentExternalRules.Status.class, names = "LIVE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("U13.18 — статус справочных правил — любой иной из перечня")
    void u13_18_anyOtherStatusIsNotTradable(InstrumentExternalRules.Status status) {
        assertThat(rulesWithStatus(status).isLive()).isFalse();
    }

    private static Exchange exchange(Exchange.Status status) {
        Exchange exchange = new Exchange();
        exchange.setStatus(status);
        return exchange;
    }

    private static Instrument instrument(Instrument.Status status, List<CandleGroup> groups) {
        Instrument instrument = new Instrument();
        instrument.setStatus(status);
        instrument.setCandleGroups(groups);
        return instrument;
    }

    private static CandleGroup group(CandleGroup.Status status) {
        CandleGroup group = new CandleGroup();
        group.setStatus(status);
        return group;
    }

    private static InstrumentExternalRules sizingSpecs(String contractValue, String lotSize,
                                                       String minSize, String tickSize) {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setExternalContractValue(contractValue);
        rules.setExternalLotSize(lotSize);
        rules.setExternalMinSize(minSize);
        rules.setExternalTickSize(tickSize);
        return rules;
    }

    private static InstrumentExternalRules rulesWithStatus(InstrumentExternalRules.Status status) {
        InstrumentExternalRules rules = new InstrumentExternalRules();
        rules.setStatus(status);
        return rules;
    }
}
