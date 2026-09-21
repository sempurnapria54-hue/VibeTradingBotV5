package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.audit.AuditApplication;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Клетка {@code B10.1} — ненастроенный контур доступа не поднимает
 * поверхности (.claude/tests/cases/audit.md §«B10 — Конфигурация и схема
 * как вход»).
 *
 * <p><b>Без {@code @SpringBootTest}, и это следствие предмета:</b>
 * ожидание клетки есть НЕподъём контекста, а поднятый контекст есть
 * предусловие всякого кейса ящика
 * (.claude/decisions/test-contour-design-pass.md, решение 1). Поэтому
 * подъём здесь — вход, и производит его сам кейс
 * ({@link AuditSubstrate#launchArguments}).
 *
 * <p><b>Пусто ровно ОДНО, и прочие оси настроены.</b> Иначе отказ подъёма
 * сошёлся бы по любому из соседних поводов — адресу базы, адресу брокера —
 * и клетка предъявляла бы не свой предмет.
 *
 * <p><b>«Журнальная выборка недоступна ни с токеном, ни без него»
 * предъявляется САМИМ отказом подъёма, а не вызовом.</b> Процесса, у
 * которого можно было бы спросить выборку, не возникает вовсе; порта у
 * упавшего процесса нет, и вызов по нему мерил бы отсутствие слушателя
 * сокета, а не закрытость поверхности.
 */
class UnconfiguredAccessContourTest {

    @Test
    @DisplayName("B10.1 — Ненастроенный контур доступа не поднимает поверхности")
    void anUnconfiguredAccessContourRaisesNoSurface() {
        String[] arguments = AuditSubstrate.launchArguments(Map.of(AuditSubstrate.ISSUER_KEY, ""));

        assertThatThrownBy(() -> new SpringApplicationBuilder(AuditApplication.class)
                .run(arguments)
                .close())
                .as("пустое означает, что контур не настроен, и это отказ, а не открытая поверхность")
                .isInstanceOf(Exception.class);
    }
}
