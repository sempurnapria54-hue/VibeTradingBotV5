package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B7.5} — снятый выключатель гасит проход раньше оси
 * (.claude/tests/cases/audit.md §«B7 — Чистка
 * журнала: применимость из оси, глубина из конфигурации»).
 *
 * <p><b>Выключатель — ВХОД клетки</b>, поэтому контекст у неё свой: его
 * читает сам такт, а положение оси приезжает при подъёме
 * (.claude/rules/codestyle.md §Джобы — каждая джоба с выключателем и CRON
 * в конфиге, и при снятом флаге тик не делает ничего).
 *
 * <p><b>Профиль хранения при этом ЧИСТЯЩИЙ, и это несущая половина
 * входа.</b> Снятый выключатель поверх неограниченного профиля давал бы
 * тот же исход по второй причине, и клетка не различала бы порядка: она
 * утверждает, что выключатель гасит проход РАНЬШЕ оси — то есть что до
 * чтения профиля такт не доходит вовсе.
 *
 * <p><b>Отсутствие предупреждения — не довесок, а способ прочитать этот
 * порядок.</b> Ветвь оси у исполнителя одна и пишет запись при пустом
 * значении; здесь ось непуста, поэтому молчание говорит лишь о том, что
 * ветвь исполнилась и промолчала. Порядок предъявляет вторая половина:
 * такт возвращается, не тронув ничего, при чистящем профиле — единственном
 * значении, при котором проход обязан был бы идти.
 */
class CleanupDisabledBoxTest extends SilentCleanupBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b7-5";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG,
                Map.of(AuditSubstrate.CLEANUP_ENABLED_KEY, "false"));
    }

    @Test
    @DisplayName("B7.5 — Снятый выключатель гасит проход раньше оси")
    void aLoweredSwitchStopsThePassBeforeTheAxisIsEvenRead() {
        OffsetDateTime gapAt = givenWorkForThePass();
        Integer logMark = AppLog.mark();

        cleanup();

        assertThat(rows.count(JOURNAL_TABLE))
                .as("не удалено ни одной строки, хотя профиль хранения ЧИСТЯЩИЙ")
                .isEqualTo(2L);
        assertThat(instant(pair(subscription().getFirst()), GAP_COLUMN))
                .as("момент разрыва не погашен").isEqualTo(gapAt.toInstant());
        assertThat(AppLog.since(logMark))
                .as("предупреждения об оси нет: ось непуста, и ветвь её промолчала бы в любом случае")
                .doesNotContain(AXIS_WARNING);
    }
}
