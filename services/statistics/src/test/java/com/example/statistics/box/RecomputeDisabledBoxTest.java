package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B7.19} — выключатель гасит тик пересчёта
 * (.claude/tests/cases/statistics.md §«B7 — Пересчёт: окно, порции и охрана
 * начала ряда»).
 *
 * <p><b>Выключатель — ВХОД клетки</b>, поэтому контекст у неё свой: его
 * читает сам тик при каждом такте, а положение оси связывается при подъёме
 * контекста. Тот же довод, что у клетки о снятом выключателе тика приёма
 * ({@link TickDisabledBoxTest}).
 *
 * <p><b>Молчание мерится ДВУМЯ наблюдаемыми, и второе несущее.</b> Пустая
 * таблица агрегатов сама по себе говорит лишь, что строк не появилось, — то
 * же самое сказал бы прошедший и ничего не нашедший проход. Счётчик запросов
 * базы отвечает на другой вопрос: к базе не ушло НИ ОДНОГО запроса
 * группировки, то есть тик не работал вовсе, а не отработал впустую.
 *
 * <p><b>Ряд фактов положен ЗАВЕДОМО собираемым.</b> Факты лежат в сутках,
 * которые открывает полночь: охрана начала ряда ({@code B7.7}) сутки,
 * покрытые рядом частично, гасит сама — и клетка тогда была бы зелена по
 * чужой причине. Что этот же вход при поднятом выключателе строки даёт,
 * держат клетки {@link RecomputeProjectionBoxTest}.
 *
 * <p><b>«Отказа нет» читается жалобами СВОЕГО предмета, а не пустотой
 * журнала.</b> Журнал контекста несёт записи соседей по прогону — брокера,
 * каркаса, — и ассерт о пустоте мерил бы их, а не тик.
 */
class RecomputeDisabledBoxTest extends StatisticsBox {

    /** Краткое имя клетки: из него строятся её группа и её тема. */
    private static final String SLUG = "b7-19";

    /** Сутки, в которых лежит ряд фактов обоих зёрен. */
    private static final Integer FACT_DAY = 2;

    /** Слова, которыми жалоба назвала бы предмет клетки. */
    private static final List<String> OWN_ALARM_WORDS = List.of("ecompute", "ggregate");

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG,
                Map.of(StatisticsSubstrate.RECOMPUTE_ENABLED_KEY, "false"));
    }

    @Test
    @DisplayName("B7.19 — Выключатель гасит тик пересчёта")
    void theSwitchPutsOutTheRecomputeTick() {
        Facts.deal("E-7-19-DEAL", TENANT, midnightDaysAgo(FACT_DAY));
        Facts.incident("E-7-19-INC", TENANT, HOLD_RAISED, midnightDaysAgo(FACT_DAY));
        Integer logMark = AppLog.mark();
        rows.resetStatementCounters();

        assertThatCode(this::recompute)
                .as("такт при снятом выключателе отказом не кончается").doesNotThrowAnyException();

        assertThat(rows.statementCalls(DEAL_GRAIN_QUERY))
                .as("к базе не ушло ни одного запроса группировки сделочного зерна: тик не "
                        + "работал вовсе, а не отработал впустую")
                .isZero();
        assertThat(rows.statementCalls(INCIDENT_GRAIN_QUERY))
                .as("у второго зерна то же: порции не было ни одной").isZero();
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("строк сделочного зерна не появилось").isZero();
        assertThat(rows.count(INCIDENT_AGGREGATES))
                .as("и строк зерна происшествий тоже").isZero();
        assertThat(aggregates(DEAL_GRAIN, TENANT).dealRows())
                .as("поверхность чтения при этом отвечает — и отдаёт пустую страницу")
                .isEmpty();
        assertThat(ownAlarms(logMark))
                .as("жалобы о пересчёте в журнале нет: снятый выключатель — штатная ветвь, "
                        + "а не аномалия для наблюдателя окружения")
                .isEmpty();
        assertThat(rows.count(DEAL_FACTS))
                .as("вход поставлен: собирать было что").isEqualTo(1L);
    }

    /**
     * Жалобы, названные словами СВОЕГО предмета.
     *
     * @param mark отметка журнала, снятая до такта
     */
    private static List<String> ownAlarms(Integer mark) {
        return AppLog.alarmsSince(mark).stream()
                .filter(alarm -> OWN_ALARM_WORDS.stream().anyMatch(alarm::contains))
                .toList();
    }
}
