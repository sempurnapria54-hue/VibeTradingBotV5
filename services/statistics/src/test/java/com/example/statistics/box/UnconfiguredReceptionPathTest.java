package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.statistics.StatisticsApplication;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Клетка {@code B12.2} — ненастроенная тропа приёма не поднимает контейнера
 * (.claude/tests/cases/statistics.md §«B12 — Конфигурация и схема как вход»).
 *
 * <p><b>Предмет клетки — отсутствие МОЛЧАЛИВОГО СТАРТА.</b> Сервис с
 * ненастроенным адресом соседа поднимается и отказывает на вызове; здесь
 * исход другой, и различие несущее: вход у статистики один — события, — и
 * процесс, поднявшийся без приёма, отвечал бы читателю числами, собранными из
 * фактов, которых никто не кладёт, и полнотой, которую некому опровергнуть
 * (docs/concept.md, П1).
 *
 * <p><b>Молчание тика этого не спасает, и потому ожидание клетки именно
 * НЕподъём.</b> Тик состояния приёма при неживом приёме уносит ряды и строк
 * не двигает ({@code B3.6}), но предикат непрерывности на пустой области
 * квантора отвечает «не утверждаема» — то есть читателю виден отказ
 * НАБЛЮДЕНИЯ, а не отказ настройки, и отличить их он ничем не может.
 *
 * <p><b>Без {@code @SpringBootTest} по тому же доводу, что у соседней
 * клетки:</b> ожидание есть НЕподъём, а поднятый контекст есть предусловие
 * всякого кейса ящика. Подъём здесь — вход, и производит его сам кейс
 * ({@link StatisticsSubstrate#launchArguments}).
 *
 * <p><b>Пусто ровно ОДНО, и контур доступа при этом настроен.</b> Иначе отказ
 * сошёлся бы по поводу соседней клетки, и обе предъявляли бы один предмет.
 */
class UnconfiguredReceptionPathTest {

    @Test
    @DisplayName("B12.2 — Ненастроенная тропа приёма не поднимает контейнера")
    void anUnconfiguredReceptionPathRaisesNoContainer() {
        String[] arguments = StatisticsSubstrate.launchArguments(
                Map.of(StatisticsSubstrate.BROKER_ADDRESS_KEY, ""));

        assertThatThrownBy(() -> new SpringApplicationBuilder(StatisticsApplication.class)
                .run(arguments)
                .close())
                .as("пустое означает, что тропа приёма не настроена: старта «без приёма, "
                        + "но с поверхностью» не бывает")
                .isInstanceOf(Exception.class);
    }
}
