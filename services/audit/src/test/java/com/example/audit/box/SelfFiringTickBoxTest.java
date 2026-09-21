package com.example.audit.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B10.6}, четвёртая её величина — такт тика состояния приёма
 * приходит конфигурацией (.claude/tests/cases/audit.md §«B10 —
 * Конфигурация и схема как вход»).
 *
 * <p><b>Такта ящик не подаёт, и в этом весь предмет.</b> Прочие клетки
 * прогона бьют тик прямым вызовом метода джобы — единственной точкой
 * касания бина (решение 6), — потому что штатный такт прогона выражен
 * часовой паузой и второго удара за прогон не даёт. Здесь наоборот:
 * вызова нет ни одного, и строки состояния заводит САМ тик. При штатной
 * оси не появилось бы ни одной строки за всё время клетки.
 *
 * <p><b>Мало появления строк — нужен ПОВТОР такта.</b> Строки завёл бы и
 * одиночный удар при любом такте; что такт именно тот, который назначен,
 * предъявляет момент обновления: единственный его писатель — тик, и он
 * ставит момент своего удара. Момент, сдвинувшийся внутри окна клетки,
 * есть второй удар, случившийся без всякого участия кейса.
 *
 * <p><b>Своя группа и свои темы обязательны</b> по общему признаку класса
 * со своим положением осей; вдобавок живой тик этого контекста писал бы
 * строки чужих пар, будь группа общей.
 */
class SelfFiringTickBoxTest extends AuditBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b10-6-tick";

    /** Такт тика этого контекста: полсекунды вместо часа. */
    private static final Duration TICK_INTERVAL = Duration.ofMillis(500);

    /**
     * Окно наблюдения второго удара.
     *
     * <p>Оно на порядки у́же штатного часового такта: сдвиг момента внутри
     * него не объясним ничем, кроме назначенной оси.
     */
    private static final Duration SELF_FIRING_WINDOW = Duration.ofSeconds(20);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG,
                Map.of(AuditSubstrate.STATE_TICK_INTERVAL_KEY, TICK_INTERVAL.toMillis() + "ms"));
    }

    @Test
    @DisplayName("B10.6 — Такт тика приходит конфигурацией: удары идут без участия кейса")
    void theTickIntervalArrivesByConfigurationAndFiresOnItsOwn() {
        Awaitility.await()
                .atMost(SELF_FIRING_WINDOW)
                .pollInterval(POLL)
                .until(() -> Objects.equals(rows.count(RECEPTION_TABLE),
                        (long) subscription().size()));

        Object firstUpdate = pair(subject()).get(UPDATED_COLUMN);
        Awaitility.await()
                .atMost(SELF_FIRING_WINDOW)
                .pollInterval(POLL)
                .until(() -> isFalse(Objects.equals(firstUpdate,
                        pair(subject()).get(UPDATED_COLUMN))));

        assertThat(pairs())
                .as("строки обеих пар завёл сам тик: такта кейс не подавал ни разу")
                .hasSize(subscription().size());
        assertThat(pair(subject()).get(UPDATED_COLUMN))
                .as("момент обновления сдвинут вторым ударом, случившимся по назначенной оси")
                .isNotEqualTo(firstUpdate);
    }

    /** Тема, по строке которой клетка читает моменты ударов. */
    private String subject() {
        return AuditSubstrate.ownCoreTopic(SLUG);
    }
}
