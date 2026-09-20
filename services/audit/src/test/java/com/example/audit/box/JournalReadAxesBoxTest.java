package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B8.16} — размер страницы и предел окна приходят
 * конфигурацией (.claude/tests/cases/audit.md §«B8 — Журнальная выборка
 * чтения»).
 *
 * <p><b>Обе величины — ВХОД клетки, поэтому контекст у неё свой.</b>
 * Положение осей есть часть ключа кэша контекста, и сдвинуть его внутри
 * класса нечем: соседние шестнадцать клеток группы живут на штатных осях
 * ({@link JournalReadBoxTest}), а эта — на своих.
 *
 * <p><b>«Двигается вслед за конфигурацией» предъявляется ТРЕМЯ окнами
 * одного прогона, а не сравнением с чужим классом.</b> Окно ровно в
 * назначенный предел принимается, шире его на секунду — отвергается, а
 * окно штатного предела, законное у соседей, отвергается здесь: граница
 * приёма стои́т там, где её назначила ось, и ни в одном другом месте.
 * Сравнение с соседним классом того же не сказало бы — он поднимает
 * СВОЙ контекст, и разошлись бы заодно все прочие его оси.
 *
 * <p><b>Своя группа и свои темы обязательны</b>
 * ({@link AuditSubstrate#registerOwn}): группа есть состояние на брокере, и
 * контекст со своим именем читал бы чужую тему с начала.
 *
 * <p><b>Ожидание отказа берёт класс и названный домом повод, а не
 * число</b> (.claude/tests/cases/audit.md §«Число ответа и класс отказа —
 * разные ожидания»); числом пиньнут только контракт успеха.
 */
class JournalReadAxesBoxTest extends AuditBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b8-16";

    /** Предел ширины окна ЭТОГО контекста: он у́же штатного на двое суток с лишним. */
    private static final Duration OWN_MAX_WINDOW = Duration.ofHours(2);

    /** Размер страницы ЭТОГО контекста: он меньше штатного на строку. */
    private static final Integer OWN_PAGE_SIZE = 2;

    /** Имя величины нижней границы полноты: ею читается, что ответ дошёл до выборки. */
    private static final String BOUND_FIELD = "lowerBound";

    /** Имя предиката непрерывности — второй величины того же состава. */
    private static final String CLAIMABLE_FIELD = "continuityClaimable";

    /** Имя поля класса отказа в едином error-DTO. */
    private static final String CODE_FIELD = "code";

    /** Имя поля пояснения в том же DTO. */
    private static final String MESSAGE_FIELD = "message";

    /** Класс отказа нашего кода: вопрос чтения не принят. */
    private static final String QUERY_REJECTED = "QUERY_NOT_ACCEPTED";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG, Map.of(
                AuditSubstrate.MAX_WINDOW_KEY, OWN_MAX_WINDOW.toHours() + "h",
                AuditSubstrate.PAGE_SIZE_KEY, String.valueOf(OWN_PAGE_SIZE)));
    }

    @Test
    @DisplayName("B8.16 — Размер страницы и предел окна приходят конфигурацией")
    void thePageSizeAndTheWindowLimitArriveByConfiguration() {
        String topic = subscription().getFirst();
        publish(topic, "E-AXIS-1", momentsAgo(Duration.ofMinutes(10)), Bodies.reference());
        publish(topic, "E-AXIS-2", momentsAgo(Duration.ofMinutes(20)), Bodies.reference());
        publish(topic, "E-AXIS-3", momentsAgo(Duration.ofMinutes(30)), Bodies.reference());
        awaitRecordCount(3L);
        awaitConsumed(topic);
        OffsetDateTime to = now();

        Answer exact = page(to.minus(OWN_MAX_WINDOW), to);
        Answer wider = page(to.minus(OWN_MAX_WINDOW).minusSeconds(1), to);
        Answer sharedLimit = page(to.minus(AuditSubstrate.MAX_WINDOW), to);

        assertThat(exact.status())
                .as("окно ровно в назначенный предел принимается").isEqualTo(200);
        assertThat(wider.asObject().get(CODE_FIELD))
                .as("шире назначенного на секунду — уже нет").isEqualTo(QUERY_REJECTED);
        assertThat(String.valueOf(wider.asObject().get(MESSAGE_FIELD)))
                .as("и текст называет НАЗНАЧЕННЫЙ предел, а не умолчание сервиса")
                .contains(String.valueOf(OWN_MAX_WINDOW));
        assertThat(sharedLimit.asObject().get(CODE_FIELD))
                .as("окно штатного предела, законное у соседей, здесь отвергнуто: "
                        + "граница приёма двигается вслед за конфигурацией")
                .isEqualTo(QUERY_REJECTED);

        assertThat(exact.records())
                .as("число строк страницы равно настроенному, а не умолчанию сервиса")
                .hasSize(OWN_PAGE_SIZE);
        assertThat(exact.nextCursor())
                .as("и третья строка осталась за страницей: позиция продолжения есть")
                .isNotNull();
        assertThat(exact.completeness())
                .as("полнота едет с выдачей как обычно — своим составом")
                .containsKeys(BOUND_FIELD, CLAIMABLE_FIELD);

        // Параметром запроса ни то, ни другое не задаётся: читатель не может
        // попросить окно шире предела и страницу больше настроенной.
        Answer asked = page(to.minus(OWN_MAX_WINDOW), to,
                "pageSize", String.valueOf(OWN_PAGE_SIZE + 1),
                "maxWindow", AuditSubstrate.MAX_WINDOW.toDays() + "d");
        Answer askedWider = page(to.minus(OWN_MAX_WINDOW).minusSeconds(1), to,
                "maxWindow", AuditSubstrate.MAX_WINDOW.toDays() + "d");

        assertThat(asked.records())
                .as("запрошенный размер страницы не читается: строк по-прежнему настроенное число")
                .hasSize(OWN_PAGE_SIZE);
        assertThat(askedWider.asObject().get(CODE_FIELD))
                .as("запрошенный предел окна не читается тоже: вопрос по-прежнему не принят")
                .isEqualTo(QUERY_REJECTED);
    }

    /** Страница названного окна этого контекста с операндами сверх него. */
    private Answer page(OffsetDateTime from, OffsetDateTime to, String... operands) {
        return get(journalPath(from, to, operands), TENANT);
    }
}
