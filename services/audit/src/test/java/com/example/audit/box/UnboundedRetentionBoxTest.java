package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B7.3} — неограниченный профиль: предмета у прохода нет, и
 * он молчит (.claude/tests/cases/audit.md §«B7 — Чистка
 * журнала: применимость из оси, глубина из конфигурации»).
 *
 * <p><b>Профиль хранения — ВХОД клетки</b>, поэтому контекст у неё свой:
 * ось приезжает при подъёме ключом манифеста сервиса.
 *
 * <p><b>Предмет здесь — МОЛЧАНИЕ, а не бездействие.</b> Бездействие у
 * этой клетки общее с соседней: при недоехавшей оси проход тоже не идёт.
 * Различает их дом ровно следом — назначенное значение проходит молча, а
 * пустота поднимает предупреждение
 * (docs/components/JournalCleanupJob.md §«Ось не доехала — проход не
 * идёт, и это не то же самое, что `UNBOUNDED`»), — и единственный
 * наблюдатель этого различия есть журнал приложения ({@link AppLog}): ни
 * база, ни поверхность обоих состояний не разводят ничем.
 *
 * <p><b>Отметка журнала снимается ДО такта.</b> Приёмник копит записи
 * всех кейсов прогона, и утверждение о молчании, прочитанное с начала,
 * ловило бы чужое предупреждение соседней клетки — то есть краснело бы по
 * поводу, которого эта не ставила.
 */
class UnboundedRetentionBoxTest extends SilentCleanupBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b7-3";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG,
                Map.of(AuditSubstrate.RETENTION_PROFILE_KEY, "UNBOUNDED"));
    }

    @Test
    @DisplayName("B7.3 — Неограниченный профиль: предмета у прохода нет, и он молчит")
    void anUnboundedProfileLeavesThePassWithoutASubjectAndItStaysSilent() {
        OffsetDateTime gapAt = givenWorkForThePass();
        Integer logMark = AppLog.mark();

        cleanup();

        assertThat(rows.count(JOURNAL_TABLE))
                .as("не удалено ни одной строки, хотя одна из них старше любой мыслимой глубины")
                .isEqualTo(2L);
        assertThat(instant(pair(subscription().getFirst()), GAP_COLUMN))
                .as("момент разрыва не погашен ни у одной пары").isEqualTo(gapAt.toInstant());
        assertThat(AppLog.since(logMark))
                .as("и предупреждения нет: назначенное значение проходит молча")
                .doesNotContain(AXIS_WARNING);
    }
}
