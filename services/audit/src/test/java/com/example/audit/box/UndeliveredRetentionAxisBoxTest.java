package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B7.4} — ось не доехала: прохода нет, и он говорит об этом
 * (.claude/tests/cases/audit.md §«B7 — Чистка
 * журнала: применимость из оси, глубина из конфигурации»).
 *
 * <p><b>Отсутствие оси ставится ИЗЪЯТИЕМ ключа</b>
 * ({@link AuditSubstrate#UNSET}), а не пустым значением: исполнителю тогда
 * достаётся собственное умолчание сервиса — ровно то, что он получает в
 * развёртывании без назначенной оси. Подстановка пустой строки мерила бы
 * вдобавок преобразование пустого значения в член перечня, то есть не тот
 * предмет.
 *
 * <p><b>Направление исхода консервативно, и клетка мерит именно его.</b>
 * Удаление необратимо, поэтому чистить по значению, которого никто не
 * назначал, значило бы терять журнал по недоразумению
 * (docs/components/JournalCleanupJob.md §«Ось не доехала — проход не идёт,
 * и это не то же самое, что `UNBOUNDED`»; docs/rules/absent-value-semantics.md).
 *
 * <p><b>Предупреждение — единственное, чем это состояние отличимо от
 * назначенного {@code UNBOUNDED}.</b> Исход у них один, база и поверхность
 * молчат обе, и без записи журнала дефект развёртывания выглядел бы
 * назначенным решением.
 */
class UndeliveredRetentionAxisBoxTest extends SilentCleanupBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b7-4";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG,
                Map.of(AuditSubstrate.RETENTION_PROFILE_KEY, AuditSubstrate.UNSET));
    }

    @Test
    @DisplayName("B7.4 — Ось не доехала: прохода нет, и он говорит об этом")
    void anUndeliveredAxisStopsThePassAndSaysSo() {
        OffsetDateTime gapAt = givenWorkForThePass();
        Integer logMark = AppLog.mark();

        cleanup();

        assertThat(rows.count(JOURNAL_TABLE))
                .as("не удалено ни одной строки: направление консервативно, удаление необратимо")
                .isEqualTo(2L);
        assertThat(instant(pair(subscription().getFirst()), GAP_COLUMN))
                .as("момент разрыва не погашен").isEqualTo(gapAt.toInstant());
        assertThat(AppLog.since(logMark))
                .as("поднято предупреждение: им состояние отличимо от назначенного UNBOUNDED")
                .contains(AXIS_WARNING);
    }
}
