package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Снятие риска при выключенной петле — клетка {@code B5.15}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> положение оси
 * {@code deal-orchestrator.enabled} есть предмет кейса. В общем контексте
 * петля тоже не бьётся сама — расписание глушится выражением такта
 * ({@link TradingCoreSubstrate} §шапка), — но её ручной фасад жив, и
 * утверждение «снятие от петли не зависит» мерило бы там только то, что
 * клетка тика не подавала.
 *
 * <p><b>Живой риск ставится ВОССТАНОВЛЕНИЕМ, и выбора здесь нет.</b> Тропа
 * до налитого входа идёт проходами сопровождения, а их выключатель гасит
 * — вместе с расписанием и ручной тик (.claude/rules/codestyle.md
 * §Джобы). Единственная тропа ящика к активной сделке с живым риском, не
 * проходящая через петлю, — проактивная детекция: позиция без объясняющей
 * сделки заводит сделку вокруг себя (кейс {@code B7.1}).
 *
 * <p><b>Своя группа потребителя и своя тема владельца определений</b> —
 * довод у шапки {@link TradingCoreSubstrate}.
 */
class DisabledPassHaltBoxTest extends TradingCoreBox {

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-disabled-deal-pass";

    /** Биржевой идентификатор найденного эпизода. */
    private static final String POSITION_EXTERNAL_ID = "ex-live-1";

    /** Живой эпизод по инструменту контура, не объяснённый ни одной сделкой. */
    private static final String LIVE_POSITION = Feed.livePosition(POSITION_EXTERNAL_ID, EXTERNAL_INSTRUMENT,
            "5", "100", "2026-09-20T10:00:05Z");

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                TradingCoreSubstrate.PASS_ENABLED_KEY, "false"));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B5.15 — kill-switch петле не подчинён")
    void theKillSwitchDoesNotDependOnTheLoop() {
        openRecoveredLiveDeal();
        // Позиция живёт до закрытия и исчезает с чтения, следующего за ним:
        // снятие риска подтверждается фактами, и подтверждение — тоже
        // предмет клетки.
        connector.answersInTurn(accountPath() + "/positions/instrument", LIVE_POSITION, LIVE_POSITION,
                Feed.absent());
        connector.answers(accountPath() + "/positions/closures", Feed.ack("ex-close-1", "close-1"));
        connector.answers(accountPath() + "/positions/closed", Feed.emptyArray());
        connector.answers(accountPath() + "/positions", Feed.emptyArray());
        PeerStub.all().forEach(PeerStub::forgetRequests);

        fullHalt(ACCOUNT);

        // Снятие риска состоялось без единого прохода сопровождения:
        // закрытие позиции ушло, и отчёт доведён до терминала снимком
        // «после» — подтверждение тоже не ждало петли.
        assertThat(connector.requests(accountPath() + "/positions/closures")).hasSize(1);
        Map<String, Object> report = rows.all("anomaly_reports").getFirst();
        assertThat(report.get("status")).isEqualTo("COMPLETED");
        assertThat(report.get("internal_after")).isNotNull();
        assertThat(String.valueOf(rows.row("exchange_accounts", "internal_id", ACCOUNT).get("safety_rung")))
                .isEqualTo("TRADE_BLOCKED");
        // Каскад сделок при этом ждёт своего тика: сделка уведена в ошибку
        // решением реакции, а довести её до аварийного терминала некому —
        // выключенный тик ничего не делает.
        tick(Tick.DEAL_ORCHESTRATOR);
        assertThat(rows.all("deals").getFirst().get("status")).isEqualTo("ERROR");
    }

    /**
     * Активная сделка с живым риском, заведённая ВОССТАНОВЛЕНИЕМ — тиком
     * проактивной детекции на позиции, которую не объясняет ни одна
     * сделка.
     */
    private void openRecoveredLiveDeal() {
        provision(List.of(ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        connector.answers(accountPath() + "/positions", Feed.array(LIVE_POSITION));
        connector.answers(accountPath() + "/orders/pending", Feed.emptyArray());
        connector.answers(accountPath() + "/algo-orders/pending", Feed.emptyArray());
        tick(Tick.ANOMALY_DETECTION);
        assertThat(rows.count("deals")).isEqualTo(1L);
        assertThat(rows.all("deals").getFirst().get("status")).isEqualTo("ACTIVE");
        assertThat(rows.all("deals").getFirst().get("entry_reason")).isEqualTo("RECOVERY");
    }

    /** Корень путей счёта у коннектора. */
    private String accountPath() {
        return "/api/v1/accounts/" + ACCOUNT;
    }
}
