package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Выключенное реле — клетка {@code B7.8}
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>Свой контекст, и это ВХОД клетки:</b> выключатель реле приезжает
 * конфигурацией, и общему ящику он отнял бы публикацию у всех соседок.
 * Расписание в прогоне глушится ВЫРАЖЕНИЕМ такта именно поэтому — чтобы
 * выключатель остался входом вот этой клетки
 * ({@link StrategiesSubstrate}).
 *
 * <p><b>Предмет — ОБЕ тропы, а не одна.</b> Выключатель гасит и
 * расписание, и ручной триггер; клетка подаёт ручной, потому что
 * расписание в прогоне не бьёт вовсе, а гашение тропы, которой нет,
 * утверждением не является.
 *
 * <p><b>Конец прохода ждётся записью фасада</b>: у выключенного реле
 * наблюдаемого следа нет ни одного по построению, и без этой записи
 * отрицание мерило бы скорость теста ({@link StrategiesBox#relayPass()}).
 */
class DisabledRelayBoxTest extends StrategiesBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StrategiesSubstrate.register(registry,
                Map.of(StrategiesSubstrate.RELAY_ENABLED_KEY, "false"));
    }

    @Test
    @DisplayName("B7.8 — Выключенное реле не публикует даже по ручному тику")
    void b7_8_aDisabledRelayPublishesNothingEvenOnAManualTick() {
        peerResolvesEverything();
        givenActive(TENANT);
        assertThat(rows.count(OUTBOX_TABLE)).as("неопубликованная строка есть").isEqualTo(1L);
        Wire.Mark mark = Wire.mark();

        Answer answer = relayPass();

        assertThat(answer.status())
                .as("фасад отвечает за запуск, а не за работу: выключенность наружу не транслируется")
                .isEqualTo(202);
        assertThat(Wire.publishedSinceOrNone(mark))
                .as("в тему не ушло ничего")
                .isEmpty();
        assertThat(marked()).as("отметки не ставятся").isEmpty();
        assertThat(rows.count(OUTBOX_TABLE)).isEqualTo(1L);
    }
}
