package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.audit.AuditApplication;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Клетка {@code B10.2} — ненастроенная тропа приёма не поднимает
 * контейнера (.claude/tests/cases/audit.md §«B10 — Конфигурация и схема
 * как вход»).
 *
 * <p><b>Предмет клетки — отсутствие МОЛЧАЛИВОГО СТАРТА.</b> Соседний
 * сервис с ненастроенным адресом соседа поднимается и отказывает на
 * вызове; здесь исход другой, и различие несущее: вход у журнала один —
 * события, — и процесс, поднявшийся без приёма, отвечал бы читателю
 * «событий не было» на журнале, в который никто не пишет
 * (docs/concept.md, П1).
 *
 * <p><b>Без {@code @SpringBootTest} по тому же доводу, что у соседней
 * клетки:</b> ожидание есть НЕподъём, а поднятый контекст есть
 * предусловие всякого кейса ящика. Подъём здесь — вход, и производит его
 * сам кейс ({@link AuditSubstrate#launchArguments}).
 *
 * <p><b>Пусто ровно ОДНО, и контур доступа при этом настроен.</b> Иначе
 * отказ сошёлся бы по поводу соседней клетки, и обе предъявляли бы один
 * предмет.
 */
class UnconfiguredReceptionPathTest {

    @Test
    @DisplayName("B10.2 — Ненастроенная тропа приёма не поднимает контейнера")
    void anUnconfiguredReceptionPathRaisesNoContainer() {
        String[] arguments =
                AuditSubstrate.launchArguments(Map.of(AuditSubstrate.BROKER_ADDRESS_KEY, ""));

        assertThatThrownBy(() -> new SpringApplicationBuilder(AuditApplication.class)
                .run(arguments)
                .close())
                .as("пустое означает, что тропа приёма не настроена: старта «без приёма, "
                        + "но с поверхностью» не бывает")
                .isInstanceOf(Exception.class);
    }
}
