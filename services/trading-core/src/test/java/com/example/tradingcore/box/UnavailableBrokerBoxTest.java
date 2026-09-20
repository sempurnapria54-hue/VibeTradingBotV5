package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Недоступный брокер — клетка {@code B9.5}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> адрес брокера
 * подменяется перекрываемой тропой ({@link BrokerGate}), которую клетка
 * закрывает на первом тике и открывает на втором. Ставить её общему ящику
 * значило бы отнять публикацию у всех его соседок.
 *
 * <p><b>Предмет — ЧТО НЕ СЛУЧИЛОСЬ.</b> Отказ брокера не есть ошибка
 * сделки: решение уже записано и от публикации не зависит — значит статусы
 * сделок стоя́т, ступеней проход не поднимает, а строки просто копятся
 * (docs/components/OutboxRelayJob.md §«Брокер недоступен — проход
 * пропускается, строки копятся»).
 *
 * <p><b>Тик ждётся своим потолком.</b> Отказ приходит не сразу: публикующий
 * клиент ждёт раскладки темы до своего умолчания, а величиной конфигурации
 * сервиса этот потолок не объявлен — цена названа у {@link BrokerGate}.
 */
class UnavailableBrokerBoxTest extends TradingCoreBox {

    /**
     * Потолок ожидания следа тика против закрытой тропы: он длиннее
     * штатного ровно на умолчание клиента брокера.
     */
    private static final Duration REFUSAL_TIMEOUT = Duration.ofSeconds(180);

    /** Перекрываемая тропа: контекст читает её адрес вместо адреса контейнера. */
    private static final BrokerGate GATE =
            BrokerGate.closedBefore(TradingCoreSubstrate.broker().getBootstrapServers());

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-unavailable-broker";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                TradingCoreSubstrate.BROKER_ADDRESS_KEY, GATE.bootstrapServers()));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B9.5 — недоступный брокер проход прекращает, а решения не трогает")
    void anUnavailableBrokerStopsThePassAndLeavesDecisionsAlone() {
        provision(List.of(ACCOUNT, SECOND_ACCOUNT), Map.of(INSTRUMENT, EXTERNAL_INSTRUMENT));
        Long deal = recoveryDeal();
        freeze(ACCOUNT);
        freeze(SECOND_ACCOUNT);
        Long written = rows.count("outbox_events");
        Integer mark = AppLog.mark();

        tick(Tick.OUTBOX_RELAY, REFUSAL_TIMEOUT);

        // Ни одна строка не помечена — проход прекратился на первом отказе.
        assertThat(markedEventIds()).isEmpty();
        assertThat(rows.count("outbox_events")).isEqualTo(written);
        // Статус сделки проход не двигает: публикация к решению отношения
        // не имеет.
        assertThat(rows.row("deals", "id", deal).get("status")).isEqualTo("ACTIVE");
        // Ступеней проход не поднимает: на счетах стои́т ровно та, которую
        // поставила клетка.
        assertThat(rungOf(ACCOUNT)).isEqualTo("HOLD");
        assertThat(rungOf(SECOND_ACCOUNT)).isEqualTo("HOLD");
        // В журнале — строка об отказе.
        assertThat(AppLog.since(mark)).contains("Broker refused the outbox row");

        GATE.open();
        Wire.Mark topic = Wire.mark();
        tick(Tick.OUTBOX_RELAY, REFUSAL_TIMEOUT);

        // Следующий тик на поднятом брокере публикует ВСЁ накопленное.
        assertThat(Wire.publishedSince(topic)).hasSize(written.intValue());
        assertThat(markedEventIds()).hasSize(written.intValue());
    }

    /** Ступень лестницы, стоящая на счёте. */
    private String rungOf(String internalId) {
        return String.valueOf(rows.row("exchange_accounts", "internal_id", internalId).get("safety_rung"));
    }

    /** Идентичности событий строк, у которых отметка публикации стои́т. */
    private List<String> markedEventIds() {
        return rows.select("""
                        select event_id from outbox_events where published_at is not null order by id asc
                        """).stream()
                .map(row -> String.valueOf(row.get("event_id")))
                .toList();
    }

    /**
     * Живая сделка без объявления — то есть восстановительная.
     *
     * <p>Она здесь ради ОТРИЦАНИЯ: «статусов сделок проход не двигает» без
     * единой сделки в базе было бы утверждением ни о чём.
     */
    private Long recoveryDeal() {
        return rows.insert("""
                insert into deals (internal_id, exchange_account_id, instrument_id, status, direction,
                                   entry_reason)
                values ('D1', ?, ?, 'ACTIVE', 'LONG', 'RECOVERY') returning id
                """, accountId(ACCOUNT), instrumentId(INSTRUMENT));
    }
}
