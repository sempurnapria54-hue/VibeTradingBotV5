package com.example.tradingcore.box;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Боевой режим допуска сверки — вторая половина клетки {@code B5.13} и
 * клетка {@code B5.7}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клеток:</b> положение оси
 * {@code exchange-contour.exchanges.OKX.reconciliation-exploratory} есть
 * предмет кейса. Штатно режим разведочный, и расхождение сверки лестницу
 * не триггерит вовсе (docs/rules/pnl-reconciliation.md §«Реакция на
 * расхождение») — то есть сигнала мягкой ступени, чьё поглощение мерит
 * {@code B5.7}, в штатном контексте не возникает.
 *
 * <p><b>Своя группа потребителя и своя тема владельца определений</b> —
 * довод у шапки {@link TradingCoreSubstrate}.
 */
class StrictReconciliationBoxTest extends LiveDealBox {

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-strict-reconciliation";

    /** Код отчёта и сигнала расхождения сверки. */
    private static final String RECONCILIATION_MISMATCH = "PNL_RECONCILIATION_MISMATCH";

    /** Мягкая ступень биржевого счёта. */
    private static final String HOLD = "HOLD";

    /** Класс события подъёма ступени. */
    private static final String HOLD_RAISED = "HOLD_RAISED";

    /** Реализованный результат в записи закрытия эпизода. */
    private static final String RECORD_PROFIT = "-7";

    /** Сумма движения закрытия: расходится с записью сверх допуска. */
    private static final String BILL_AMOUNT = "-5";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                TradingCoreSubstrate.RECONCILIATION_EXPLORATORY_KEY, "false"));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B5.13 (боевой режим) — тот же вход при выключенном разведочном режиме даёт мягкую ступень")
    void theSameMismatchOutsideTheExploratoryModeRaisesTheSoftRung() {
        openLiveDeal();
        standExchangeFollowingCommands(RECORD_PROFIT, BILL_AMOUNT);

        exitByDeletion(workingDefinition());
        passesUntilDealTerminal();

        assertThat(dealStatus()).isEqualTo("CLOSED");
        assertThat(dealRow().get("reconciliation_status")).isEqualTo("MISMATCHED");
        assertThat(codesOfReports()).contains(RECONCILIATION_MISMATCH);
        assertThat(accountRung()).isEqualTo(HOLD);
    }

    @Test
    @DisplayName("B5.7 — мягкий запрос на объекте под сворачиванием поглощается")
    void theSoftRequestOnAnObjectUnderCollapseIsAbsorbed() {
        openLiveDeal();
        standExchangeFollowingCommands(RECORD_PROFIT, BILL_AMOUNT);
        exitByDeletion(workingDefinition());
        // Выход дошёл до добычи движений, а терминала ещё нет: сверка
        // посчитается на терминале, и её обязанность уже возникла.
        passesUntil(() -> nonNull(dealRow().get("bills_fetched_through")));
        assertThat(dealStatus()).isEqualTo("EXIT_PENDING");
        fullHalt(ACCOUNT);
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        Object rungStandingSince = accountRow().get("modified_at");
        Long raisedOnce = countEvents(HOLD_RAISED);

        passesUntilDealTerminal();

        // Сигнал мягкой ступени того же радиуса случился: сверка аварийного
        // терминала расходится сверх допуска.
        assertThat(dealRow().get("reconciliation_status")).isEqualTo("MISMATCHED");
        // Понижения нет: ступень та же, строка счёта не переставлялась,
        // второго факта подъёма нет — понижает только снятие.
        assertThat(accountRung()).isEqualTo(TRADE_BLOCKED);
        assertThat(accountRow().get("modified_at")).isEqualTo(rungStandingSince);
        assertThat(countEvents(HOLD_RAISED)).isEqualTo(raisedOnce);
    }
}
