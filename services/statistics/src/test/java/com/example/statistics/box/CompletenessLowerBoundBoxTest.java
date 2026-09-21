package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B5.1}, {@code B5.2}, {@code B5.3} и {@code B5.5} — нижняя
 * граница полноты на штатном положении осей
 * (.claude/tests/cases/statistics.md §«B5 — Полнота: нижняя граница ОДНИМ
 * операндом»).
 *
 * <p><b>Класс равен КОНФИГУРАЦИИ КОНТЕКСТА</b>, как у соседних групп: все
 * четыре клетки берут штатные оси — включённый тик, такт, до которого прогон
 * не доживает, подписку на две заведённые темы, — и расходятся только
 * состоянием своей группы и своего ряда фактов, которое каждая ставит себе
 * сама.
 *
 * <p><b>Тем у подписки две, и нужны они ровно одной клетке.</b> Разведённые
 * моменты наблюдения двух пар — вход клетки о максимуме, и на единственной
 * теме такое утверждение не выразимо вовсе: сравнивать не с чем. Прочим трём
 * вторая тема безразлична, и своего контекста они ради неё не заводят.
 *
 * <p><b>Клетки, ПЕРЕТРЯХИВАЮЩЕЙ назначение партиций, здесь нет намеренно, и
 * это не раскладка по вкусу.</b> Назначение перетряхивается чужим участником
 * группы, и рябь от его входа и выхода переживает клетку: строка пары
 * получает новый момент наблюдения тогда, когда соседняя клетка уже читает
 * границу. Клетка о монотонности границы ({@code B5.6}) поэтому живёт у
 * {@link ObservationRestartBoxTest} — там ВСЕ клетки назначение трогают, и
 * ряби не от кого наследовать. Замер, на котором это записано: равенство
 * границы моменту строки пары в клетке {@code B5.1} разошлось на
 * микросекундах после того, как в этот класс была добавлена клетка с
 * перетряхиванием.
 *
 * <p><b>Моменты наблюдения ставятся прямой записью, и это durable-ВХОД, а не
 * подмена выхода.</b> Тик заводит строку моментом своего такта, то есть
 * «сейчас», и обе строки одного такта получают ОДИН момент; клеткам же нужен
 * порядок двух моментов либо момент, названный относительно ряда фактов.
 * Форма строки объявлена домом и читается наружу
 * (docs/rules/durable-consumer-reception.md §«Строка состояния приёма —
 * таблица `reception_states`»), поэтому запись по колонкам говорит о том же, о
 * чём читает ассерт. Тик момента наблюдения у заведённой строки не
 * переписывает ({@code ReceptionStateRepository.openPair} поглощает конфликт
 * по ключу пары), поэтому такт после записи её не отменяет.
 *
 * <p><b>Граница у статистики считается ОДНИМ операндом</b> — позднейшим
 * моментом наблюдения пар (docs/spec/durable-reception.json,
 * {@code receptionLowerBound}): факты не чистятся ни в одном окружении, значит
 * начало ряда двигать нечем, а второй оси времени факт не хранит вовсе
 * (docs/models/domain/other/StatisticsFact.md §«Состояние приёма и полнота
 * чисел статистики»). Отсюда клетка соседа о ВТОРОМ операнде здесь
 * превращается в клетку о его ОТСУТСТВИИ.
 *
 * <p><b>Отсутствие операнда доказывается рядом, лежащим ЦЕЛИКОМ позже момента
 * наблюдения, и иначе оно недоказуемо.</b> Второй операнд соседа есть НАЧАЛО
 * ряда следствий, и максимум с ним виден только тогда, когда начало ряда
 * позже: ряд, у которого хоть одна строка лежит раньше, даёт тот же максимум и
 * при существующем операнде — то есть не различает ничего. Отсюда у клетки
 * {@code B5.2} две половины и порядок между ними: сперва ряд целиком поздний
 * (операнда нет), затем к нему добавляется древняя строка (чистки нет).
 * Записано это по промаху контрольного прогона: первая редакция клетки клала
 * обе строки разом и на оси «второй операнд заведён» оставалась зелёной.
 *
 * <p><b>Величины читаются ЧЕРЕЗ ПОВЕРХНОСТЬ</b>
 * ({@link StatisticsBox#lowerBound()}), а не считаются тестом по колонкам:
 * предмет их в том, что статистика объявляет о себе читателю рядом с числами
 * (docs/rules/durable-consumer-reception.md §«Величины едут рядом с числами, а
 * не отдельным запросом»).
 */
class CompletenessLowerBoundBoxTest extends StatisticsBox {

    /** Краткое имя клеток: из него строятся их группа и их темы. */
    private static final String SLUG = "b5-1";

    /** Биржевой счёт — обязательный ключ сделочного зерна. */
    private static final String ACCOUNT = "ACCOUNT-1";

    /** Определение стратегии — второй компонент ключа зерна. */
    private static final String STRATEGY = "S-1";

    /** Возраст события, с которым клетки ходят в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Насколько раньше «сейчас» наблюдается пара с РАННИМ моментом. */
    private static final Duration EARLIER_OBSERVATION = Duration.ofHours(2);

    /** Насколько раньше «сейчас» наблюдается пара с ПОЗДНЕЙШИМ моментом. */
    private static final Duration LATER_OBSERVATION = Duration.ofHours(1);

    /** Насколько раньше «сейчас» наблюдаются пары там, где момент у них общий. */
    private static final Duration OBSERVATION_AGO = Duration.ofHours(2);

    /**
     * Сколько суток назад лежат факты, которые проход обязан собрать.
     *
     * <p><b>Момент у них — ПОЛНОЧЬ, и это не украшение.</b> Проход пишет сутки
     * только начавшиеся не раньше начала ряда фактов
     * (docs/spec/statistics-aggregates.json, {@code dayRecomputable}), и сутки
     * сегодняшние по факту, легшему в их середине, покрыты частично — строк
     * агрегатов по ним не появляется ни одной.
     */
    private static final Integer BUCKET_DAYS_BACK = 1;

    /** Насколько глубже любой мыслимой глубины хранения лежит древний факт. */
    private static final Integer ANCIENT_DAYS_BACK = 60;

    /** Имя группы потребителя журнала: ею предъявляется чужая строка состояния. */
    private static final String JOURNAL_GROUP = "audit.journal";

    /** Тема чужой группы: своего производителя у неё в прогоне нет. */
    private static final String JOURNAL_TOPIC = "trading-core.facts";

    /** Насколько ПОЗЖЕ всех своих моментов наблюдается чужая пара. */
    private static final Duration JOURNAL_AHEAD = Duration.ofHours(12);

    /** Слово, которым сегодня подписаны величины полноты: предмет находки F-2. */
    private static final String JOURNAL_WORD = "урнал";

    /** Запись момента наблюдения одной пары. */
    private static final String SET_PAIR_OBSERVED = """
            update reception_states set observed_since = ? where consumer_group = ? and topic = ?
            """;

    /** Запись момента наблюдения по всем строкам своей группы. */
    private static final String SET_OBSERVED = """
            update reception_states set observed_since = ? where consumer_group = ?
            """;

    /** Снятие признака подписки со всех строк своей группы. */
    private static final String DROP_SUBSCRIPTION = """
            update reception_states set subscribed = false where consumer_group = ?
            """;

    /** Вставка строки ЧУЖОЙ группы: колонки те же, которыми её заводит её тик. */
    private static final String OPEN_FOREIGN_PAIR = """
            insert into reception_states
                (consumer_group, topic, observed_since, subscribed, reception_halted, updated_at)
            values (?, ?, ?, true, true, ?)
            """;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        List<String> both = List.of(
                StatisticsSubstrate.ownTopic(SLUG), StatisticsSubstrate.ownSecondTopic(SLUG));
        StatisticsSubstrate.registerOwn(registry, SLUG, both, both, Map.of());
    }

    @Test
    @DisplayName("B5.1 — Граница есть позднейший момент наблюдения пар")
    void theBoundIsTheLatestObservationMomentOfThePairs() {
        givenReceptionStateRows();
        OffsetDateTime earlier = momentsAgo(EARLIER_OBSERVATION);
        OffsetDateTime later = momentsAgo(LATER_OBSERVATION);
        rows.write(SET_PAIR_OBSERVED, earlier, consumerGroup(), first());
        rows.write(SET_PAIR_OBSERVED, later, consumerGroup(), second());
        givenFactsOfBothGrains();

        assertThat(instant(pair(second()), OBSERVED_COLUMN))
                .as("вход поставлен: моменты наблюдения двух пар разведены")
                .isAfter(instant(pair(first()), OBSERVED_COLUMN));
        assertThat(aggregates(DEAL_GRAIN, TENANT).dealRows())
                .as("и факты сделочного зерна собраны").hasSize(1);
        assertThat(aggregates(INCIDENT_GRAIN, TENANT).incidentRows())
                .as("и факты зерна происшествий тоже").hasSize(1);
        assertThat(lowerBoundMoment().toInstant())
                .as("границей служит ПОЗДНЕЙШИЙ момент наблюдения")
                .isEqualTo(later.toInstant());
        assertThat(lowerBoundMoment().toInstant())
                .as("меньший момент её не опускает: минимумом граница не является")
                .isAfter(earlier.toInstant());
        assertThat(lowerBoundMoment().toInstant())
                .as("величина взята из СВОЕЙ базы — она дословно равна тому, что в строке пары")
                .isEqualTo(instant(pair(second()), OBSERVED_COLUMN));
        assertThat(aggregates(INCIDENT_GRAIN, TENANT).completeness().get("lowerBound"))
                .as("зерно спрошенной страницы границы не меняет: величина о приёме, а не о числах")
                .isEqualTo(aggregates(DEAL_GRAIN, TENANT).completeness().get("lowerBound"));
    }

    @Test
    @DisplayName("B5.2 — Второго операнда у границы нет: чистки фактов не существует")
    void thereIsNoSecondOperandBecauseFactsAreNeverCleaned() {
        givenReceptionStateRows();
        OffsetDateTime observed = momentsAgo(OBSERVATION_AGO);
        rows.write(SET_OBSERVED, observed, consumerGroup());

        // Половина первая: ряд лежит ЦЕЛИКОМ позже момента наблюдения. Будь
        // начало ряда вторым операндом, граница уехала бы к нему.
        publish(first(), "E-FRESH", DEAL_CLOSED, momentsAgo(EVENT_AGE),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(first());
        assertThat(instant(dealFactOf("E-FRESH"), CLOSED_COLUMN))
                .as("вход поставлен: начало ряда следствий лежит ПОЗЖЕ момента наблюдения")
                .isAfter(observed.toInstant());
        assertThat(lowerBoundMoment().toInstant())
                .as("граница осталась моментом наблюдения: начало ряда её не поднимает")
                .isEqualTo(observed.toInstant());

        // Половина вторая: ряд уходит глубже любой мыслимой глубины хранения,
        // и ни один проход контекста его не трогает.
        publish(first(), "E-ANCIENT", DEAL_CLOSED, midnightDaysAgo(ANCIENT_DAYS_BACK),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(first());
        assertThat(instant(dealFactOf("E-ANCIENT"), CLOSED_COLUMN))
                .as("вход поставлен: часть ряда лежит РАНЬШЕ момента наблюдения")
                .isBefore(observed.toInstant());

        tick();
        tick();
        recompute();
        recompute();

        assertThat(rows.count(DEAL_FACTS))
                .as("исполнителя чистки фактов в контексте нет ни одного: не удалено ни строки, "
                        + "хотя одна из них глубже любой мыслимой глубины хранения")
                .isEqualTo(2L);
        assertThat(lowerBoundMoment().toInstant())
                .as("граница равна моменту наблюдения и после всех проходов")
                .isEqualTo(observed.toInstant());
        assertThat(lowerBoundMoment().toInstant())
                .as("к началу сохранившегося ряда она не поднимается")
                .isNotEqualTo(instant(dealFactOf("E-ANCIENT"), CLOSED_COLUMN));
    }

    @Test
    @DisplayName("B5.3 — Ни одной подписанной пары: границы нет, и это значение")
    void withoutASubscribedPairThereIsNoBoundAtAll() {
        givenReceptionStateRows();
        publish(first(), "E-ROWS", DEAL_CLOSED, midnightDaysAgo(BUCKET_DAYS_BACK),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        awaitConsumed(first());
        recompute();
        rows.write(DROP_SUBSCRIPTION, consumerGroup());

        assertThat(pairs())
                .as("вход поставлен: строки пар есть, и все сняты с подписки")
                .hasSize(2)
                .allMatch(row -> Boolean.FALSE.equals(row.get(SUBSCRIBED_COLUMN)));
        Answer page = aggregates(DEAL_GRAIN, TENANT);
        assertThat(page.completeness().get("lowerBound"))
                .as("границы нет, и это значение — не ноль и не эпоха").isNull();
        assertThat(page.completeness().get("continuityClaimable"))
                .as("обе величины говорят об одном состоянии одно и то же")
                .isEqualTo(Boolean.FALSE);
        assertThat(page.dealRows())
                .as("строки агрегатов при этом отдаются: отсутствие границы их не прячет")
                .hasSize(1);
    }

    @Test
    @Tag("debt")
    @DisplayName("B5.5 — Величины едут СВОИ, а не журнальные")
    void theValuesAreItsOwnRatherThanTheJournalsOnes() {
        givenReceptionStateRows();
        OffsetDateTime observed = momentsAgo(OBSERVATION_AGO);
        rows.write(SET_OBSERVED, observed, consumerGroup());
        OffsetDateTime journalAhead = observed.plus(JOURNAL_AHEAD);
        rows.write(OPEN_FOREIGN_PAIR, JOURNAL_GROUP, JOURNAL_TOPIC, journalAhead, now());

        assertThat(rows.row(RECEPTION_TABLE, GROUP_COLUMN, JOURNAL_GROUP).get(HALTED_COLUMN))
                .as("вход поставлен: в той же базе лежит строка ЖУРНАЛЬНОЙ группы, "
                        + "и приём по ней встал")
                .isEqualTo(Boolean.TRUE);
        assertThat(journalAhead.toInstant())
                .as("а наблюдается она позже всех своих пар: журнальная величина видна была бы сразу")
                .isAfter(observed.toInstant());
        assertThat(lowerBoundMoment().toInstant())
                .as("граница прочитана из строки СВОЕЙ группы в своей базе")
                .isEqualTo(observed.toInstant());
        assertThat(continuityClaimable())
                .as("и предикат считается по своим парам: чужая остановка его не роняет")
                .isEqualTo(Boolean.TRUE);
        assertThat(get(SURFACE_DESCRIPTION, TENANT).body())
                .as("выдача называет величины СВОИМИ: читатель не выводит из неё, что о полноте "
                        + "его чисел свидетельствует чужая группа (находка F-2)")
                .doesNotContain(JOURNAL_WORD);
    }

    /** Кладёт по одному факту каждого зерна и собирает по ним агрегаты. */
    private void givenFactsOfBothGrains() {
        publish(first(), "E-DEAL", DEAL_CLOSED, midnightDaysAgo(BUCKET_DAYS_BACK),
                Bodies.dealClosed(ACCOUNT, STRATEGY));
        publish(first(), "E-INCIDENT", DEAL_OPENED, midnightDaysAgo(BUCKET_DAYS_BACK),
                Bodies.incident(ACCOUNT));
        awaitConsumed(first());
        recompute();
    }

    /** Строка сделочного факта названного события; иное число строк — падение. */
    private Map<String, Object> dealFactOf(String eventId) {
        return rows.row(DEAL_FACTS, "event_id", eventId);
    }

    /** Первая тема подписки: в неё ходят все клетки класса. */
    private String first() {
        return StatisticsSubstrate.ownTopic(SLUG);
    }

    /** Вторая тема подписки: ею разводятся моменты наблюдения двух пар. */
    private String second() {
        return StatisticsSubstrate.ownSecondTopic(SLUG);
    }
}
