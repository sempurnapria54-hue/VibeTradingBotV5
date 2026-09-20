package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B3.6} — неживой приём: тик молчит и уносит ряды
 * (.claude/tests/cases/audit.md).
 *
 * <p><b>Назначение отнимается СНЯТИЕМ ТЕМ у брокера.</b> Второй повод,
 * названный кейсом, — остановленный контейнер слушателя — потребовал бы
 * коснуться второго бина, а решение 6 называет единственной такой точкой
 * тик (.claude/decisions/test-contour-design-pass.md). Темы, которой у
 * брокера нет, партиций нет тоже, и назначение пустеет само — ровно то
 * состояние, которое дом называет «группа развалилась либо связи с
 * брокером нет».
 *
 * <p><b>Допустимый возраст строки сокращён, и это ВХОД клетки, а не
 * подкрутка.</b> Третий конъюнкт предиката непрерывности — свежесть самой
 * строки состояния, и её истечение есть предмет последнего ожидания:
 * молчащий измеритель обязан кончиться ответом «не утверждаема», а не
 * молчаливым «дыры нет». При умолчании в пять минут клетка мерила бы то
 * же самое, платя за это пятью минутами прогона.
 *
 * <p><b>Контекст закрывается вместе с классом</b>: тем его подписки у
 * брокера больше нет, и переиспользовать такой контекст некому.
 */
@DirtiesContext
class ReceptionNotLiveBoxTest extends AuditBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b3-6";

    /** Допустимый возраст строки состояния у этого контекста. */
    private static final String STATE_MAX_AGE = "2s";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG,
                Map.of(AuditSubstrate.STATE_MAX_AGE_KEY, STATE_MAX_AGE));
    }

    @Test
    @DisplayName("B3.6 — Неживой приём: тик молчит и уносит ряды")
    void whenReceptionIsNotLiveTheTickKeepsSilentAndCarriesTheSeriesAway() {
        givenReceptionStateRows();
        tick();
        assertThat(receptionRowCount()).as("ряды выпущены прошлым тактом").isPositive();
        assertThat(continuityClaimable()).as("строка состояния свежа").isEqualTo(Boolean.TRUE);

        Wire.deleteTopics(subscription());

        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> {
                    tick();
                    return Objects.equals(receptionRowCount(), 0L);
                });
        Map<String, Object> frozen = pair(subscription().getFirst());

        tick();

        assertThat(pair(subscription().getFirst()).get(UPDATED_COLUMN))
                .as("момент обновления не двинулся: тик молчит")
                .isEqualTo(frozen.get(UPDATED_COLUMN));
        assertThat(pair(subscription().getFirst()).get(SUBSCRIBED_COLUMN))
                .as("признак подписки не переписан").isEqualTo(frozen.get(SUBSCRIBED_COLUMN));
        assertThat(receptionRowCount())
                .as("рядов приёма не осталось ни одного — они пропали, а не обнулились").isZero();
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(Boolean.FALSE, continuityClaimable()));
    }
}
