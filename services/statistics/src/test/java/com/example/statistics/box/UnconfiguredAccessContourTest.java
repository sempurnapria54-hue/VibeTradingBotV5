package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.statistics.StatisticsApplication;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Клетка {@code B12.1} — ненастроенный контур доступа не поднимает
 * поверхности (.claude/tests/cases/statistics.md §«B12 — Конфигурация и
 * схема как вход»).
 *
 * <p><b>Без {@code @SpringBootTest}, и это следствие предмета:</b> ожидание
 * клетки есть НЕподъём контекста, а поднятый контекст есть предусловие
 * всякого кейса ящика (.claude/decisions/test-contour-design-pass.md,
 * решение 1). Поэтому подъём здесь — вход, и производит его сам кейс
 * ({@link StatisticsSubstrate#launchArguments}).
 *
 * <p><b>Пусто ровно ОДНО, и прочие оси настроены.</b> Иначе отказ подъёма
 * сошёлся бы по любому из соседних поводов — адресу базы, адресу брокера — и
 * клетка предъявляла бы не свой предмет.
 *
 * <p><b>«Ни одна закрытая точка не отвечает 200» предъявляется САМИМ отказом
 * подъёма, а не вызовом.</b> Процесса, у которого можно было бы спросить
 * агрегатную выборку, не возникает вовсе; порта у упавшего процесса нет, и
 * вызов по нему мерил бы отсутствие слушателя сокета, а не закрытость
 * поверхности.
 */
class UnconfiguredAccessContourTest {

    @Test
    @DisplayName("B12.1 — Ненастроенный контур доступа не поднимает поверхности")
    void anUnconfiguredAccessContourRaisesNoSurface() {
        String[] arguments =
                StatisticsSubstrate.launchArguments(Map.of(StatisticsSubstrate.ISSUER_KEY, ""));

        assertThatThrownBy(() -> new SpringApplicationBuilder(StatisticsApplication.class)
                .run(arguments)
                .close())
                .as("пустое означает, что контур не настроен, и это отказ, а не открытая поверхность")
                .isInstanceOf(Exception.class);
    }
}
