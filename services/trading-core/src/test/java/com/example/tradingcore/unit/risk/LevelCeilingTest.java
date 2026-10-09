package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.BASE;
import static com.example.tradingcore.unit.risk.RiskFixture.INSTRUMENT_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.account;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.deal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.entryLeg;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.levelAppetite;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Потолки живого риска биржевого счёта и тенанта у преконтроля (дом —
 * docs/rules/risk-policy.md §«Потолки живого риска счёта и тенанта»;
 * формы — docs/spec/risk-limits.json, величины {@code ownLiveRiskRaised},
 * {@code withinAccountSimultaneous}, {@code withinTenantSimultaneous}).
 *
 * <p><b>Числа — тестового окружения держателя:</b> сделка 1 %, счёт 10 %,
 * тенант 30 %. База счёта и база единственного счёта тенанта — 10000, то
 * есть потолок счёта 1000, тенанта 3000. Вход базовой сборки поднимает живой
 * риск своей сделки на 92.955.
 *
 * <p><b>Соседи несут живой риск неисполненной долей живой ноги</b> —
 * заявленный риск ноги без налива: так операнд соседа назван одним числом,
 * а форма его та же, что у проверяемой сделки.
 */
class LevelCeilingTest {

    /** Второй счёт того же тенанта. */
    private static final Long SECOND_ACCOUNT_ID = 4L;

    private final RiskHarness harness = new RiskHarness();

    @BeforeEach
    void givenTheTestEnvironmentNumbers() {
        harness.givenAppetite(levelAppetite("10", "30"));
    }

    @Test
    @DisplayName("Соседей нет: вход в пределах потолков счёта и тенанта проходит")
    void anEntryWithoutPeersPasses() {
        assertThat(codes(harness.validate(entryAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("Сосед по счёту ровно добирает потолок счёта вместе со входом: граница включена")
    void aPeerOnTheAccountExactlyAtTheAccountCeilingPasses() {
        harness.givenLevelDeals(List.of(peer(2L, ACCOUNT_ID, "907.045")));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .as("907.045 + 92.955 = 1000 ровно в потолок 10 % × 10000")
                .isEmpty();
    }

    @Test
    @DisplayName("Сосед по счёту и вход выше потолка счёта: отказ кодом счёта, тенант в пределах")
    void aPeerOnTheAccountOverTheAccountCeilingIsRejected() {
        harness.givenLevelDeals(List.of(peer(2L, ACCOUNT_ID, "907.046")));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .as("1000.001 против 1000 у счёта; против 3000 у тенанта — в пределах")
                .containsExactly(RiskCheckCode.RISK_PER_ACCOUNT_SIMULTANEOUS_EXCEEDED);
    }

    @Test
    @DisplayName("Сосед на другом счёте тенанта: отказ кодом тенанта, база тенанта — сумма баз счетов")
    void aPeerOnAnotherAccountOverTheTenantCeilingIsRejected() {
        harness.givenTenantAccounts(List.of(account(BASE), account(SECOND_ACCOUNT_ID, BASE)));
        harness.givenLevelDeals(List.of(peer(2L, SECOND_ACCOUNT_ID, "5907.046")));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .as("5907.046 + 92.955 против 30 % × 20000 = 6000; у счёта сделки соседей нет")
                .containsExactly(RiskCheckCode.RISK_PER_TENANT_SIMULTANEOUS_EXCEEDED);
    }

    @Test
    @DisplayName("Счёт тенанта в другой валюте базы в базу тенанта не входит, и его сделки — тоже")
    void anAccountInAnotherBaseCurrencyIsLeftOutOfTheTenantLevel() {
        ExchangeAccount otherCurrency = account(SECOND_ACCOUNT_ID, "1000000");
        otherCurrency.setRiskBaseCurrency("USDC");
        harness.givenTenantAccounts(List.of(account(BASE), otherCurrency));
        harness.givenLevelDeals(List.of(peer(2L, SECOND_ACCOUNT_ID, "2907.046")));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .as("база тенанта 10000, а не 1010000; сделка счёта в USDC в операнд не входит")
                .isEmpty();
    }

    @Test
    @DisplayName("Живой риск соседа не измерен: акт, повышающий живой риск, отвергается кодами уровней")
    void anUnmeasuredPeerRejectsARaisingAct() {
        harness.givenLevelDeals(List.of(unprotectedPeer(2L, ACCOUNT_ID)));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .as("неизмеренный уровень выполненным не читается")
                .containsExactly(RiskCheckCode.RISK_PER_ACCOUNT_SIMULTANEOUS_EXCEEDED,
                        RiskCheckCode.RISK_PER_TENANT_SIMULTANEOUS_EXCEEDED);
    }

    @Test
    @DisplayName("Акт живого риска своей сделки не повышает: уровни истинны, соседей не грузят")
    void anActNotRaisingTheOwnLiveRiskLeavesTheLevelsAlone() {
        Deal live = deal(BigDecimal.ZERO, null);
        live.setPositions(List.of(episode("10", ANCHOR)));
        live.setTranches(List.of(tranche(List.of(), List.of(protection(STOP.toPlainString())))));

        assertThat(codes(harness.validate(protectionAction("2950"), context(live))))
                .as("подтяжка уровня с 2910 до 2950 живой риск снижает")
                .isEmpty();
        verifyNoInteractions(harness.levelDealsBoundary());
    }

    @Test
    @DisplayName("Процент счёта не принят: повышающий акт отвергается кодом незаданного числа")
    void anUnacceptedAccountPercentRejectsARaisingAct() {
        harness.givenAppetite(levelAppetite(null, "30"));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .containsExactly(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("Процент тенанта не принят: повышающий акт отвергается кодом незаданного числа")
    void anUnacceptedTenantPercentRejectsARaisingAct() {
        harness.givenAppetite(levelAppetite("10", null));

        assertThat(codes(harness.validate(entryAction(), workingContext())))
                .containsExactly(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED);
    }

    /**
     * Соседняя живая сделка названного счёта: одна живая нога без налива
     * с названным заявленным риском — это и есть её живой риск.
     */
    private static Deal peer(Long dealId, Long accountId, String legRisk) {
        Deal peer = deal(BigDecimal.ZERO, null);
        peer.setId(dealId);
        peer.setExchangeAccountId(accountId);
        peer.setInstrumentId(INSTRUMENT_ID);
        peer.setTranches(List.of(tranche(List.of(entryLeg(Order.Status.ACTIVE, legRisk, "100", "0")), List.of())));
        return peer;
    }

    /**
     * Соседняя сделка с живым эпизодом и без действующего уровня защиты:
     * её живой риск не измерен.
     */
    private static Deal unprotectedPeer(Long dealId, Long accountId) {
        Deal peer = deal(BigDecimal.ZERO, null);
        peer.setId(dealId);
        peer.setExchangeAccountId(accountId);
        peer.setInstrumentId(INSTRUMENT_ID);
        peer.setPositions(List.of(episode("10", ANCHOR)));
        peer.setTranches(List.of(tranche(List.of(entryLeg(Order.Status.COMPLETED, "0", "10", "10")), List.of())));
        return peer;
    }
}
