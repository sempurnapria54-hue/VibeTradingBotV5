package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.1} — отсутствует класс события
 * (.claude/tests/cases/statistics.md).
 *
 * <p>Класс решает, становится ли событие фактом и какого зерна
 * ({@code StatisticsEventListener.requireEnvelope}); пустой делает ветвление
 * произвольным, а молчаливый пропуск потерял бы факт безвозвратно — тропы
 * восстановления у статистики нет ни одной
 * (docs/rules/durable-consumer-reception.md §«Обработчик отказа — часть
 * конструкции, а не настройка, и признак у него механический: восстановимо ли
 * пропущенное»).
 *
 * <p>Исход общий всей группе, и он собран в базовом классе
 * ({@link PoisonedReceptionBox#assertReceptionHalted()}).
 */
class MissingEventTypeBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-1");
    }

    @Test
    @DisplayName("B2.1 — Отсутствует класс события")
    void aMessageWithoutTheEventClassStopsReception() {
        givenReceptionStateRows();

        poisonWithout(EVENT_TYPE);

        assertReceptionHalted();
    }
}
