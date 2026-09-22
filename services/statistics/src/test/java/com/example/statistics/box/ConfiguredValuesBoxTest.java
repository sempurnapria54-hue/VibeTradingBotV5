package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B12.7} — пауза повтора и доля срока хранения суть
 * конфигурация, а не константы (.claude/tests/cases/statistics.md §«B12 —
 * Конфигурация и схема как вход»).
 *
 * <p><b>Величин у кейса шесть, а здесь их две, и остальные четыре УЖЕ
 * имеют носителей.</b> Второй записи им не заводится
 * (.claude/rules/carrier-levels.md): окно пересчёта несут клетки окна
 * ({@code B7.1}, {@code B7.15} — {@code B7.17}), допустимый возраст строки
 * состояния — {@code B6.4}, размер страницы и предел окна чтения —
 * {@code B10.17}, а оба ТАКТА — соседний класс
 * ({@link SelfFiringJobsBoxTest}), потому что наблюдаются они тем, что джоба
 * бьётся САМА.
 *
 * <p><b>Каждое значение СДВИНУТО так, что умолчание сервиса дало бы другой
 * исход.</b> Совпадающее с умолчанием значение не отличало бы «величина
 * доехала» от «взято умолчание»: пауза в двадцать секунд не даёт повтора в
 * окне, в котором пятисекундное умолчание дало бы четыре; четверть срока
 * хранения даёт вдвое меньший порог, чем умолчание в половину.
 *
 * <p><b>«Сам срок хранения в конфигурации не хранится» предъявляется СМЕНОЙ
 * срока У БРОКЕРА.</b> Порог, поехавший вслед за чужой величиной, которой
 * сервис не знает ни одним ключом, и есть предъявление того, что срок
 * добывается обходом, а не читается из настроек
 * (docs/components/ReceptionStateJob.md §«Срок хранения темы добывается тем
 * же обходом»).
 *
 * <p><b>Числа попыток повтора рядом с паузой нет вовсе, и здесь это не
 * повторяется:</b> предъявляет его {@code B2.11} — сотня пауз подряд без
 * единого продвижения смещения.
 *
 * <p><b>Своя группа и своя тема обязательны:</b> клетка о паузе ломает приём
 * на время своего окна, а клетка о пороге меняет срок хранения темы — и то,
 * и другое есть состояние, общее всем контекстам одного имени.
 */
class ConfiguredValuesBoxTest extends StatisticsBox {

    /** Краткое имя клеток: из него строятся их группа и их тема. */
    private static final String SLUG = "b12-7";

    /** Пауза повтора этого контекста: двадцать секунд вместо пяти. */
    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(20);

    /**
     * Окно, в котором повтора не случается.
     *
     * <p>Оно ШИРЕ умолчания сервиса и у́же назначенной паузы: при
     * пятисекундном умолчании запись была бы принята внутри него, при
     * двадцатисекундной паузе — нет.
     */
    private static final Duration WITHOUT_RETRY = Duration.ofSeconds(8);

    /** Доля срока хранения этого контекста: четверть вместо половины. */
    private static final Double LAG_ALERT_FRACTION = 0.25;

    /** Первый срок хранения темы, назначаемый у брокера. */
    private static final Duration FIRST_RETENTION = Duration.ofHours(4);

    /** Второй срок: им предъявляется, что порог едет за чужой величиной. */
    private static final Duration SECOND_RETENTION = Duration.ofHours(10);

    /** Возраст события, с которым клетка ходит в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put(StatisticsSubstrate.RETRY_INTERVAL_KEY, RETRY_INTERVAL.toSeconds() + "s");
        axes.put(StatisticsSubstrate.LAG_ALERT_FRACTION_KEY, String.valueOf(LAG_ALERT_FRACTION));
        StatisticsSubstrate.registerOwn(registry, SLUG, axes);
    }

    @Test
    @DisplayName("B12.7 — Пауза между повторами приходит конфигурацией")
    void theRetryPauseArrivesByConfiguration() {
        givenReceptionStateRows();

        rows.withoutTable(DEAL_FACTS, () -> {
            publish(subject(), "E-RETRIED", DEAL_CLOSED, momentsAgo(EVENT_AGE),
                    Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY));
            awaitHalted(subject());
        });

        Awaitility.await()
                .during(WITHOUT_RETRY)
                .atMost(WITHOUT_RETRY.plusSeconds(2))
                .pollInterval(POLL)
                .until(() -> Objects.equals(rows.count(DEAL_FACTS), 0L));
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(rows.count(DEAL_FACTS), 1L));

        assertThat(dealFact().get("event_id"))
                .as("повтор пришёл после назначенной паузы, а не после умолчания в пять секунд")
                .isEqualTo("E-RETRIED");
    }

    @Test
    @DisplayName("B12.7 — Доля срока хранения приходит конфигурацией, а сам срок — от брокера")
    void theAlertFractionArrivesByConfigurationWhileTheRetentionComesFromTheBroker() {
        Wire.setRetention(subject(), FIRST_RETENTION);
        givenReceptionStateRows();
        tick();

        assertThat(thresholdRowOf(subject()))
                .as("порог есть назначенная ДОЛЯ добытого срока; при умолчании в половину "
                        + "он был бы вдвое больше")
                .isEqualTo(Math.round(FIRST_RETENTION.toMillis() * LAG_ALERT_FRACTION));

        Wire.setRetention(subject(), SECOND_RETENTION);
        tick();

        assertThat(thresholdRowOf(subject()))
                .as("срок сменён У БРОКЕРА, и порог поехал за ним: в конфигурации сервиса "
                        + "самого срока нет — его добывает обход")
                .isEqualTo(Math.round(SECOND_RETENTION.toMillis() * LAG_ALERT_FRACTION));
    }

    /** Тема, с которой работают обе клетки этого контекста. */
    private String subject() {
        return StatisticsSubstrate.ownTopic(SLUG);
    }
}
