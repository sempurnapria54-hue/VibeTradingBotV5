package com.example.tradingcore.unit.safety;

import static com.example.tradingcore.unit.safety.SafetyFixture.ACCOUNT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.TENANT_ID;
import static com.example.tradingcore.unit.safety.SafetyFixture.account;
import static com.example.tradingcore.unit.safety.SafetyFixture.deal;
import static com.example.tradingcore.unit.safety.SafetyFixture.position;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.exchange_account.ExchangeAccount;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.core.tenant.Tenant;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.safety.LossStreakCounter;
import com.example.tradingcore.persistence.service.ExchangeAccountDataService;
import com.example.tradingcore.persistence.service.TenantRiskAppetiteDataService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Серия убытков: ценовой результат и порог — группа `U14` документа
 * `.claude/tests/cases/trading-core-safety.md`
 * (дом — docs/rules/loss-streak-halt.md §«Что двигает счётчик»;
 * исполнимая форма — docs/spec/loss-streak-halt.json).
 *
 * <p><b>Базовая сборка:</b> счётчик серии; службы счёта и риск-аппетита
 * тенанта подменены; контекст несёт закрытую сделку с предъявленным
 * целиком графом, итоговым результатом и эпизодами; порог тенанта
 * назначен.
 *
 * <p><b>Накопленная издержка финансирования собирается настоящими
 * эпизодами</b>, а не подменённым предикатом: сумма по эпизодам живёт
 * на модели (.claude/rules/codestyle.md §«Тесты доменных моделей»).
 */
class LossStreakCounterTest {

    private static final Integer LIMIT = 3;

    private final ExchangeAccountDataService accounts = mock(ExchangeAccountDataService.class);
    private final TenantRiskAppetiteDataService appetites =
            mock(TenantRiskAppetiteDataService.class);

    private LossStreakCounter counter;

    @BeforeEach
    void setUp() {
        counter = new LossStreakCounter(accounts, appetites);
        limit(LIMIT);
    }

    private void limit(Integer value) {
        Tenant tenant = new Tenant();
        tenant.setGlobalConsecutiveLossLimit(value);
        when(appetites.findByTenantInternalId(TENANT_ID)).thenReturn(Optional.of(tenant));
    }

    private DealContext context(BigDecimal resultProfit, Boolean graphComplete,
                                ExchangeAccount exchangeAccount, BigDecimal fundingCost) {
        Deal closed = deal(91L);
        closed.setStatus(Deal.Status.CLOSED);
        closed.setResultProfit(resultProfit);
        closed.setPositions(new ArrayList<>(List.of(
                position(Position.Status.CLOSED, BigDecimal.ZERO, fundingCost))));
        return DealContext.builder()
                .deal(closed)
                .exchangeAccount(exchangeAccount)
                .graphComplete(graphComplete)
                .build();
    }

    private DealContext lossContext(Integer countBefore) {
        ExchangeAccount exchangeAccount = account();
        exchangeAccount.setConsecutiveLossCount(countBefore);
        return context(new BigDecimal("-12.5"), true, exchangeAccount, BigDecimal.ZERO);
    }

    /** Ценовой убыток увеличивает счётчик. */
    @Test
    @DisplayName("U14.1 — ценовой результат отрицателен: служба счёта получает «убыток», счётчик на модели +1")
    void u14_1_aPriceLossIncrementsTheCounter() {
        DealContext context = lossContext(1);

        counter.applyTerminal(context);

        verify(accounts).applyLossStreak(ACCOUNT_ID, true);
        assertThat(context.getExchangeAccount().getConsecutiveLossCount()).isEqualTo(2);
    }

    /** Ценовая прибыль обнуляет. */
    @Test
    @DisplayName("U14.2 — ценовой результат положителен: служба счёта получает «прибыль», счётчик — ноль")
    void u14_2_aPriceProfitResetsTheCounter() {
        ExchangeAccount exchangeAccount = account();
        exchangeAccount.setConsecutiveLossCount(2);
        DealContext context = context(new BigDecimal("7.25"), true, exchangeAccount, BigDecimal.ZERO);

        counter.applyTerminal(context);

        verify(accounts).applyLossStreak(ACCOUNT_ID, false);
        assertThat(context.getExchangeAccount().getConsecutiveLossCount()).isZero();
    }

    /** Ноль серию не обнуляет. */
    @Test
    @DisplayName("U14.3 — ценовой результат — ноль: служба счёта не позвана, счётчик как был")
    void u14_3_aZeroResultLeavesTheCounterAlone() {
        ExchangeAccount exchangeAccount = account();
        exchangeAccount.setConsecutiveLossCount(2);
        DealContext context = context(BigDecimal.ZERO, true, exchangeAccount, BigDecimal.ZERO);

        counter.applyTerminal(context);

        verify(accounts, never()).applyLossStreak(anyLong(), any());
        assertThat(context.getExchangeAccount().getConsecutiveLossCount()).isEqualTo(2);
    }

    /** Недоступный результат тем более не обнуляет. */
    @Test
    @DisplayName("U14.4 — итогового результата на сделке нет: служба счёта не позвана, счётчик как был")
    void u14_4_anAbsentResultLeavesTheCounterAlone() {
        ExchangeAccount exchangeAccount = account();
        exchangeAccount.setConsecutiveLossCount(2);

        counter.applyTerminal(context(null, true, exchangeAccount, BigDecimal.ZERO));

        verify(accounts, never()).applyLossStreak(anyLong(), any());
        assertThat(exchangeAccount.getConsecutiveLossCount()).isEqualTo(2);
    }

    /** На усечённой загрузке счётчик замораживается. */
    @Test
    @DisplayName("U14.5 — граф предъявлен не целиком: служба счёта не позвана")
    void u14_5_anIncompleteGraphFreezesTheCounter() {
        counter.applyTerminal(context(new BigDecimal("-12.5"), false, account(), BigDecimal.ZERO));

        verify(accounts, never()).applyLossStreak(anyLong(), any());
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b>
     * Благоприятное умолчание запрещено (docs/rules/absent-value-semantics.md
     * §«Благоприятное умолчание запрещено»), а гейт спрашивает ЛОЖНОСТЬ
     * признака, а не отсутствие истины: на пустом он не срабатывает, и
     * счётчик двигается по непредъявленному графу. Красный прогон и есть
     * предъявление находки `S-5` (`.claude/work/backlog.md` §«Пустой
     * признак полноты графа преконтроль читает как предъявленный»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U14.6 — признак полноты графа не объявлен вовсе: тот же исход, что у ложного")
    void u14_6_anAbsentGraphFlagReadsAsNotPresented() {
        counter.applyTerminal(context(new BigDecimal("-12.5"), null, account(), BigDecimal.ZERO));

        verify(accounts, never()).applyLossStreak(anyLong(), any());
    }

    /** Знак финансирования в домене нормализован издержкой: возврат её в число есть сложение. */
    @Test
    @DisplayName("U14.7 — итог и накопленная издержка финансирования заданы: операнд — их сумма")
    void u14_7_thePriceResultAddsBackTheFundingCost() {
        ExchangeAccount exchangeAccount = account();
        exchangeAccount.setConsecutiveLossCount(0);

        counter.applyTerminal(context(new BigDecimal("-2.00"), true, exchangeAccount,
                new BigDecimal("3.00")));

        verify(accounts).applyLossStreak(ACCOUNT_ID, false);
        assertThat(exchangeAccount.getConsecutiveLossCount())
                .as("ценовой результат положителен: -2.00 + 3.00")
                .isZero();
    }

    /** Порог достигнут ходом. */
    @Test
    @DisplayName("U14.8 — убыточная сделка доводит счётчик до порога: предел достигнут")
    void u14_8_theLimitIsReached() {
        assertThat(counter.applyTerminal(lossContext(LIMIT - 1))).isTrue();
    }

    /** Ниже порога — не достигнут. */
    @Test
    @DisplayName("U14.9 — счётчик после хода ниже порога: предел не достигнут")
    void u14_9_belowTheLimitIsNotReached() {
        assertThat(counter.applyTerminal(lossContext(0))).isFalse();
    }

    /** Провизорное значение не подставляется. */
    @Test
    @DisplayName("U14.10 — порог у тенанта не назначен: предел не достигнут")
    void u14_10_anUnsetLimitNeverTriggers() {
        limit(null);

        assertThat(counter.applyTerminal(lossContext(10))).isFalse();
    }

    /** Строки риск-аппетита нет вовсе — тот же исход. */
    @Test
    @DisplayName("U14.11 — строки риск-аппетита у тенанта нет вовсе: тот же исход")
    void u14_11_anAbsentAppetiteRowNeverTriggers() {
        when(appetites.findByTenantInternalId(TENANT_ID)).thenReturn(Optional.empty());

        assertThat(counter.applyTerminal(lossContext(10))).isFalse();
    }

    /** Пустой счётчик читается нулём. */
    @Test
    @DisplayName("U14.12 — счётчик на счёте пуст: читается нулём, убыток даёт единицу")
    void u14_12_anAbsentCounterReadsAsZero() {
        DealContext context = lossContext(null);

        counter.applyTerminal(context);

        assertThat(context.getExchangeAccount().getConsecutiveLossCount()).isEqualTo(1);
    }

    /** Порог мерится по счёту, а не по ходу. */
    @Test
    @DisplayName("U14.13 — нулевой результат на счёте, стоящем на пороге: предел достигнут, счётчик не двинут")
    void u14_13_theLimitIsMeasuredOnTheAccountNotTheMove() {
        ExchangeAccount exchangeAccount = account();
        exchangeAccount.setConsecutiveLossCount(LIMIT);

        assertThat(counter.applyTerminal(context(BigDecimal.ZERO, true, exchangeAccount,
                BigDecimal.ZERO))).isTrue();
        assertThat(exchangeAccount.getConsecutiveLossCount()).isEqualTo(LIMIT);
    }

    /** Исход сделки холд не снимает никогда. */
    @Test
    @DisplayName("U14.14 — прибыльная сделка на счёте, стоящем на пороге: счётчик обнулён, предел не достигнут, ступень не снята")
    void u14_14_aProfitResetsTheCounterButClearsNoRung() {
        ExchangeAccount exchangeAccount = account(ExchangeAccount.SafetyRung.HOLD);
        exchangeAccount.setConsecutiveLossCount(LIMIT);

        assertThat(counter.applyTerminal(context(new BigDecimal("5.00"), true, exchangeAccount,
                BigDecimal.ZERO))).isFalse();
        assertThat(exchangeAccount.getConsecutiveLossCount()).isZero();
        verify(accounts, never()).clearRung(anyLong(), any(), any());
    }
}
