package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B3.5} и {@code B3.9} — состав пар берётся из объявленной
 * подписки, а неизмеренная величина уносит свой ряд
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Обе клетки стоя́т на ОДНОМ положении осей</b>, поэтому живут одним
 * классом: подписка объявляет две темы, а у брокера заведена одна.
 * Оставшаяся тема назначения не даёт ни одной партицией — и она же не
 * отдаёт срока хранения; первое есть вход клетки о составе, второе — вход
 * клетки о рядах.
 *
 * <p><b>Пустое назначение поставлено ОТСУТСТВИЕМ темы у брокера, и это
 * выбор из двух поводов.</b> Второй — отдача партиции другой реплике той
 * же группы — зависит от назначающего и от протокола группы, то есть
 * сделал бы предусловие гонкой. Наблюдаемое у обоих поводов одно:
 * объявленная подписка шире назначения, и состав строк обязан следовать
 * подписке (docs/components/ReceptionStateJob.md §«Состав пар берётся из
 * объявленной подписки, а не из назначения»).
 *
 * <p><b>Приём при этом ЖИВ:</b> контейнер запущен и держит партицию
 * заведённой темы, а живость мерится назначением ЦЕЛИКОМ, не потемно.
 */
class DeclaredSubscriptionBoxTest extends AuditBox {

    /** Краткое имя клеток: из него строятся их группа и их темы. */
    private static final String SLUG = "b3-5";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG,
                List.of(AuditSubstrate.ownCoreTopic(SLUG)),
                List.of(AuditSubstrate.ownCoreTopic(SLUG), AuditSubstrate.ownStrategyTopic(SLUG)),
                Map.of());
    }

    @Test
    @DisplayName("B3.5 — Состав берётся из объявленной подписки, а не из назначенных партиций")
    void theCompositionComesFromTheDeclaredSubscriptionRatherThanFromAssignedPartitions() {
        givenReceptionStateRows();

        assertThat(pairs()).as("строка есть у каждой объявленной темы").hasSize(2);
        assertThat(pair(unassignedTopic()).get(SUBSCRIBED_COLUMN))
                .as("тема без назначения подписана: по назначению строка получила бы ложь "
                        + "при живой подписке — ошибка в разрешающую сторону")
                .isEqualTo(Boolean.TRUE);
        assertThat(lowerBound()).as("нижняя граница от этого не опускается").isNotNull();
        assertThat(continuityClaimable()).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("B3.9 — Неизмеренная величина уносит СВОЙ ряд, а не подменяется нулём")
    void anUnmeasuredValueCarriesAwayItsOwnRowRatherThanBecomingZero() {
        givenReceptionStateRows();

        tick();

        assertThat(ageRowOf(assignedTopic())).as("возраст есть у каждой подписанной пары").isNotNull();
        assertThat(ageRowOf(unassignedTopic())).isNotNull();
        assertThat(thresholdRowOf(assignedTopic()))
                .as("порог есть там, где срок темы добыт").isNotNull().isPositive();
        assertThat(thresholdRowOf(unassignedTopic()))
                .as("а у темы, чей срок не добыт, ряда порога нет вовсе — ни ноля, ни бесконечности")
                .isNull();
    }

    /** Тема, партицию которой контейнер держит: у брокера она заведена. */
    private String assignedTopic() {
        return AuditSubstrate.ownCoreTopic(SLUG);
    }

    /** Тема объявленной подписки, которой у брокера нет. */
    private String unassignedTopic() {
        return AuditSubstrate.ownStrategyTopic(SLUG);
    }
}
